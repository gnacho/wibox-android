#!/usr/bin/env python3
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
DYNAMIC = "analysis/dynamic/inventory.json"
ENTRY = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt"
MODELS = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/UpgradeModels.kt"
REAL = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/RealWiboxCloud.kt"
PARSER = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/UpgradeXmlParser.kt"
ROUTES = "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/internal/ProtocolRoutes.kt"
DOCS = "docs/protocol.md"
UPGRADE_PATH = "/webs/version/app/version_check.jsp"

TEST_SNIPPETS = [
    "requestMatchesAttempt3AndParsesEveryObservedField",
    "encodesReservedClientIdCharactersWithoutChangingQueryShape",
    "parsesNonEmptyUrlsDescriptionAndConfig",
    "rejectsNon200AndUnobservedContentTypes",
    "rejectsMalformedMissingAndInvalidIntegerXml",
    "rejectsDoctypeXxeAndXIncludeWithoutResolvingContent",
    "nonZeroProviderResultIsUnsupportedNotSuccess",
    "mapsTransportFailuresAndPropagatesCancellation",
    "validatesUpgradeBaseAndInputsBeforeNetwork",
    "ignoresSetCookieAndNeverTouchesTokenStore",
    "closedClientRejectsUpgradeBeforeNetwork",
]

FIXTURES = [
    "upgrade_success.xml", "upgrade_nonempty.xml", "upgrade_malformed.xml",
    "upgrade_missing.xml", "upgrade_invalid_integer.xml", "upgrade_xxe.xml",
    "upgrade_xinclude.xml", "upgrade_nonzero.xml",
]

