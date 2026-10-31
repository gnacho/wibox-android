#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

TEST_GROUPS = (
    "core",
    "app_unit",
    "app_instrumented",
    "external_api_unit",
    "external_api_instrumented",
)


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def indexed(items):
    if not isinstance(items, list):
        return {}
    result = {}
    for item in items:
        if isinstance(item, dict) and isinstance(item.get("id"), str) and item["id"]:
            result[item["id"]] = item
    return result


def edge_pairs(items):
    if not isinstance(items, list):
        return []
    pairs = []
    for item in items:
        if isinstance(item, (list, tuple)) and len(item) == 2:
            pairs.append((item[0], item[1]))
        elif isinstance(item, dict) and "from" in item and "to" in item:
            pairs.append((item["from"], item["to"]))
        else:
            pairs.append(("invalid", repr(item)))
    return pairs


def review_errors(expected, actual, kind):
    errors = []
    expected_items = indexed(expected)
    actual_items = indexed(actual)
    if len(expected_items) != len(expected):
        errors.append(f"Invalid or duplicate {kind} in audit matrix")
    if len(actual_items) != len(actual):
        errors.append(f"Invalid or duplicate {kind} in final report")
    for missing in sorted(set(expected_items) - set(actual_items)):
        errors.append(f"Missing {kind}: {missing}")
    for unexpected in sorted(set(actual_items) - set(expected_items)):
        errors.append(f"Unexpected {kind}: {unexpected}")
    for item_id in sorted(set(expected_items) & set(actual_items)):
        item = actual_items[item_id]
        for field in ("status", "evidence", "notes"):
            if not item.get(field):
                errors.append(f"{kind}:{item_id} missing {field}")
        if kind == "capability" and not item.get("cloud_notes"):
            errors.append(f"capability:{item_id} missing cloud_notes")
    return errors


def validate(report, matrix, runtime, tests):
    errors = []
    if report.get("status") != "COMPLETE_WITH_DOCUMENTED_LIMITS":
        errors.append("Final report must include documented limits")
    if report.get("hardware_verified") is not False:
        errors.append("Hardware compatibility cannot be certified")
    summary = report.get("summary", {})
    expected_counts = {
        "capabilities": len(matrix.get("capabilities", [])),
        "screens": len(matrix.get("screens", [])),
        "navigation_edges": len(matrix.get("navigation_edges", [])),
    }
    for key, count in expected_counts.items():
        if summary.get(key) != count:
            errors.append(f"Summary {key} must be {count}")
    errors.extend(review_errors(matrix.get("capabilities"), report.get("capabilities"), "capability"))
    errors.extend(review_errors(matrix.get("screens"), report.get("screens"), "screen"))
    expected_edges = edge_pairs(matrix.get("navigation_edges"))
    actual_edges = edge_pairs(report.get("navigation_edges_reviewed"))
    if actual_edges != expected_edges:
        errors.append("Navigation edge review mismatch")

    if report.get("ui_modernization_verified") is not True:
        errors.append("UI modernization not verified")
    if report.get("research_supplement_verified") is not True:
        errors.append("Research supplement not verified")
    if runtime.get("status") != "implemented":
        errors.append("External API runtime not implemented")
    for key in ("service", "sdk", "secret_scan_passed", "unauthorized_denied"):
        if runtime.get(key) is not True:
            errors.append(f"External API runtime missing {key}")
    signatures = runtime.get("two_signature_tests", {})
    if signatures.get("status") != "passed":
        errors.append("Two-signature API tests not passed")
    if signatures.get("signatures", 0) != 2:
        errors.append("Exactly two test signatures required")
    if runtime.get("authorization") != "uid_package_signing_allowlist":
        errors.append("Unexpected external API authorization model")
    if runtime.get("method_count") != 15 or runtime.get("model_count") != 43:
        errors.append("External API surface does not match its verified design")

    for group in TEST_GROUPS:
        values = tests.get(group)
        if not isinstance(values, dict):
            errors.append(f"Missing test summary: {group}")
            continue
        for key in ("failures", "errors", "skipped"):
            if values.get(key) != 0:
                errors.append(f"Test summary {group}.{key} must be zero")
        if not isinstance(values.get("tests"), int) or values["tests"] < 1:
            errors.append(f"Test summary {group}.tests must be positive")
    return errors


