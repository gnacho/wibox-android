#!/usr/bin/env python3
"""Runtime verifier for the Wibox external Binder API (leaf 1.2.10).

Validates the implemented runtime against the inventory generated after the
gate runs: manifest registration, the 15 AIDL methods, the 43 AIDL
parcelables, the SDK, the uid+package+signer allowlist authorization,
deny-by-default and two-signature test evidence, the absence of secrets in
the new sources and honest Unsupported behavior.

Exit 0 prints WIBOX_EXTERNAL_API_RUNTIME_VERIFIED.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AIDL_DIR = ROOT / "external-api/src/main/aidl/dev/nacho/wibox/externalapi/aidl"
SDK_DIR = ROOT / "external-api/src/main/kotlin/dev/nacho/wibox/externalapi/sdk"
INVENTORY_PATH = ROOT / "analysis/external-api-runtime/inventory.json"
CONTRACT_PATH = ROOT / "analysis/external-api/api-contract.json"
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID_TEST_DIR = ROOT / "app/src/androidTest/kotlin/dev/nacho/wibox/externalapi"

EXPECTED_METHOD_COUNT = 15
EXPECTED_PARCELABLE_COUNT = 43

SECRET_PATTERNS = [
    r"clientId",
    r"dataEncodeKey",
    r"\bCookie\b",
    r"\bBearer\b",
    r"apiKey",
    r"accessToken",
    r"refreshToken",
    r"\bpassword\b",
]

AUTH_MARKERS = [
    "Binder.getCallingUid",
    "getPackagesForUid",
    "apkContentsSigners",
    "GET_SIGNING_CERTIFICATES",
    "AuthorizationStore",
    "REVOKED",
]

LIMIT_MARKERS = {
    "external-api/src/main/kotlin/dev/nacho/wibox/externalapi/aidl/ParcelableSupport.kt": [
        "REQUEST_PARCEL_BYTES",
        "RESPONSE_PARCEL_BYTES",
        "EVENT_BATCH_PARCEL_BYTES",
    ],
    "app/src/main/kotlin/dev/nacho/wibox/externalapi/ExternalApiRuntime.kt": [
        "PENDING_ONE_SHOT_PER_APP",
        "PENDING_ONE_SHOT_GLOBAL",
    ],
    "app/src/main/kotlin/dev/nacho/wibox/externalapi/WiboxExternalApiService.kt": [
        "SUBSCRIPTIONS_PER_APP",
    ],
    "external-api/src/main/kotlin/dev/nacho/wibox/externalapi/server/AuthorizationEngine.kt": [
        "pendingPerPackageLimit",
        "pendingGlobalLimit",
    ],
}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def strip_comments(source: str) -> str:
    """Removes line and block comments so that negative statements such as
    "no tokens" in documentation do not trip the secret scan."""
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", source)


def parse_manifest(path: Path):
    """Returns the registered external API service as a dict, or None."""
    tree = ET.parse(path)
    root = tree.getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    for service in root.iter("service"):
        name = service.get(f"{android_ns}name", "")
        if not name.endswith("WiboxExternalApiService"):
            continue
        actions = [
            action.get(f"{android_ns}name")
            for intent_filter in service.findall("intent-filter")
            for action in intent_filter.findall("action")
        ]
        return {
            "name": name,
            "exported": service.get(f"{android_ns}exported"),
            "permission": service.get(f"{android_ns}permission"),
            "actions": actions,
        }
    return None


def parse_aidl_methods(path: Path):
    """Method names declared inside the AIDL interface body."""
    text = path.read_text(encoding="utf-8")
    interface_match = re.search(r"interface\s+\w+\s*\{(.*?)\}", text, flags=re.DOTALL)
    if not interface_match:
        return []
    body = interface_match.group(1)
    methods = []
    for line in body.splitlines():
        match = re.match(r"\s+[\w<>,.\s\[\]]+?\s(\w+)\(.*\);\s*$", line)
        if match:
            methods.append(match.group(1))
    return methods


def count_parcelables(directory: Path):
    """Counts parcelable declarations, excluding interfaces and callbacks."""
    count = 0
    for path in sorted(directory.glob("*.aidl")):
        text = path.read_text(encoding="utf-8")
        if re.search(r"^\s*(oneway\s+)?interface\s+", text, flags=re.MULTILINE):
            continue
        if re.search(r"^\s*parcelable\s+\w+;\s*$", text, flags=re.MULTILINE):
            count += 1
    return count


def read_sources(paths):
    contents = {}
    for relative in paths:
        source = ROOT / relative
        contents[relative] = source.read_text(encoding="utf-8") if source.exists() else ""
    return contents


def scan_for_secrets(sources):
    matches = []
    for name, content in sources.items():
        clean = strip_comments(content)
        for pattern in SECRET_PATTERNS:
            if re.search(pattern, clean):
                matches.append(f"{name}:{pattern}")
    return matches


def validate():
    errors = []

    if not INVENTORY_PATH.exists():
        return ["inventory.json missing"]
    inventory = load(INVENTORY_PATH)
    if inventory.get("status") != "implemented":
        errors.append("inventory status must be implemented")

    contract = load(CONTRACT_PATH)

    # Manifest registration
    service = parse_manifest(MANIFEST_PATH)
    if service is None:
        errors.append("external API service not registered in AndroidManifest.xml")
    else:
        if not service["name"].endswith(".externalapi.WiboxExternalApiService") and not service["name"].endswith(
            "WiboxExternalApiService"
        ):
            errors.append(f"unexpected service component: {service['name']}")
        if service["exported"] != "true":
            errors.append("service must be exported")
        if service["permission"] is not None:
            errors.append("service must not declare an android:permission attribute")
        if inventory["service"]["intent_action"] not in service["actions"]:
            errors.append(f"service must expose action {inventory['service']['intent_action']}")
        if inventory["service"]["manifest_permission"] is not None:
            errors.append("inventory must record no manifest permission")

    # Methods: AIDL source vs contract vs inventory
    methods = parse_aidl_methods(AIDL_DIR / "IWiboxExternalService.aidl")
    if len(methods) != EXPECTED_METHOD_COUNT:
        errors.append(f"AIDL must declare 15 methods, got {len(methods)}")
    contract_methods = [method["name"] for method in contract.get("methods", [])]
    if methods != contract_methods:
        errors.append("AIDL methods differ from the contract methods")
    if methods != inventory.get("methods", []):
        errors.append("AIDL methods differ from the inventory methods")

    # Models: parcelable declarations vs contract vs inventory
    parcelables = count_parcelables(AIDL_DIR)
    if parcelables != EXPECTED_PARCELABLE_COUNT:
        errors.append(f"AIDL parcelables must be 43, got {parcelables}")
    if len(contract.get("models", {})) != EXPECTED_PARCELABLE_COUNT:
        errors.append("contract models must be 43")
    if inventory.get("parcelable_count") != EXPECTED_PARCELABLE_COUNT:
        errors.append("inventory parcelable_count must be 43")

    # SDK presence
    for sdk_file in inventory.get("sdk_files", []):
        if not (SDK_DIR / sdk_file).exists():
            errors.append(f"sdk file missing: {sdk_file}")

    # Runtime sources and authorization model
    sources = read_sources(inventory.get("server_sources", []))
    for relative, content in sources.items():
        if not content:
            errors.append(f"runtime source missing: {relative}")
    joined = "\n".join(sources.values())
    for marker in AUTH_MARKERS:
        if marker not in joined:
            errors.append(f"authorization marker missing in runtime sources: {marker}")
    if "default_policy" not in inventory.get("authorization", {}) or inventory["authorization"].get("default_policy") != "deny":
        errors.append("inventory must record deny by default")
    if inventory.get("authorization", {}).get("shared_uid_policy") != "deny":
        errors.append("inventory must record shared uid deny")

    # Limits declared in the contract and enforced in code
    for relative, markers in LIMIT_MARKERS.items():
        content = (ROOT / relative).read_text(encoding="utf-8") if (ROOT / relative).exists() else ""
        for marker in markers:
            if marker not in content:
                errors.append(f"limit marker missing in {relative}: {marker}")

    # Two-signature unit test evidence (signatures=2)
    unit = inventory.get("tests", {}).get("unit", {})
    if unit.get("exit_code") != 0:
        errors.append("inventory must record unit gate exit code 0")
    if not unit.get("two_signature_tests_passed"):
        errors.append("two signature tests must be recorded as passed")
    if unit.get("signatures") != 2:
        errors.append("two signature test must use signatures=2")
    two_signature_test = ROOT / unit.get(
        "two_signature_test_class",
        "external-api/src/test/kotlin/dev/nacho/wibox/externalapi/server/AuthorizationEngineTest.kt",
    )
    if not two_signature_test.exists():
        errors.append("two signature test file missing")
    else:
        test_source = two_signature_test.read_text(encoding="utf-8")
        if "signatures=2" not in test_source:
            errors.append("two signature test must state signatures=2")
        if "twoSignatureSet" not in test_source or "signerB" not in test_source:
            errors.append("two signature test must build a two signer set")

    # Deny-by-default instrumented evidence
    instrumented = inventory.get("tests", {}).get("instrumented", {})
    if instrumented.get("exit_code") != 0:
        errors.append("inventory must record instrumented gate exit code 0")
    if instrumented.get("failures") != 0 or instrumented.get("errors") != 0:
        errors.append("instrumented gate must record zero failures and errors")
    deny_test = ANDROID_TEST_DIR / "ExternalApiDenyByDefaultTest.kt"
    if not deny_test.exists():
        errors.append("deny by default instrumented test missing")
    else:
        deny_source = deny_test.read_text(encoding="utf-8")
        for marker in ("NOT_AUTHORIZED", "HANDSHAKE_REQUIRED", "bindService", "setClassName"):
            if marker not in deny_source:
                errors.append(f"deny by default test missing marker: {marker}")
    results_xml = ROOT / "app/build/outputs/androidTest-results/connected"
    if results_xml.exists():
        for xml_file in results_xml.rglob("*.xml"):
            suite = ET.parse(xml_file).getroot()
            if suite.get("failures") not in (None, "0") or suite.get("errors") not in (None, "0"):
                errors.append(f"instrumented results report failures: {xml_file.name}")
            names = [case.get("classname", "") for case in suite.iter("testcase")]
            if not any(name.endswith(instrumented.get("deny_by_default_test_class", "@")) for name in names):
                errors.append(f"instrumented results do not include the deny test: {xml_file.name}")

    # Honest Unsupported
    service_source = sources.get("app/src/main/kotlin/dev/nacho/wibox/externalapi/WiboxExternalApiService.kt", "")
    if "realtimeGate" not in service_source:
        errors.append("subscribeEvents must gate realtime through the provider capability catalog")
    if "WireApiErrorCode.UNSUPPORTED" not in service_source:
        errors.append("service must map unavailable operations to UNSUPPORTED")
    if "liveSupported = true" in service_source:
        errors.append("service must never fake a connected realtime stream")

    # Secrets
    secret_matches = scan_for_secrets(sources)
    recorded_scan = inventory.get("secrets_scan", {})
    if recorded_scan.get("matches") != 0:
        errors.append("inventory must record zero secret matches")
    if secret_matches:
        errors.append(f"secret patterns found in runtime sources: {', '.join(secret_matches)}")

    return errors


def self_test():
    """Exercises the parsing helpers on synthetic inputs."""
    errors = []

    synthetic_manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <service
            android:name=".externalapi.WiboxExternalApiService"
            android:exported="true">
            <intent-filter>
                <action android:name="dev.nacho.wibox.externalapi.BIND" />
            </intent-filter>
        </service>
    </application>
</manifest>
"""
    tmp_manifest = ROOT / "analysis/external-api-runtime/.self-test-manifest.xml"
    tmp_manifest.write_text(synthetic_manifest, encoding="utf-8")
    try:
        parsed = parse_manifest(tmp_manifest)
        if parsed is None:
            errors.append("self-test: manifest parser did not find the service")
        else:
            if parsed["exported"] != "true":
                errors.append("self-test: exported must be true")
            if parsed["permission"] is not None:
                errors.append("self-test: permission must be absent")
            if "dev.nacho.wibox.externalapi.BIND" not in parsed["actions"]:
                errors.append("self-test: action missing")
    finally:
        tmp_manifest.unlink(missing_ok=True)

    synthetic_aidl = """package dev.nacho.wibox.externalapi.aidl;

interface IDemo {
    HandshakeResult handshake(in ClientHello hello);
    void listDevices(in PageRequest request, in IDevicePageCallback callback);
    CancellationResult cancelOperation(String requestId);
}
"""
    tmp_aidl = ROOT / "analysis/external-api-runtime/.self-test-service.aidl"
    tmp_aidl.write_text(synthetic_aidl, encoding="utf-8")
    tmp_parcelable = ROOT / "analysis/external-api-runtime/.self-test-parcel.aidl"
    tmp_parcelable.write_text("package dev.nacho.wibox.externalapi.aidl;\n\nparcelable DemoThing;\n", encoding="utf-8")
    try:
        methods = parse_aidl_methods(tmp_aidl)
        if methods != ["handshake", "listDevices", "cancelOperation"]:
            errors.append(f"self-test: aidl method parse mismatch: {methods}")
        if count_parcelables(tmp_parcelable.parent) != 1:
            errors.append("self-test: parcelable count mismatch")
    finally:
        tmp_aidl.unlink(missing_ok=True)
        tmp_parcelable.unlink(missing_ok=True)

    secret_source = 'val clientId = fetchClientId()\n'
    if scan_for_secrets({"a.kt": secret_source}) != ["a.kt:clientId"]:
        errors.append("self-test: secret scan must match identifiers in code")
    commented = "// clientId\n// password\nval ok = 1\n"
    if scan_for_secrets({"a.kt": commented}) != []:
        errors.append("self-test: secret scan must ignore comments")

    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test-only", action="store_true")
    args = parser.parse_args()
    errors = self_test()
    if args.self_test_only:
        if errors:
            for error in errors:
                print(f"WIBOX_EXTERNAL_API_SELF_TEST_ERROR: {error}")
            return 1
        print("WIBOX_EXTERNAL_API_RUNTIME_SELF_TEST_VERIFIED")
        return 0
    errors.extend(validate())
    if errors:
        for error in errors:
            print(f"WIBOX_EXTERNAL_API_RUNTIME_ERROR: {error}")
        return 1
    print("WIBOX_EXTERNAL_API_RUNTIME_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
