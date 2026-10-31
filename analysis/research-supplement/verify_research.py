#!/usr/bin/env python3
"""Verify the public research supplement without network or third-party packages."""

from __future__ import annotations

import copy
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATH = ROOT / "analysis/research-supplement/source-matrix.json"
FINDINGS_PATH = ROOT / "analysis/research-supplement/findings.json"
DOC_PATH = ROOT / "docs/research-supplement.md"
PROVIDER_PATH = ROOT / "analysis/architecture/provider-matrix.json"
DYNAMIC_PATH = ROOT / "analysis/dynamic/inventory.json"
SUCCESS = "WIBOX_RESEARCH_SUPPLEMENT_VERIFIED"

EXPECTED_URLS_BY_ID = {
    "duhow_repo": "https://github.com/duhow/wibox",
    "duhow_install": "https://github.com/duhow/wibox/blob/main/INSTALL.md",
    "linuch_part1": "https://web.archive.org/web/20211121234612/https://linuch.pl/blog/fermax-wayfi-wideodomofon-hack-czesc-1",
    "linuch_part2": "https://web.archive.org/web/20211124125931/https://linuch.pl/blog/fermax-wayfi-wideodomofon-hack-czesc-2",
    "linuch_part3": "https://web.archive.org/web/20211128173119/https://linuch.pl/blog/fermax-wayfi-wideodomofon-hack-czesc-3",
    "cmos486_blog": "https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant",
    "cmos486_media_repo": "https://github.com/cmos486/wibox-media",
    "cmos486_card_repo": "https://github.com/cmos486/wibox-intercom-video-card",
    "segator_media_repo": "https://github.com/segator/wibox-media",
    "ha_forum_post7": "https://community.home-assistant.io/t/wi-box-fermax-integration-home-assistant-help/525777/7",
    "esp32_repo": "https://github.com/h0r4ci0/Control-Remoto-de-Portero-FERMAX",
    "recon_talk": "https://cfp.recon.cx/recon-2026/talk/QQZNHF/",
    "troopers_home": "https://troopers.de/",
    "troopers_duox_talk": "https://troopers.de/troopers26/talks/uscbkd",
    "circl_cve": "https://vulnerability.circl.lu/vuln/CVE-2026-86585",
    "fermax_advisories": "https://fermax.com/security-advisories",
    "nvd_page": "https://nvd.nist.gov/vuln/detail/CVE-2026-86585",
    "nvd_api": "https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=CVE-2026-86585",
    "cve_awg_api": "https://cveawg.mitre.org/api/cve/CVE-2026-86585",
    "fermax_product": "https://www.fermax.com/spain/single-products/f03266-desvio-de-llamada-wifi-vds-wi-box",
}
SUPPORTING_URLS = {
    "https://api.github.com/repos/duhow/wibox",
    "https://api.github.com/repos/cmos486/wibox-media",
    "https://api.github.com/repos/cmos486/wibox-intercom-video-card",
    "https://api.github.com/repos/segator/wibox-media",
    "https://api.github.com/repos/h0r4ci0/Control-Remoto-de-Portero-FERMAX",
}
EXPECTED_CLASSIFICATIONS = {
    "duhow_repo": "custom_wibox_duhow",
    "duhow_install": "custom_wibox_duhow",
    "linuch_part1": "stock_wayfi_historical",
    "linuch_part2": "stock_wayfi_historical",
    "linuch_part3": "custom_wayfi_historical",
    "cmos486_blog": "custom_wibox_cmos486",
    "cmos486_media_repo": "custom_wibox_cmos486",
    "cmos486_card_repo": "custom_wibox_ha_ui",
    "segator_media_repo": "custom_wibox_segator",
    "ha_forum_post7": "community_wibox_mixed",
    "esp32_repo": "physical_fermax_esp32_bridge",
    "recon_talk": "duox_plus_research",
    "troopers_home": "conference_context",
    "troopers_duox_talk": "duox_plus_research",
    "circl_cve": "duox_plus_monitor_cve",
    "fermax_advisories": "duox_plus_monitor_cve",
    "nvd_page": "duox_plus_monitor_cve",
    "nvd_api": "duox_plus_monitor_cve",
    "cve_awg_api": "duox_plus_monitor_cve",
    "fermax_product": "official_wibox_product",
}
REQUIRED_CONCLUSIONS = {
    "DUHOW-CUSTOM-001",
    "DUHOW-SOFIA-002",
    "WAYFI-SCOPE-003",
    "WAYFI-UART-004",
    "CMOS-WEBRTC-005",
    "CMOS-CAPABILITIES-006",
    "CMOS-OTA-007",
    "CARD-UI-008",
    "SEGATOR-SIP-009",
    "FORK-DIFF-010",
    "ESP32-PHYSICAL-011",
    "DUOX-TALKS-012",
    "FORUM-COMMUNITY-013",
    "OFFICIAL-F03266-014",
    "STATIC-ONLY-015",
}
BASELINE_HASHES = {
    "docs/rea-analysis.md": "19bce697e5e1b5482cfd3249a953cb9e32d2761086b97d60f3b5f8bf1d8338c7",
    "docs/provider-architecture.md": "675b059d23499ec551115966026e2bb352dbf1b3183b775fdca64a23ba403a63",
    "docs/dynamic-analysis.md": "2b7efc28e3987785153e8472ae678e729bf3b0e9837a73092674cdf7925b0160",
    "analysis/architecture/provider-matrix.json": "912878dcef3f697cef5d7fc561dc1d1542a241673993343909fbdf4d6dd6a915",
    "analysis/dynamic/inventory.json": "f31824799489b4e89712be985c611efc80f6502a830fd513e74c540bb9523ea6",
}


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def nonempty_string(value) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate(source, findings, provider, dynamic, document: str, immutable_hashes=None):
    errors = []
    sources = source.get("sources", [])
    source_by_id = {}
    for item in sources:
        source_id = item.get("id")
        if not nonempty_string(source_id):
            errors.append("source without id")
            continue
        if source_id in source_by_id:
            errors.append(f"duplicate source id: {source_id}")
        source_by_id[source_id] = item

    expected_ids = set(EXPECTED_URLS_BY_ID)
    if set(source_by_id) != expected_ids:
        errors.append(f"source id coverage mismatch: {sorted(set(source_by_id) ^ expected_ids)}")

    vocabulary = set(source.get("classification_vocabulary", []))
    all_urls = set()
    for source_id, expected_url in EXPECTED_URLS_BY_ID.items():
        item = source_by_id.get(source_id)
        if item is None:
            continue
        if item.get("url") != expected_url:
            errors.append(f"URL mismatch for {source_id}")
        all_urls.add(item.get("url"))
        for supporting in item.get("supporting_urls", []):
            if not nonempty_string(supporting) or not supporting.startswith("https://"):
                errors.append(f"invalid supporting URL in {source_id}")
            else:
                all_urls.add(supporting)
        if item.get("consulted_at") != "2026-10-31":
            errors.append(f"consultation date missing or wrong in {source_id}")
        if not nonempty_string(item.get("type")) or not nonempty_string(item.get("credibility")):
            errors.append(f"type/credibility missing in {source_id}")
        if item.get("classification") not in vocabulary:
            errors.append(f"classification not in vocabulary for {source_id}")
        expected_classification = EXPECTED_CLASSIFICATIONS[source_id]
        if item.get("classification") != expected_classification:
            errors.append(f"{source_id} must remain {expected_classification}")
        if item.get("stock_evidence_effect") != "no_provider_elevation":
            errors.append(f"unexpected stock elevation marker in {source_id}")
        if item.get("access", {}).get("http_status") != 200:
            errors.append(f"source not recorded as accessible: {source_id}")
        if not isinstance(item.get("product"), dict):
            errors.append(f"product/model scope missing in {source_id}")
        claims = item.get("verified_claims")
        if not isinstance(claims, list) or not claims:
            errors.append(f"verified claims missing in {source_id}")
        else:
            for claim in claims:
                if not nonempty_string(claim.get("claim")):
                    errors.append(f"empty claim in {source_id}")
                refs = claim.get("evidence")
                if not isinstance(refs, list) or not refs or any(not nonempty_string(ref) for ref in refs):
                    errors.append(f"claim without file/section evidence in {source_id}")
        if not item.get("unverified_claims"):
            errors.append(f"unverified limits missing in {source_id}")
        repository = item.get("repository")
        if repository is not None:
            if not isinstance(repository, dict):
                errors.append(f"invalid repository metadata in {source_id}")
            else:
                commit = repository.get("commit", "")
                if not re.fullmatch(r"[0-9a-f]{40}", commit):
                    errors.append(f"commit missing or invalid in {source_id}")
                if not nonempty_string(repository.get("license")):
                    errors.append(f"license missing in {source_id}")
                if not nonempty_string(repository.get("license_evidence")):
                    errors.append(f"license evidence missing in {source_id}")
                if not nonempty_string(repository.get("branch")):
                    errors.append(f"branch missing in {source_id}")

    expected_urls = set(EXPECTED_URLS_BY_ID.values()) | SUPPORTING_URLS
    if all_urls != expected_urls:
        errors.append(f"URL coverage mismatch: {sorted(all_urls ^ expected_urls)}")
    if len(expected_urls) != 25:
        errors.append("internal expected URL count must be 25")

    comparisons = source.get("repository_comparisons", [])
    if len(comparisons) != 1:
        errors.append("exactly one repository comparison is required")
    elif comparisons[0].get("merge_base") != "a55b8de9b19b954238a3c160e283a2c1867e8ce6":
        errors.append("repository comparison merge-base mismatch")
    elif comparisons[0].get("unique_commits") != {"cmos486": 29, "segator": 3}:
        errors.append("repository comparison commit counts mismatch")

    conclusions = findings.get("conclusions", [])
    conclusion_ids = set()
    for conclusion in conclusions:
        conclusion_id = conclusion.get("id")
        if not nonempty_string(conclusion_id) or conclusion_id in conclusion_ids:
            errors.append(f"missing or duplicate conclusion id: {conclusion_id}")
        conclusion_ids.add(conclusion_id)
        if conclusion.get("provider_effect") != "no_elevation":
            errors.append(f"provider elevation in conclusion {conclusion_id}")
        evidence = conclusion.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            errors.append(f"conclusion {conclusion_id} has no source evidence")
            continue
        for ref in evidence:
            if ref.get("source") not in source_by_id:
                errors.append(f"unknown source reference in conclusion {conclusion_id}: {ref.get('source')}")
            if not ref.get("refs") or any(not nonempty_string(value) for value in ref.get("refs", [])):
                errors.append(f"empty evidence reference in conclusion {conclusion_id}")
    if conclusion_ids != REQUIRED_CONCLUSIONS:
        errors.append(f"conclusion coverage mismatch: {sorted(conclusion_ids ^ REQUIRED_CONCLUSIONS)}")
    if findings.get("overall_provider_effect") != "no_elevation":
        errors.append("overall provider effect must be no_elevation")

    security = findings.get("security_scope", {})
    cve = security.get("cve_2026_86585", {})
    if cve.get("confirmed") is not True:
        errors.append("CVE-2026-86585 must be confirmed by official sources")
    for source_id in ("fermax_advisories", "cve_awg_api", "nvd_api"):
        if source_id not in cve.get("confirmation_sources", []):
            errors.append(f"CVE official confirmation missing: {source_id}")
    if cve.get("cwe") != "CWE-347" or cve.get("affected_versions") != "< 01.48.001":
        errors.append("CVE CWE or affected version mismatch")
    if "DUOX PLUS" not in cve.get("product", ""):
        errors.append("CVE product must remain DUOX PLUS monitor firmware")
    if "01.48.001" not in cve.get("mitigation", ""):
        errors.append("CVE mitigation version missing")
    expected_platforms = {"VEO-WIFI", "VEO-XS-WIFI", "VEO-XS-WIFI-DDA", "VEO-XL-WIFI", "VEO-XL-WIFI-DDA"}
    if set(cve.get("structured_platforms", [])) != expected_platforms:
        errors.append("CVE structured platform list mismatch")
    if cve.get("exploitation_tested") is not False or cve.get("exploit_instructions_included") is not False:
        errors.append("CVE exploitation must remain out of scope")

    f03266 = security.get("wibox_f03266", {})
    if (
        f03266.get("classification") != "not_listed_no_evidence_of_applicability"
        or f03266.get("affected") is not None
        or f03266.get("applies_by_inference") is not False
    ):
        errors.append("CVE must not be applied to F03266")
    qvcloud = security.get("stock_qvcloud", {})
    if (
        qvcloud.get("new_vulnerability_evidence") is not False
        or qvcloud.get("applies_by_inference") is not False
        or qvcloud.get("provider_elevation") is not False
    ):
        errors.append("CVE must not create QVCloud evidence")

    future = findings.get("future_provider_implications", [])
    future_by_id = {item.get("id"): item for item in future}
    for item in future:
        if item.get("available_now") is not False or item.get("stock_compatibility_claimed") is not False:
            errors.append(f"false current compatibility in future implication {item.get('id')}")
        if not item.get("requirements"):
            errors.append(f"future requirements missing in {item.get('id')}")
    for future_id in ("mqtt-duhow", "mqtt-custom-media", "self-hosted-api", "binder-aidl"):
        item = future_by_id.get(future_id)
        if item is None or item.get("status") != "future_not_implemented":
            errors.append(f"future provider must remain not implemented: {future_id}")

    guard = findings.get("provider_matrix_guard", {})
    if guard.get("baseline_sha256") != BASELINE_HASHES["analysis/architecture/provider-matrix.json"]:
        errors.append("recorded provider baseline hash mismatch")
    if sha256(PROVIDER_PATH) != guard.get("baseline_sha256"):
        errors.append("provider matrix baseline hash changed")
    totals = provider.get("totals", {})
    if totals.get("capabilities") != 64 or totals.get("screens") != 38:
        errors.append("provider matrix totals changed")
    capabilities = {item.get("id"): item for item in provider.get("capabilities", [])}
    for capability_id, expected_status in guard.get("expected_stock_states", {}).items():
        capability = capabilities.get(capability_id, {})
        stock = capability.get("providers", {}).get("stock-qvcloud", {})
        if capability.get("current") != expected_status or stock.get("status") != expected_status:
            errors.append(f"stock capability elevated or changed: {capability_id}")
    for provider_id, expected in guard.get("expected_future_providers", {}).items():
        actual = provider.get("providers", {}).get(provider_id, {})
        if actual.get("phase") != expected.get("phase") or actual.get("implemented_as_complete_provider") is not False:
            errors.append(f"future provider incorrectly implemented: {provider_id}")

    dynamic_guard = findings.get("dynamic_inventory_guard", {})
    if sha256(DYNAMIC_PATH) != dynamic_guard.get("baseline_sha256"):
        errors.append("dynamic inventory baseline hash changed")
    if dynamic.get("result", {}).get("runtime_cloud_compatibility_claimed") is not False:
        errors.append("dynamic runtime cloud compatibility must remain false")
    if dynamic.get("isolation", {}).get("physical_actions_attempted") != 0:
        errors.append("dynamic physical actions must remain zero")

    for source_id in expected_ids:
        if source_id not in document:
            errors.append(f"source id absent from research document: {source_id}")
    for phrase in (
        "duhow/wibox es firmware custom, no stock",
        "no un servidor WebRTC nativo",
        "No se aplica el CVE a Wi-Box",
        "No cambia ninguna compatibilidad",
        "oportunidad de diseño, no disponibilidad actual",
    ):
        if phrase not in document:
            errors.append(f"required research guard phrase missing: {phrase}")
    if "\u2014" in document or "\u2013" in document:
        errors.append("em dash or en dash found in research document")

    if immutable_hashes is not None:
        for relative, expected_hash in immutable_hashes.items():
            if sha256(ROOT / relative) != expected_hash:
                errors.append(f"immutable baseline changed: {relative}")
    return errors