def make_valid(matrix):
    return (
        {
            "status": "COMPLETE_WITH_DOCUMENTED_LIMITS",
            "hardware_verified": False,
            "summary": {
                "capabilities": len(matrix["capabilities"]),
                "screens": len(matrix["screens"]),
                "navigation_edges": len(matrix["navigation_edges"]),
            },
            "capabilities": [
                {
                    "id": item["id"],
                    "status": "reviewed",
                    "evidence": ["final-audit"],
                    "notes": "Compatibility and limits reviewed",
                    "cloud_notes": "Current provider evidence retained without elevation",
                }
                for item in matrix["capabilities"]
            ],
            "screens": [
                {
                    "id": item["id"],
                    "status": "reviewed",
                    "evidence": ["final-audit"],
                    "notes": "Navigation, states and captures reviewed",
                }
                for item in matrix["screens"]
            ],
            "navigation_edges_reviewed": edge_pairs(matrix["navigation_edges"]),
            "ui_modernization_verified": True,
            "research_supplement_verified": True,
        },
        {
            "status": "implemented",
            "service": True,
            "sdk": True,
            "secret_scan_passed": True,
            "unauthorized_denied": True,
            "two_signature_tests": {"status": "passed", "signatures": 2},
            "authorization": "uid_package_signing_allowlist",
            "method_count": 15,
            "model_count": 43,
        },
        {
            group: {"tests": 1, "failures": 0, "errors": 0, "skipped": 0}
            for group in TEST_GROUPS
        },
    )


def self_test(matrix):
    report, runtime, tests = make_valid(matrix)
    initial = validate(report, matrix, runtime, tests)
    if initial:
        raise AssertionError(f"Positive control failed: {initial}")
    mutations = {
        "capabilities": lambda r, p, t: r["capabilities"].pop(),
        "screens": lambda r, p, t: r["screens"].pop(),
        "edges": lambda r, p, t: r["navigation_edges_reviewed"].pop(),
        "hardware": lambda r, p, t: r.update(hardware_verified=True),
        "ui": lambda r, p, t: r.update(ui_modernization_verified=False),
        "runtime": lambda r, p, t: p.update(status="design_only"),
        "tests": lambda r, p, t: t["core"].update(failures=1),
    }
    for name, mutate in mutations.items():
        report, runtime, tests = make_valid(matrix)
        mutate(report, runtime, tests)
        if not validate(report, matrix, runtime, tests):
            raise AssertionError(f"Negative control accepted: {name}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test-only", action="store_true")
    args = parser.parse_args()
    base = Path("analysis/final-audit")
    matrix = load(base / "audit-matrix.json")
    self_test(matrix)
    if args.self_test_only:
        print("WIBOX_FINAL_AUDIT_SELF_TEST_VERIFIED")
        return 0
    required = {
        "report": base / "final-report.json",
        "runtime": base / "external-api-runtime.json",
        "tests": base / "test-summary.json",
    }
    for name, path in required.items():
        if not path.is_file():
            print(f"FINAL_AUDIT_BLOCKED: missing {name}: {path}")
            return 1
    report = load(required["report"])
    runtime = load(required["runtime"])
    tests = load(required["tests"])
    errors = validate(report, matrix, runtime, tests)
    if errors:
        for error in errors:
            print(f"FINAL_AUDIT_ERROR: {error}")
        return 1
    print(
        "WIBOX_FINAL_AUDIT_VERIFIED "
        f"capabilities={len(matrix['capabilities'])} "
        f"screens={len(matrix['screens'])} "
        f"edges={len(matrix['navigation_edges'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
