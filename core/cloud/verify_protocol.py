#!/usr/bin/env python3
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
ENTRY = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt"
ROUTES = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/ProtocolRoutes.kt"
REAL = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/RealWiboxCloud.kt"
MODELS = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/ShadowModels.kt"
ERRORS = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/CloudError.kt"
DOCS = "docs/protocol.md"

EXPECTED_ROUTES = {
    "OAUTH_TOKEN": "/qvoauthv2/token",
    "SHADOW_STATUS": "/openapi-tdk/device/status",
    "SHADOW_STATUS_MULTI": "/openapi-tdk/device/status/multi",
    "SHADOW_SUBSCRIBE": "/openapi-tdk/device/status/sub",
    "SHADOW_UNSUBSCRIBE": "/openapi-tdk/device/status/unsub",
}

REQUIRED_TEST_SNIPPETS = [
    "passwordGrantUsesExactWireContractAndPersistsSession",
    "refreshGrantUsesOnlyRefreshQueryAndAccessTokenHeader",
    "refreshWithoutStoredTokenFailsBeforeNetwork",
    "restoresOnceAndUsesFreshCachedAccessToken",
    "automaticRefreshAtFiveMinuteBoundaryPrecedesShadowRequest",
    "concurrentRefreshUsesSingleFlightAndOnePersistenceWrite",
    "statusUsesPostTokenHeaderAndTypedBody",
    "multiStatusUsesArrayBodyAndPreservesMissingState",
    "subscribeUsesDeviceStateSubscriptionAndReturnsAck",
    "unsubscribeUsesReasonAndEmptyFieldsAndReturnsAck",
    "shadowWithoutTokenDoesNotSendRequest",
    "parsesDocumentedAlarmTypesAndPreservesUnknownFields",
    "rejectsMalformedAndUndocumentedAlarmPayloads",
    "capabilitiesAndUnsupportedOperationsNeverClaimRuntimeSupport",
    "mapsHttpErrorsWithoutRetryOrSecretLeakage",
    "mapsNetworkAndTimeoutWithoutRetry",
    "mapsKtorRequestTimeoutBeforeCancellationAndRedactsUrl",
    "blankRestoredTokensFailBeforeNetwork",
    "rejectsMalformedAndIncompleteOAuthWithoutPersistence",
    "rejectsMalformedIncompleteAndProviderErrorShadowResponses",
    "mapsPersistenceFailures",
    "validatesInputConfigurationAndCloseWithoutNetwork",
]

REQUIRED_MARKERS = [
    "P-OAUTH-PASSWORD", "P-OAUTH-REFRESH", "P-SESSION-RESTORE",
    "P-SHADOW-STATUS", "P-SHADOW-MULTI", "P-SHADOW-SUB",
    "P-SHADOW-UNSUB", "P-ALARM-FCM", "P-ERROR-HTTP",
    "P-ERROR-WIRE", "U-SERVICE-DISCOVERY", "U-DEVICE-LIST",
    "U-REALTIME", "U-ACTIONS", "U-REGISTER", "U-MEDIA",
]

REQUIRED_SECTIONS = [
    "## 8. Service discovery dinámico no resuelto",
    "## 9. MQTT desactivado",
    "## 10. P2P nativo no reimplementado",
    "## 11. Diferencias con duhow y cmos486",
]

REQUIRED_ERRORS = [
    "MissingToken", "Authentication", "Authorization", "Http", "Provider",
    "Network", "Timeout", "Parse", "Persistence", "InvalidInput",
    "Unsupported", "Closed",
]

REQUIRED_FIXTURES = [
    "oauth_password.json", "oauth_refresh.json", "shadow_status.json",
    "shadow_status_multi.json", "shadow_ack.json", "alarm_call.json",
    "alarm_motion.json", "alarm_answered.json", "malformed.json",
    "shadow_incomplete.json", "shadow_provider_error.json",
]


