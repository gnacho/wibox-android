#!/usr/bin/env python3
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
MATRIX = "analysis/architecture/provider-matrix.json"
CATALOG = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/ProviderCapabilities.kt"
STOCK = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/StockQvCloudProvider.kt"
PORT = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxProvider.kt"
MODELS = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/ProviderModels.kt"
ACTIONS = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/ProviderActionModels.kt"
DOCS = "docs/provider-implementation.md"
TEST = "core/cloud/src/test/kotlin/dev/nacho/wibox/core/cloud/ProviderContractTest.kt"

TEST_SNIPPETS = [
    "catalogContains64CapabilitiesWithoutPromotingDeclaredSupport",
    "upgradeDelegatesToLegacyWithoutDuplicatingProtocol",
    "providerErrorsPreserveCategoryRetryAndCapability",
    "sessionsAreOpaqueAndCredentialsAreRedacted",
    "shadowOperationsMapToNeutralSnapshotsAndAck",
    "alarmParserCreatesEnvelopeAndRealtimeKeepsUnsupportedAndCancellation",
    "actionsFailBeforeLegacyExecutionAndAckLevelsStayDistinct",
    "listAndDiscoveryRemainUnsupportedWithoutEmptySuccess",
    "prepareUsesRestoredSessionAndCloseIsSingleOwner",
    "prepareDoesNotReturnReadyAfterConcurrentClose",
    "legacyApiRemainsUsableAndPublicProviderApiHasNoKtorTypes",
]


