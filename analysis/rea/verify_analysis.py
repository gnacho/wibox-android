#!/usr/bin/env python3
"""Validate the REA/Wibox analysis without network access or third-party packages."""

from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterator

ROOT = Path(__file__).resolve().parents[2]
REPORT_PATH = ROOT / "docs" / "rea-analysis.md"
INVENTORY_PATH = ROOT / "analysis" / "rea" / "inventory.json"
MARKER = "REA_ANALYSIS_VERIFIED"
REA_COMMIT = "d48e472fa6a664cacbb400560e673533005795ce"
DUHOW_COMMIT = "d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7"
DUHOW_URL = "https://github.com/duhow/wibox#1"
BLOG_URL = "https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant"
EVIDENCE_RE = re.compile(
    r"`(?P<path>(?:[A-Za-z0-9_.-]+/)*[A-Za-z0-9_.-]+):"
    r"(?P<start>\d+)(?:-(?P<end>\d+))?`"
)
LINE_SPEC_RE = re.compile(r"^(?P<start>[1-9]\d*)(?:-(?P<end>[1-9]\d*))?$")


class ValidationError(Exception):
    """An invalid or incoherent analysis artifact."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def require_mapping(value: Any, name: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{name} must be an object")
    return value


def require_list(value: Any, name: str, *, nonempty: bool = True) -> list[Any]:
    require(isinstance(value, list), f"{name} must be an array")
    if nonempty:
        require(len(value) > 0, f"{name} must not be empty")
    return value


def require_text(value: Any, name: str) -> str:
    require(isinstance(value, str) and len(value.strip()) > 0, f"{name} must be non-empty text")
    return value


def read_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing inventory: {path.relative_to(ROOT)}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as cause:
        raise ValidationError(f"invalid inventory JSON: {cause.msg}") from cause
    return require_mapping(value, "inventory")


def source_roots(inventory: dict[str, Any]) -> dict[str, Path]:
    primary = require_mapping(inventory.get("source"), "source")
    additional = require_list(inventory.get("additional_sources"), "additional_sources")
    sources = [primary, *additional]
    roots: dict[str, Path] = {}
    for source in sources:
        source_map = require_mapping(source, "source entry")
        source_id = require_text(source_map.get("id"), "source.id")
        require(source_id not in roots, f"duplicate source id: {source_id}")
        checkout_value = source_map.get("local_checkout")
        if checkout_value is None:
            require(source_id == "cmos486-blog", f"missing local_checkout for {source_id}")
            continue
        checkout = Path(require_text(checkout_value, f"{source_id}.local_checkout"))
        require(checkout.is_absolute(), f"{source_id}.local_checkout must be absolute")
        require(checkout.is_dir(), f"missing source checkout for {source_id}")
        roots[source_id] = checkout
    require("rea" in roots, "primary REA source is missing")
    require("duhow-wibox" in roots, "duhow source is missing")
    return roots


def parse_line_spec(spec: Any) -> tuple[int, int]:
    require(isinstance(spec, str), "evidence.lines must be text")
    match = LINE_SPEC_RE.fullmatch(spec)
    require(match is not None, f"invalid evidence line range: {spec!r}")
    start = int(match.group("start"))
    end = int(match.group("end") or start)
    require(end >= start, f"descending evidence line range: {spec}")
    return start, end


def iter_evidence(value: Any) -> Iterator[dict[str, Any]]:
    if isinstance(value, dict):
        if {"path", "lines", "contains"}.issubset(value):
            yield value
        for child in value.values():
            yield from iter_evidence(child)
    elif isinstance(value, list):
        for child in value:
            yield from iter_evidence(child)


def evidence_source_id(evidence: dict[str, Any]) -> str:
    source_id = evidence.get("source_id", "rea")
    require(isinstance(source_id, str) and source_id, "evidence.source_id must be non-empty text")
    return source_id


def validate_evidence_record(
    evidence: dict[str, Any], roots: dict[str, Path]
) -> None:
    source_id = evidence_source_id(evidence)
    require(source_id in roots, f"unknown evidence source id: {source_id}")
    relative = Path(require_text(evidence.get("path"), "evidence.path"))
    require(not relative.is_absolute() and ".." not in relative.parts, "evidence path must be relative and contained")
    source_file = roots[source_id] / relative
    require(source_file.is_file(), f"missing evidence file: {source_id}:{relative}")
    start, end = parse_line_spec(evidence.get("lines"))
    lines = source_file.read_text(encoding="utf-8", errors="replace").splitlines()
    require(end <= len(lines), f"evidence range outside file: {source_id}:{relative}:{start}-{end}")
    excerpt = "\n".join(lines[start - 1 : end])
    contains = require_list(evidence.get("contains"), "evidence.contains")
    for expected in contains:
        require(isinstance(expected, str) and expected, "evidence.contains entries must be non-empty text")
        require(expected in excerpt, f"evidence snippet absent: {source_id}:{relative}:{start}-{end}")
    forbidden = evidence.get("not_contains", [])
    require_list(forbidden, "evidence.not_contains", nonempty=False)
    for unexpected in forbidden:
        require(isinstance(unexpected, str) and unexpected, "evidence.not_contains entries must be non-empty text")
        require(unexpected not in excerpt, f"unexpected evidence snippet present: {source_id}:{relative}:{start}-{end}")


def candidate_report_roots(path: str, roots: dict[str, Path]) -> list[Path]:
    if path.startswith("include/") or path in {"INSTALL.md", "USAGE.md"}:
        return [roots["duhow-wibox"]]
    if path.startswith("src/") or path in {"package.json", ".gitmodules", "scripts/rea.mjs"}:
        return [roots["rea"]]
    if path.startswith("docs/"):
        duhow_docs = {"docs/system.md", "docs/codes.md", "docs/updates.md"}
        if path in duhow_docs:
            return [roots["duhow-wibox"]]
        rea_docs = {
            "docs/architecture.mermaid",
            "docs/android-analysis.md",
            "docs/web-network-captures.md",
            "docs/pcap-analysis.md",
        }
        if path in rea_docs:
            return [roots["rea"]]
    if path.startswith("scripts/") and path != "scripts/rea.mjs":
        return [roots["duhow-wibox"]]
    return list(roots.values())


def validate_report_references(report: str, roots: dict[str, Path]) -> int:
    references = list(EVIDENCE_RE.finditer(report))
    require(len(references) >= 60, "report has too few file:line references")
    for match in references:
        relative_text = match.group("path")
        if re.fullmatch(r"\d+(?:\.\d+){3}", relative_text):
            continue
        relative = Path(relative_text)
        start = int(match.group("start"))
        end = int(match.group("end") or start)
        require(1 <= start <= end, f"invalid report evidence range: {relative_text}")
        candidates = candidate_report_roots(relative_text, roots)
        found = False
        for root in candidates:
            candidate = root / relative
            if not candidate.is_file():
                continue
            line_count = len(candidate.read_text(encoding="utf-8", errors="replace").splitlines())
            if end <= line_count:
                found = True
                break
        require(found, f"unresolved report evidence: {relative_text}:{start}-{end}")
    return len(references)


def validate_report(report: str, roots: dict[str, Path]) -> None:
    require(len(report) >= 20000, "report is incomplete")
    required_markers = [
        "# Analisis de REA y fuentes Wibox",
        "## 2. Fuentes, revisiones, licencias y metodo",
        "## 3. Comparabilidad",
        "## 4. Arquitectura y lenguajes",
        "## 5. Matriz de funciones",
        "## 6. Endpoints, transportes y modelos",
        "## 7. Autenticacion, pairing, credenciales y tokens",
        "## 8. Descubrimiento, dispositivos, acciones y estados",
        "## 9. Tiempo real: MQTT, SIP, WebRTC y otros canales",
        "## 10. Criptografia",
        "## 11. Firmware",
        "## 12. Errores, reintentos y timeouts",
        "## 13. Configuracion, secretos y riesgos",
        "## 14. Confirmado, hipotesis y desconocido",
        "## 15. Contrato recomendado para `core:cloud`",
        "## 16. Plan de contraste con el APK",
        REA_COMMIT,
        DUHOW_COMMIT,
        "https://github.com/morluto/rea",
        DUHOW_URL,
        BLOG_URL,
        "HECHO DE CODIGO",
        "HIPOTESIS",
        "AFIRMACION DE TERCERO",
        "AuthPwd",
        "MQTT",
        "SIP",
        "WebRTC",
        "GPL-3.0",
        "MIT",
    ]
    for marker in required_markers:
        require(marker in report, f"report is missing required content: {marker}")
    require("\u2014" not in report and "\u2013" not in report, "report contains a forbidden dash")
    validate_report_references(report, roots)


def validate_source_metadata(inventory: dict[str, Any]) -> None:
    source = require_mapping(inventory.get("source"), "source")
    require(source.get("id") == "rea", "source.id must be rea")
    require(source.get("url") == "https://github.com/morluto/rea", "unexpected REA URL")
    require(source.get("branch") == "main", "unexpected REA branch")
    require(source.get("commit") == REA_COMMIT, "unexpected REA commit")
    require(re.fullmatch(r"[0-9a-f]{40}", source["commit"]) is not None, "invalid REA commit")
    require(source.get("package_version") == "6.8.0", "unexpected REA package version")
    license_map = require_mapping(source.get("license"), "source.license")
    require(license_map.get("spdx") == "MIT", "unexpected REA license")

    additional = require_list(inventory.get("additional_sources"), "additional_sources")
    require(len(additional) == 2, "exactly two additional sources are required")
    by_id = {entry.get("id"): entry for entry in additional if isinstance(entry, dict)}
    require(set(by_id) == {"duhow-wibox", "cmos486-blog"}, "additional source ids are incoherent")
    duhow = by_id["duhow-wibox"]
    require(duhow.get("url") == DUHOW_URL, "unexpected duhow URL")
    require(duhow.get("branch") == "main", "unexpected duhow branch")
    require(duhow.get("commit") == DUHOW_COMMIT, "unexpected duhow commit")
    duhow_license = require_mapping(duhow.get("license"), "duhow.license")
    require(duhow_license.get("identifier") == "GPL-3.0", "unexpected duhow license")
    require_text(duhow_license.get("scope_note"), "duhow license scope_note")
    blog = by_id["cmos486-blog"]
    require(blog.get("url") == BLOG_URL, "unexpected blog URL")
    require("tercero" in require_text(blog.get("source_role"), "blog.source_role").lower(), "blog must be marked as third-party")


def validate_absence_or_presence(inventory: dict[str, Any]) -> None:
    wibox = require_mapping(inventory.get("wibox_cloud"), "wibox_cloud")
    require(wibox.get("reference_status") == "absent-in-this-commit", "REA Wibox absence scope is unclear")
    for topic in ("authentication", "devices", "realtime"):
        entry = require_mapping(wibox.get(topic), f"wibox_cloud.{topic}")
        status = require_text(entry.get("status"), f"wibox_cloud.{topic}.status")
        require(status in {"not-implemented-in-reference", "implemented", "documented"}, f"unexpected {topic} status")
        if status == "not-implemented-in-reference":
            require_text(entry.get("justification"), f"wibox_cloud.{topic}.justification")
            require_list(entry.get("evidence"), f"wibox_cloud.{topic}.evidence")
            require_text(entry.get("negative_command"), f"wibox_cloud.{topic}.negative_command")

    duhow = require_mapping(inventory.get("duhow_wibox"), "duhow_wibox")
    code = require_mapping(duhow.get("code_confirmed"), "duhow_wibox.code_confirmed")
    require_list(code.get("endpoints"), "duhow code endpoints")
    require_list(code.get("mqtt_topics"), "duhow MQTT topics")
    uart = require_mapping(code.get("uart"), "duhow UART")
    require_mapping(uart.get("actions"), "duhow UART actions")
    require(len(uart["actions"]) >= 8, "duhow UART action inventory is incomplete")
    require_list(uart.get("evidence"), "duhow UART evidence")
    notes = require_mapping(duhow.get("third_party_stock_notes"), "duhow stock notes")
    cloud = require_mapping(notes.get("cloud"), "duhow cloud notes")
    require_list(cloud.get("cloud_log_endpoints"), "duhow cloud log endpoints")
    require_list(cloud.get("sequence"), "duhow cloud sequence")
    require_text(cloud.get("limitations"), "duhow cloud limitations")
    secrets = require_mapping(notes.get("secrets"), "duhow secret notes")
    require_mapping(secrets.get("fields"), "duhow secret fields")
    require_text(secrets.get("renewal_and_expiry"), "duhow token renewal limitations")
    require_list(notes.get("local_stock_ports"), "duhow stock ports")


def validate_inventory(inventory: dict[str, Any], roots: dict[str, Path]) -> int:
    require(inventory.get("schema_version") == 1, "unsupported schema_version")
    validate_source_metadata(inventory)
    for key in ("components", "endpoints", "models", "transports", "functions"):
        require_list(inventory.get(key), key)
    validate_absence_or_presence(inventory)
    claims = require_list(inventory.get("cmos486_third_party_claims"), "cmos486_third_party_claims")
    for index, claim in enumerate(claims):
        claim_map = require_mapping(claim, f"cmos486 claim {index}")
        require_text(claim_map.get("claim"), f"cmos486 claim {index}.claim")
        require(claim_map.get("url") == BLOG_URL, f"cmos486 claim {index} has wrong URL")
        require("third-party" in require_text(claim_map.get("evidence_type"), f"cmos486 claim {index}.evidence_type"), "blog evidence is not marked as third-party")
        require_text(claim_map.get("section"), f"cmos486 claim {index}.section")
    comparability = require_mapping(inventory.get("comparability"), "comparability")
    require(comparability.get("apk_version_correlated") is False, "APK correlation must remain false")
    require_list(comparability.get("required_apk_checks"), "required_apk_checks")
    contract = require_mapping(inventory.get("kotlin_core_contract"), "kotlin_core_contract")
    require(contract.get("module") == "core:cloud", "wrong Kotlin module")
    operation_items = require_list(contract.get("operations"), "kotlin operations")
    require(all(isinstance(item, str) and item for item in operation_items), "kotlin operations must be non-empty strings")
    operations = set(operation_items)
    require({"authenticate", "listDevices", "observeEvents", "performAction"}.issubset(operations), "Kotlin contract misses auth/devices/realtime/actions")
    verification = require_mapping(inventory.get("verification"), "verification")
    require(verification.get("command") == "python3 analysis/rea/verify_analysis.py", "unexpected verification command")
    require(verification.get("success_marker") == MARKER, "unexpected verification marker")
    require(verification.get("dependencies") == "Python standard library only", "unexpected verifier dependencies")
    require_list(verification.get("negative_controls"), "verification.negative_controls")
    passes = require_list(inventory.get("analysis_passes"), "analysis_passes")
    require(passes == [
        "pass-1-complete-inventory",
        "pass-2-protocol-reread",
        "pass-3-error-omission-review",
        "pass-4-polish",
    ], "four ordered analysis passes must be recorded")

    count = 0
    for evidence in iter_evidence(inventory):
        validate_evidence_record(evidence, roots)
        count += 1
    require(count >= 50, "inventory has too few validated evidence records")
    return count


def expect_invalid(inventory: dict[str, Any], roots: dict[str, Path], label: str) -> None:
    try:
        validate_inventory(inventory, roots)
    except ValidationError:
        return
    raise ValidationError(f"negative control did not fail: {label}")


def run_negative_controls(inventory: dict[str, Any], roots: dict[str, Path]) -> None:
    try:
        validate_report("# Informe incompleto\n", roots)
    except ValidationError:
        pass
    else:
        raise ValidationError("negative control did not fail: incomplete report")

    missing_topics = copy.deepcopy(inventory)
    del missing_topics["duhow_wibox"]["code_confirmed"]["mqtt_topics"]
    expect_invalid(missing_topics, roots, "missing MQTT topics")

    broken_evidence = copy.deepcopy(inventory)
    broken_evidence["duhow_wibox"]["code_confirmed"]["uart"]["evidence"][0]["path"] = "missing/evidence.txt"
    expect_invalid(broken_evidence, roots, "broken evidence path")

    incomplete = copy.deepcopy(inventory)
    incomplete["components"] = []
    expect_invalid(incomplete, roots, "empty components")


def main() -> int:
    try:
        require(REPORT_PATH.is_file(), f"missing report: {REPORT_PATH.relative_to(ROOT)}")
        report = REPORT_PATH.read_text(encoding="utf-8")
        inventory = read_json(INVENTORY_PATH)
        roots = source_roots(inventory)
        validate_report(report, roots)
        validate_inventory(inventory, roots)
        run_negative_controls(inventory, roots)
    except (ValidationError, OSError, UnicodeError) as cause:
        print(f"REA_ANALYSIS_ERROR: {cause}", file=sys.stderr)
        return 1
    print(MARKER)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