MODEL_FIELDS = [
    "flag", "result", "priority", "version", "url", "description",
    "configVersion", "configUrl", "configPicture",
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

    dynamic_text = read(DYNAMIC)
    entry = read(ENTRY)
    models = read(MODELS)
    real = read(REAL)
    parser = read(PARSER)
    routes = read(ROUTES)
    docs = read(DOCS)

    try:
        dynamic = json.loads(dynamic_text)
        attempt3 = next(item for item in dynamic["attempts"] if item["id"] == "attempt-3")
        request = next(item for item in dynamic["network"]["requests"] if item.get("id") == "HTTP-UPGRADE-002")
        update = next(item for item in dynamic["static_correspondence"] if item["operation"] == "app_upgrade_check")
    except (json.JSONDecodeError, KeyError, StopIteration) as error:
        errors.append(f"Invalid dynamic upgrade evidence: {error}")
        return errors

    require(attempt3["upgrade"]["request_observed"] is True, "Attempt3 request is not observed")
    require(attempt3["upgrade"]["response_observed"] is True, "Attempt3 response is not observed")
    require(request["method"] == "GET", "Upgrade method differs from attempt3")
    require(request["url"]["scheme"] == "https", "Upgrade must remain HTTPS")
    require(request["url"]["path"] == UPGRADE_PATH, "Upgrade path differs from attempt3")
    require(request["query_keys"] == ["os", "appid", "version", "clientid"], "Upgrade query keys differ")
    require(request["query_values_sanitized"]["os"] == "android", "Upgrade OS differs")
    require(request["query_values_sanitized"]["appid"] == "2021", "Upgrade appid differs")
    require(request["query_values_sanitized"]["version"] == "18", "Observed version differs")
    require(request["query_values_sanitized"]["clientid"] == "<redacted>", "Client ID must remain redacted")
    response = request["response"]
    require(response["observed"] is True and response["status"] == 200, "Only HTTP 200 success is supported")
    headers = {item["name"]: item["value"] for item in response["headers"]}
    require(headers.get("Content-Type") == "text/xml;charset=UTF-8", "Observed XML Content-Type differs")
    require(headers.get("Set-Cookie") == "<redacted>", "Cookie evidence must remain redacted")
    require(response["size_bytes"] == 308, "Observed response size changed")
    schema = response["body_schema"]
    require(schema["root"] == "envelope", "Upgrade root differs")
    require(schema["header"] == {"flag": "tdkcloud", "result": 0}, "Upgrade header differs")
    require(schema["content"]["priority"] == 0 and schema["content"]["version"] == "5.1.11", "Upgrade content differs")
    require(schema["content"]["url"] == "" and schema["content"]["description"] == "", "Empty upgrade content must remain empty")
    require(schema["content"]["config"] == {"version": 0, "url": "", "picture": ""}, "Upgrade config differs")
    require(update["dynamic_status"] == "request_and_success_response_observed", "Dynamic/static correspondence drifted")
    require(len(dynamic["unsupported_updates"]) == 1 and dynamic["unsupported_updates"][0]["operation"] == "app_upgrade_check", "Only app_upgrade_check may leave Unsupported")

    require("suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo" in entry, "Public upgrade API missing")
    require("override suspend fun checkAppUpgrade" in real, "Real client does not override upgrade API")
    require("data class AppUpgradeRequest" in models and "data class AppUpgradeInfo" in models, "Upgrade public models missing")
    for field in ["baseUrl", "currentVersion", "clientId"]:
        require(field in models, f"Upgrade request field missing: {field}")
    for field in MODEL_FIELDS:
        require(field in models, f"Upgrade response field missing: {field}")
    require("clientId=<redacted>" in models, "Client ID is not redacted in toString")
    require(f'const val APP_UPGRADE_PATH = "{UPGRADE_PATH}"' in routes, "Upgrade route missing")
    for query in ["os", "appid", "version", "clientid"]:
        require(f'url.parameters.append("{query}"' in real, f"Upgrade query missing in client: {query}")
    require('url.parameters.append("os", "android")' in real, "Upgrade OS is not android")
    require("tokenHeader = null" in real, "Upgrade must not send token header")
    require("expectedStatus = 200" in real, "Upgrade must accept only observed HTTP 200")
    require('requiredContentType = "text/xml;charset=UTF-8"' in real, "Upgrade Content-Type check missing")
    require('header("Accept-Language", request.acceptLanguage)' in real, "Observed Accept-Language missing")
    require("validateBaseUrl(request.baseUrl" in real, "Upgrade base URL is not validated")
    require("currentVersion <= 0" in real and "clientId.isBlank()" in real, "Upgrade input validation missing")

    for feature in [
        "disallow-doctype-decl", "external-general-entities", "external-parameter-entities",
        "load-external-dtd", "accessExternalDTD", "accessExternalSchema",
        "isXIncludeAware = false", "isExpandEntityReferences = false",
        "forbiddenDeclarations", "xInclude", "MAX_XML_BYTES",
    ]:
        require(feature in parser, f"XML security control missing: {feature}")
    for field in ["flag", "result", "priority", "version", "url", "description", "picture", "config"]:
        require(f'"{field}"' in parser, f"XML field validation missing: {field}")
    require('flag != "tdkcloud"' in parser, "Provider flag is not validated")
    require("result != 0" in parser and "CloudError.Unsupported" in parser, "Nonzero result can become success")

    tests = "\n".join(
        overrides.get(path.relative_to(root).as_posix(), path.read_text(encoding="utf-8"))
        for path in (root / "core/cloud/src/test/kotlin").rglob("*.kt")
    ) if (root / "core/cloud/src/test/kotlin").is_dir() else ""
    for snippet in TEST_SNIPPETS:
        require(snippet in tests, f"Upgrade test missing: {snippet}")

    for fixture in FIXTURES:
        text = read(f"core/cloud/src/test/resources/fixtures/{fixture}")
        require(bool(text), f"Missing upgrade fixture: {fixture}")
    success = read("core/cloud/src/test/resources/fixtures/upgrade_success.xml")
    for snippet in ["<flag>tdkcloud</flag>", "<result>0</result>", "<priority>0</priority>", "<version>5.1.11</version>", "<picture></picture>"]:
        require(snippet in success, f"Success fixture missing {snippet}")
    xxe = read("core/cloud/src/test/resources/fixtures/upgrade_xxe.xml")
    require("DOCTYPE" in xxe and "ENTITY" in xxe, "XXE negative fixture is not representative")

    for marker in [
        "P-APP-UPGRADE", "DYN3-HTTP-UPGRADE-001", "checkAppUpgrade",
        "AppUpgradeRequest", "AppUpgradeInfo", "text/xml;charset=UTF-8",
        "XInclude", "Set-Cookie", "ssl_insecure", "Unsupported",
    ]:
        require(marker in docs, f"Upgrade documentation missing: {marker}")

    observed_host = request["url"]["host"]
    for relative, text in [(ENTRY, entry), (MODELS, models), (REAL, real), (PARSER, parser), (DOCS, docs)]:
        require(observed_host not in text, f"Real upgrade host became a default in {relative}")
    require("CookieJar" not in real and "Set-Cookie" not in real, "Client persists or exposes cookies")
    require("import io.ktor" not in models and "import io.ktor" not in entry, "Ktor leaked into upgrade public API")
    require(not list((root / "core/cloud").rglob("*.pyc")), "Python bytecode found in core/cloud")
    return errors


def self_test(root: Path) -> None:
    baseline = verify(root)
    if baseline:
        raise AssertionError(f"Positive upgrade control failed: {baseline}")
    dynamic_negative = json.loads((root / DYNAMIC).read_text(encoding="utf-8"))
    for item in dynamic_negative["network"]["requests"]:
        if item.get("id") == "HTTP-UPGRADE-002":
            item["url"]["path"] = "/invalid"
    negative_cases = [
        (DYNAMIC, json.dumps(dynamic_negative)),
        (PARSER, (root / PARSER).read_text(encoding="utf-8").replace("disallow-doctype-decl", "allow-doctype-decl")),
        (REAL, (root / REAL).read_text(encoding="utf-8").replace("tokenHeader = null", "tokenHeader = \"\"")),
        (DOCS, (root / DOCS).read_text(encoding="utf-8").replace("P-APP-UPGRADE", "REMOVED")),
        ("core/cloud/src/test/resources/fixtures/upgrade_success.xml", "<envelope/>"),
    ]
    for relative, content in negative_cases:
        if not verify(root, {relative: content}):
            raise AssertionError(f"Negative upgrade control did not fail: {relative}")
    test_path = "core/cloud/src/test/kotlin/dev/nacho/wibox/core/cloud/UpgradeContractTest.kt"
    if not verify(root, {test_path: ""}):
        raise AssertionError("Negative upgrade test control did not fail")


def main() -> int:
    self_test(ROOT)
    errors = verify(ROOT)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_UPGRADE_CONTRACT_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