def catalog_rows(text: str) -> dict[str, dict]:
    rows: dict[str, dict] = {}
    for line in text.splitlines():
        match = re.match(r'^\s*"([^"]+)",\s*$', line)
        if not match:
            continue
        parts = match.group(1).split("|")
        if len(parts) != 5:
            continue
        rows[parts[0]] = {
            "support": parts[1],
            "current": parts[2],
            "evidence": parts[3].split(","),
            "limitations": set(parts[4].split(",")),
        }
    return rows


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

    matrix_text = read(MATRIX)
    catalog_text = read(CATALOG)
    stock = read(STOCK)
    port = read(PORT)
    models = read(MODELS)
    actions = read(ACTIONS)
    docs = read(DOCS)
    tests = read(TEST)

    try:
        matrix = json.loads(matrix_text)
    except json.JSONDecodeError as error:
        return [f"Invalid provider matrix: {error}"]
    capabilities = matrix.get("capabilities", [])
    require(len(capabilities) == 64, "Provider matrix must contain 64 capabilities")
    matrix_rows = {item["id"]: item for item in capabilities}
    require(len(matrix_rows) == len(capabilities), "Capability IDs must be unique")
    rows = catalog_rows(catalog_text)
    require(len(rows) == 64, "Typed catalog must contain 64 capabilities")
    require(set(rows) == set(matrix_rows), "Typed catalog IDs differ from provider matrix")

    for capability_id, capability in matrix_rows.items():
        stock_state = capability["providers"]["stock-qvcloud"]
        row = rows.get(capability_id)
        if row is None:
            continue
        require(row["support"] == stock_state["support"], f"Support drift in {capability_id}")
        require(row["current"] == capability["current"], f"Evidence drift in {capability_id}")
        require(row["evidence"] == capability["evidence"], f"Evidence references drift in {capability_id}")
        expected_limits = {f"{key}={value}" for key, value in capability["compatibility_now"].items()}
        require(row["limitations"] == expected_limits, f"Limitations drift in {capability_id}")
        require(f"`{capability_id}`" in docs, f"Capability missing from implementation docs: {capability_id}")
        for provider_id in ("mqtt", "self-hosted"):
            future = capability["providers"][provider_id]
            require(future["status"] == "not_implemented", f"Future provider promoted in {capability_id}")
        for dimension, value in capability["compatibility_now"].items():
            require(value not in {"hardware_verified", "verified", "runtime_verified"}, f"False verification claim in {capability_id}")

    require('id == "app.update_check"' in catalog_text, "Dynamic upgrade special case missing")
    require("EvidenceOrigin.DYNAMIC_CAPTURE" in catalog_text, "Upgrade dynamic origin missing")
    require("operationEvidence = if (isDynamicUpgrade) CapabilityEvidence.IMPLEMENTED_CONTRACT else declaredEvidence" in catalog_text, "Upgrade operation evidence is not separated")
    for operation in [
        "PREPARE_SESSION", "AUTHENTICATE", "REFRESH_SESSION", "RESTORE_SESSION",
        "GET_SHADOW", "GET_SHADOW_MULTI", "SUBSCRIBE_SHADOW", "UNSUBSCRIBE_SHADOW",
        "PARSE_ALARM_EVENT", "CHECK_APP_UPGRADE",
    ]:
        require(operation in catalog_text, f"Declared provider operation missing: {operation}")
    require("clientId" not in catalog_text, "Client ID leaked into capability catalog")

    require("interface WiboxProvider" in port, "WiboxProvider public port missing")
    for contract in ["SessionPort", "DevicePort", "EventPort", "ActionPort", "MaintenancePort"]:
        require(f"interface {contract}" in port, f"Provider subport missing: {contract}")
    require("object WiboxProviderFactory" in port, "Provider factory missing")
    require("fun stockQvCloud" in port, "Stock provider factory missing")
    require("fun mqtt" not in port and "fun selfHosted" not in port, "Future provider factory was invented")
    require("cloud.checkAppUpgrade(request)" in stock, "Upgrade is not delegated to WiboxCloud")
    for forbidden in ["UpgradeXmlParser", "DocumentBuilderFactory", "HttpClient", "ProtocolRoutes.APP_UPGRADE_PATH"]:
        require(forbidden not in stock, f"Upgrade protocol duplicated in stock adapter: {forbidden}")
    require("cloud.performAction" not in stock, "Unsupported action reaches legacy execution")
    require("DEVICE_ACKNOWLEDGED" not in stock and "PHYSICAL_OBSERVED" not in stock, "Stock adapter invents physical acknowledgement")
    require("closeClientWhenClosed" in stock and "compareAndSet(false, true)" in stock, "Single close ownership is missing")
    require("prepareMutex" in stock and "val restored = restoreSession()" in stock, "Prepare concurrency protection missing")
    require("class Mqtt" not in stock and "class SelfHosted" not in stock, "Future adapter was implemented")

    for model in ["SessionHandle", "CredentialMaterial", "ProviderDescriptor", "ProviderSessionContext"]:
        require(model in models, f"Provider model missing: {model}")
    require("opaqueId=<redacted>" in models and "secret=<redacted>" in models, "Session or credential redaction missing")
    for level in ["LOCAL_ACCEPTED", "PROVIDER_ACCEPTED", "DEVICE_ACKNOWLEDGED", "PHYSICAL_OBSERVED", "UNKNOWN_OUTCOME"]:
        require(level in actions, f"Action outcome level missing: {level}")
    require("observation != null && !outcomeUnknown" in actions, "Physical claim guard missing")
    for snippet in TEST_SNIPPETS:
        require(snippet in tests, f"Provider behavior test missing: {snippet}")
    for marker in [
        "Ownership, concurrencia y cierre", "Migración de app y UI", "Consumo futuro por Binder",
        "DYN3-HTTP-UPGRADE-001", "cloud.checkAppUpgrade(request)", "UNKNOWN_OUTCOME",
        "MQTT y self-hosted solo tienen IDs reservados",
    ]:
        require(marker in docs, f"Provider implementation documentation missing: {marker}")

    public_root = root / "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud"
    if public_root.is_dir():
        for path in public_root.glob("*.kt"):
            relative = path.relative_to(root).as_posix()
            text = overrides.get(relative, path.read_text(encoding="utf-8"))
            require("import io.ktor" not in text, f"Ktor type leaked into public API: {relative}")
            require("hardware_verified = true" not in text, f"False hardware claim in {relative}")
    require(not list((root / "core/cloud").rglob("*.pyc")), "Python bytecode found in core/cloud")
    return errors


def self_test(root: Path) -> None:
    baseline = verify(root)
    if baseline:
        raise AssertionError(f"Positive provider control failed: {baseline}")

    matrix = json.loads((root / MATRIX).read_text(encoding="utf-8"))
    matrix["capabilities"].pop()
    negative_cases = [
        (MATRIX, json.dumps(matrix)),
        (CATALOG, (root / CATALOG).read_text(encoding="utf-8").replace("core.lifecycle|required|implemented_contract", "core.lifecycle|required|unsupported", 1)),
        (STOCK, (root / STOCK).read_text(encoding="utf-8").replace("cloud.checkAppUpgrade(request)", "error(\"not delegated\")") + "\nval physical = PHYSICAL_OBSERVED\n"),
        (PORT, (root / PORT).read_text(encoding="utf-8") + "\nimport io.ktor.client.HttpClient\n"),
        (DOCS, (root / DOCS).read_text(encoding="utf-8").replace("`app.update_check`", "`removed_upgrade`")),
        (TEST, ""),
    ]
    for relative, content in negative_cases:
        if not verify(root, {relative: content}):
            raise AssertionError(f"Negative provider control did not fail: {relative}")


def main() -> int:
    self_test(ROOT)
    errors = verify(ROOT)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_PROVIDER_PORT_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
