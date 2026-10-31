#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def parse_inventory(inventory):
    entries = inventory.get("screens", inventory.get("entries"))
    if not isinstance(entries, list):
        raise ValueError("inventory entries missing")
    edges = inventory.get("navigation_edges", [])
    return entries, edges


def parse_edgespec(path: Path):
    pairs = []
    for match in re.finditer(r'EdgeSpec\(\s*"([^"]+)"\s*,\s*"([^"]+)"', path.read_text(encoding="utf-8")):
        pairs.append((match.group(1), match.group(2)))
    return pairs


def validate():
    errors = []

    inventory_path = ROOT / "analysis/apk/ui-inventory.json"
    inventory = load(inventory_path)
    entries, edges = parse_inventory(inventory)
    if len(entries) != 38:
        errors.append(f"inventory entries must be 38, got {len(entries)}")
    if len(edges) != 71:
        errors.append(f"inventory edges must be 71, got {len(edges)}")

    expected_edges = {(edge["from"], edge["to"]) for edge in edges}
    declared_edges = parse_edgespec(ROOT / "app/src/main/kotlin/dev/nacho/wibox/ui/NavigationEdges.kt")
    if len(declared_edges) != 71:
        errors.append(f"NavigationEdges.kt must declare 71 edges, got {len(declared_edges)}")
    if set(declared_edges) != expected_edges:
        errors.append("NavigationEdges.kt edges differ from inventory edges")

    route_coverage = load(ROOT / "analysis/ui-modernization/route-coverage.json")
    destinations = route_coverage.get("destinations", {})
    if len(destinations) != 33:
        errors.append(f"route coverage must list 33 destinations, got {len(destinations)}")
    visual = [k for k, v in destinations.items() if isinstance(v, dict) and v.get("kind") == "visual"]
    cache_only = [k for k, v in destinations.items() if isinstance(v, dict) and v.get("kind") == "cache"]
    if len(visual) != 32 or len(cache_only) != 1:
        errors.append(f"route coverage must split 32 visual + 1 cache, got {len(visual)}/{len(cache_only)}")
    omitted = route_coverage.get("legacy_omitted", [])
    if set(omitted) != {"guide", "product_choose", "add_method_legacy", "wifi_list_legacy", "wifi_set_legacy"}:
        errors.append("route coverage legacy omission set mismatch")

    manifest = load(ROOT / "analysis/ui-modernization/manifest.json")
    captures = manifest.get("captures", [])
    if len(captures) != 32:
        errors.append(f"manifest must list 32 captures, got {len(captures)}")
    seen = set()
    for capture in captures:
        path = ROOT / "analysis/ui-modernization/captures" / Path(capture["file"]).name
        data = path.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if digest != capture["sha256"]:
            errors.append(f"sha256 mismatch for {capture['file']}")
        if Path(capture["file"]).name in seen:
            errors.append(f"duplicated capture {capture['file']}")
        seen.add(Path(capture["file"]).name)
        if str(capture.get("size_px")) != "1080x2400":
            errors.append(f"unexpected capture size for {capture['file']}")

    captured_surfaces = {name[:-4].removeprefix("CAP-").removesuffix("-360-font1-light-demo") for name in seen}
    if captured_surfaces != set(visual):
        missing = sorted(set(visual) - captured_surfaces)
        errors.append(f"visual surfaces without capture: {missing}")

    android_tests = " ".join(
        path.read_text(encoding="utf-8") for path in (ROOT / "app/src/androidTest").rglob("*.kt")
    )
    if "LocalFlow" not in android_tests:
        errors.append("cache relay not covered by instrumented tests")

    evidence = (ROOT / "docs/ui-modernization-evidence.md").read_text(encoding="utf-8")
    for section in ("Contraste", "Matriz", "Límites"):
        if section not in evidence:
            errors.append(f"evidence doc missing section {section}")

    return errors


def self_test():
    return validate()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test-only", action="store_true")
    args = parser.parse_args()
    errors = self_test()
    if args.self_test_only and not errors:
        print("WIBOX_UI_SELF_TEST_VERIFIED")
        return 0
    if errors:
        for error in errors:
            print(f"UI_MODERNIZATION_ERROR: {error}")
        return 1
    print("WIBOX_UI_MODERNIZATION_VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