def expect_rejection(name, source, findings, provider, dynamic, document, mutate, expected_fragment):
    candidate_source = copy.deepcopy(source)
    candidate_findings = copy.deepcopy(findings)
    candidate_provider = copy.deepcopy(provider)
    candidate_dynamic = copy.deepcopy(dynamic)
    candidate_document = document
    mutate(candidate_source, candidate_findings, candidate_provider, candidate_dynamic)
    errors = validate(candidate_source, candidate_findings, candidate_provider, candidate_dynamic, candidate_document)
    if not errors:
        raise AssertionError(f"negative control {name} was accepted")
    if expected_fragment and not any(expected_fragment in error for error in errors):
        raise AssertionError(f"negative control {name} rejected for an unexpected reason: {errors}")
    print(f"NEGATIVE_CONTROL_OK {name}")


def main() -> int:
    source = load_json(SOURCE_PATH)
    findings = load_json(FINDINGS_PATH)
    provider = load_json(PROVIDER_PATH)
    dynamic = load_json(DYNAMIC_PATH)
    document = DOC_PATH.read_text(encoding="utf-8")

    errors = validate(source, findings, provider, dynamic, document, BASELINE_HASHES)
    if errors:
        for error in errors:
            print(f"ERROR {error}", file=sys.stderr)
        return 1
    print(f"POSITIVE_CONTROL_OK sources={len(source['sources'])} urls=25 conclusions={len(findings['conclusions'])}")

    def stock_custom_mix(src, find, prov, dyn):
        src["sources"][0]["classification"] = "official_wibox_product"

    expect_rejection(
        "stock_custom_mix", source, findings, provider, dynamic, document,
        stock_custom_mix, "duhow_repo must remain custom_wibox_duhow",
    )

    def cve_to_f03266(src, find, prov, dyn):
        find["security_scope"]["wibox_f03266"].update({
            "classification": "affected",
            "affected": True,
            "applies_by_inference": True,
        })

    expect_rejection(
        "cve_applied_to_f03266", source, findings, provider, dynamic, document,
        cve_to_f03266, "CVE must not be applied to F03266",
    )

    def claim_without_source(src, find, prov, dyn):
        find["conclusions"].append({
            "id": "UNSOURCED-TEST",
            "classification": "custom_wibox_duhow",
            "statement": "A claim without evidence must fail",
            "evidence": [],
            "provider_effect": "no_elevation",
        })

    expect_rejection(
        "claim_without_source", source, findings, provider, dynamic, document,
        claim_without_source, "has no source evidence",
    )

    def unknown_source(src, find, prov, dyn):
        find["conclusions"][0]["evidence"][0]["source"] = "unknown-source"

    expect_rejection(
        "unknown_source_reference", source, findings, provider, dynamic, document,
        unknown_source, "unknown source reference",
    )

    def elevate_provider(src, find, prov, dyn):
        for capability in prov["capabilities"]:
            if capability["id"] == "actions.unlock":
                capability["current"] = "implemented_contract"
                capability["providers"]["stock-qvcloud"]["status"] = "implemented_contract"

    expect_rejection(
        "provider_matrix_elevation", source, findings, provider, dynamic, document,
        elevate_provider, "stock capability elevated or changed: actions.unlock",
    )

    def cve_to_qvcloud(src, find, prov, dyn):
        find["security_scope"]["stock_qvcloud"].update({
            "new_vulnerability_evidence": True,
            "applies_by_inference": True,
            "provider_elevation": True,
        })

    expect_rejection(
        "cve_applied_to_qvcloud", source, findings, provider, dynamic, document,
        cve_to_qvcloud, "CVE must not create QVCloud evidence",
    )

    print(SUCCESS)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