def verify(root: Path, overrides: dict[str, str] | None = None) -> list[str]:
    overrides = overrides or {}
    errors: list[str] = []

    def require(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)

    def read(relative: str) -> str:
        if relative in overrides:
            return overrides[relative]
        path = root / relative
        if not path.is_file():
            errors.append(f"Missing file: {relative}")
            return ""
        return path.read_text(encoding="utf-8")

    docs = read(DOCS)
    entry = read(ENTRY)
    routes = read(ROUTES)
    real = read(REAL)
    models = read(MODELS)
    error_source = read(ERRORS)
    contract_text = read("analysis/integration-contract.json")
    inventory_text = read("analysis/apk/inventory.json")
    evidence = read("analysis/apk/evidence/code-evidence.md")

    require("package dev.nacho.wibox.core.cloud" in entry, "Public entry package drifted")
    require("interface WiboxCloud" in entry, "Public WiboxCloud entry is missing")
    require("object WiboxCloudFactory" in entry, "Public factory is missing")
    require("baseUrl: String," in read("core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/CloudModels.kt"), "Base URL must be required")

    public_root = root / "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud"
    if public_root.is_dir():
        for path in public_root.glob("*.kt"):
            relative = path.relative_to(root).as_posix()
            text = overrides.get(relative, path.read_text(encoding="utf-8"))
            require("import io.ktor" not in text, f"Ktor type leaked into public API: {relative}")

    try:
        contract = json.loads(contract_text)
        inventory = json.loads(inventory_text)
    except json.JSONDecodeError as error:
        errors.append(f"Invalid evidence JSON: {error}")
        return errors

    oauth = contract["stock_cloud"]["oauth"]
    require(oauth["path"] == EXPECTED_ROUTES["OAUTH_TOKEN"], "OAuth path contradicts integration contract")
    require(oauth["method"] == "GET", "OAuth method must remain GET")
    require(oauth["token_header"] == "token", "OAuth token header drifted")
    require(contract["stock_cloud"]["shadow_http_paths"] == [
        EXPECTED_ROUTES["SHADOW_STATUS"], EXPECTED_ROUTES["SHADOW_STATUS_MULTI"],
        EXPECTED_ROUTES["SHADOW_SUBSCRIBE"], EXPECTED_ROUTES["SHADOW_UNSUBSCRIBE"],
    ], "Shadow paths contradict integration contract")
    require(inventory["cloud_areas"]["shadow_http"]["paths"] == contract["stock_cloud"]["shadow_http_paths"], "APK inventory and implementation routes differ")

    for name, route in EXPECTED_ROUTES.items():
        require(f'const val {name} = "{route}"' in routes, f"Route missing or changed: {name}")
        require(route in docs, f"Route absent from docs: {route}")

    for query in ["grant_type", "client_id", "client_type", "oemid", "appid", "usr", "pwd", "region_id", "client_flag", "refresh_token"]:
        require(f'"{query}"' in real, f"OAuth query missing from client: {query}")
        require(query in docs, f"OAuth query absent from docs: {query}")
    require('header("token", tokenHeader)' in real, "Token header is not set by transport")
    require('header("Authorization"' not in real, "Client must not add an Authorization header")
    require("REFRESH_MARGIN_MILLIS = 5 * 60 * 1_000L" in read("core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/SessionManager.kt"), "Five-minute refresh margin drifted")

    for operation in ["getDeviceShadow", "getDeviceShadows", "subscribeDeviceShadows", "unsubscribeDeviceShadows", "parseAlarmEvent", "observeEvents", "listDevices", "discoverServices", "performAction"]:
        require(operation in entry and operation in real, f"Public operation is not wired: {operation}")
    for field in ["F1_state", "Switch_state", "Private_mode_state", "Fisheye_chn_state", "Lock_state", "Alarm_zone_mode", "Smart_relay"]:
        require(field in read("core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/ShadowWire.kt"), f"Shadow wire field missing: {field}")
        require(field in docs, f"Shadow field absent from docs: {field}")
    for model in ["CloudDevice", "DeviceShadow", "DeviceState", "ShadowAcknowledgement"]:
        require(f"data class {model}" in models, f"Public model missing: {model}")
    for error in REQUIRED_ERRORS:
        require(error in error_source, f"Typed error missing: {error}")

    for marker in REQUIRED_MARKERS:
        require(marker in docs, f"Evidence matrix marker missing: {marker}")
    for section in REQUIRED_SECTIONS:
        require(section in docs, f"Required protocol section missing: {section}")
    for evidence_id in ["E029", "E033", "E034", "E035", "E036", "E041", "E056", "E057", "E063", "E073"]:
        require(f"## {evidence_id} " in evidence, f"Evidence absent from source: {evidence_id}")
        require(evidence_id in docs, f"Evidence not linked from docs: {evidence_id}")

    tests = "\n".join(
        overrides.get(path.relative_to(root).as_posix(), path.read_text(encoding="utf-8"))
        for path in (root / "core/cloud/src/test/kotlin").rglob("*.kt")
    ) if (root / "core/cloud/src/test/kotlin").is_dir() else ""
    for snippet in REQUIRED_TEST_SNIPPETS:
        require(snippet in tests, f"Required test missing: {snippet}")

    for fixture in REQUIRED_FIXTURES:
        relative = f"core/cloud/src/test/resources/fixtures/{fixture}"
        text = read(relative)
        require(bool(text), f"Empty or missing fixture: {fixture}")
        if fixture != "malformed.json" and text:
            try:
                json.loads(text)
            except json.JSONDecodeError:
                errors.append(f"Fixture is not valid JSON: {fixture}")
    status_fixture = read("core/cloud/src/test/resources/fixtures/shadow_status.json")
    for key in ['"thingId"', '"Device_state"', '"Future_state"']:
        require(key in status_fixture, f"Representative Shadow fixture lacks {key}")

    scoped_texts = [docs, entry, routes, real, models, tests]
    for text in scoped_texts:
        require("TODO" not in text and "FIXME" not in text, "Unfinished marker found in implementation or docs")
        require("—" not in text and "–" not in text, "Non-ASCII dash found")
    return errors


def self_test(root: Path) -> None:
    baseline = verify(root)
    if baseline:
        raise AssertionError(f"Positive protocol control failed: {baseline}")

    negative_cases = [
        (DOCS, verify(root, {DOCS: (root / DOCS).read_text(encoding="utf-8").replace("P-SHADOW-SUB", "REMOVED")}), "matrix"),
        (ROUTES, verify(root, {ROUTES: (root / ROUTES).read_text(encoding="utf-8").replace("/qvoauthv2/token", "/invalid")}), "route"),
        (ENTRY, verify(root, {ENTRY: (root / ENTRY).read_text(encoding="utf-8") + "\nimport io.ktor.client.HttpClient\n"}), "Ktor"),
        ("core/cloud/src/test/kotlin/dev/nacho/wibox/core/cloud/SessionBehaviorTest.kt", verify(root, {"core/cloud/src/test/kotlin/dev/nacho/wibox/core/cloud/SessionBehaviorTest.kt": ""}), "singleFlight"),
        ("core/cloud/src/test/resources/fixtures/shadow_status.json", verify(root, {"core/cloud/src/test/resources/fixtures/shadow_status.json": "{}"}), "fixture"),
    ]
    for name, negative_errors, expected in negative_cases:
        if not negative_errors:
            raise AssertionError(f"Negative protocol control did not fail: {name}")


def main() -> int:
    self_test(ROOT)
    errors = verify(ROOT)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_PROTOCOL_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
