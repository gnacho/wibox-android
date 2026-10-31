#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    root = args.root.resolve()
    provider_path = root / "analysis/architecture/provider-matrix.json"
    ui_path = root / "analysis/apk/ui-inventory.json"
    dynamic_path = root / "analysis/dynamic/inventory.json"
    external_api_path = root / "analysis/external-api/api-contract.json"
    provider = load_json(provider_path)
    ui = load_json(ui_path)
    dynamic = load_json(dynamic_path)
    external_api = load_json(external_api_path) if external_api_path.is_file() else None

    capabilities = provider["capabilities"]
    screens = ui["screens"]
    edges = ui["navigation_edges"]
    assert len(capabilities) == 64, len(capabilities)
    assert len({item["id"] for item in capabilities}) == 64
    assert len(screens) == 38, len(screens)
    assert len(edges) == 71, len(edges)

    capability_rows = []
    for item in capabilities:
        compatibility = item["compatibility_now"]
        normalized = {}
        for dimension, value in compatibility.items():
            if isinstance(value, str):
                normalized[dimension] = {"level": value, "details": []}
            elif isinstance(value, dict):
                normalized[dimension] = value
            else:
                raise ValueError(f"Unsupported compatibility value for {item['id']}:{dimension}")
        compatibility = normalized
        dynamic_update = next(
            (update for update in dynamic.get("unsupported_updates", []) if update["operation"] == "app_upgrade_check"),
            None,
        )
        if item["id"] == "app.update_check" and dynamic_update and dynamic_update.get("request_response_observed"):
            compatibility["api_wire"]["level"] = "request_response_observed"
            compatibility["behavior"]["level"] = "dynamic_success_contract_observed"
        evidence_levels = sorted({value["level"] for value in compatibility.values()})
        capability_rows.append({
            "id": item["id"],
            "name": item["name"],
            "evidence_references": item["evidence"],
            "current_implementation": item["current"],
            "providers": item["providers"],
            "compatibility_now": compatibility,
            "observed_evidence_levels": evidence_levels,
            "acceptance": item["acceptance"],
            "final_verification": "PENDING_FINAL_IMPLEMENTATION_AUDIT",
        })

    screen_rows = [{
        "id": item["id"],
        "kind": item["kind"],
        "class_or_fragment": item["class"],
        "title": item["title"],
        "layout": item["layout"],
        "states": item["states"],
        "navigation": item["navigation"],
        "reachability": item["reachability"],
        "evidence_references": item["evidence"],
        "resources": item["resources"],
        "final_verification": "PENDING_FINAL_UI_AUDIT",
    } for item in screens]

    source_hashes = {
        str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in [
            provider_path,
            ui_path,
            dynamic_path,
            *([external_api_path] if external_api_path.is_file() else []),
        ]
    }
    audit = {
        "schema_version": 1,
        "status": "PENDING_FINAL_IMPLEMENTATION_AUDIT",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_hashes": source_hashes,
        "summary": {
            "capabilities_total": len(capabilities),
            "screens_total": len(screens),
            "navigation_edges_total": len(edges),
            "dynamic_unsupported_updates": [item["operation"] for item in dynamic["unsupported_updates"]],
            "external_api_design": None if external_api is None else {
                "status": external_api["status"],
                "methods": len(external_api["methods"]),
                "models": len(external_api["models"]),
                "service_implemented": False,
            },
        },
        "capabilities": capability_rows,
        "screens": screen_rows,
        "navigation_edges": edges,
        "dynamic_evidence": dynamic["unsupported_updates"],
    }
    output_dir = root / "analysis/final-audit"
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / "audit-matrix.json"
    output.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(
        "WIBOX_AUDIT_MATRIX_GENERATED "
        f"capabilities={len(capabilities)} screens={len(screens)} edges={len(edges)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
