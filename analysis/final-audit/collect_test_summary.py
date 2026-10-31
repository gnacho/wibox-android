#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET

GROUP_PATHS = {
    "core": ("core/cloud/build/test-results/testDebugUnitTest",),
    "app_unit": ("app/build/test-results/testDebugUnitTest",),
    "app_instrumented": ("app/build/outputs/androidTest-results/connected",),
    "external_api_unit": ("external-api/build/test-results/testDebugUnitTest",),
}
FILTERED_GROUPS = {
    "external_api_instrumented": ("app/build/outputs/androidTest-results/connected", "ExternalApi"),
}
COUNT_FIELDS = ("tests", "failures", "errors", "skipped")


def _fixture_isolated(path: Path) -> bool:
    return not any(part.startswith("debug-") for part in path.parts)


def collect_filtered(root: Path, relative_path: str, class_filter: str) -> dict:
    base = root / relative_path
    files = sorted(p for p in base.rglob("TEST-*.xml") if _fixture_isolated(p)) if base.is_dir() else []
    if not files:
        raise ValueError(f"No test XML files found in {relative_path}")
    result = {field: 0 for field in COUNT_FIELDS}
    result["suites"] = 0
    result["sources"] = []
    for path in files:
        parsed = ET.parse(path).getroot()
        suites = [parsed] if parsed.tag == "testsuite" else list(parsed.findall("testsuite"))
        if not suites:
            raise ValueError(f"Unexpected root element in {path}")
        file_matches = 0
        for suite in suites:
            matches = [
                case for case in suite.iter("testcase")
                if class_filter in (case.get("classname") or "")
            ]
            result["tests"] += len(matches)
            result["failures"] += sum(1 for case in matches if case.find("failure") is not None)
            result["errors"] += sum(1 for case in matches if case.find("error") is not None)
            result["skipped"] += sum(1 for case in matches if case.find("skipped") is not None)
            file_matches += len(matches)
        if file_matches:
            result["suites"] += 1
            result["sources"].append(path.relative_to(root).as_posix())
    if result["tests"] < 1:
        raise ValueError(f"Empty test group in {relative_path} for filter {class_filter}")
    if any(result[field] for field in ("failures", "errors", "skipped")):
        raise ValueError(f"Unacceptable test result in {relative_path}: {result}")
    return result


def collect_group(root: Path, relative_paths: tuple[str, ...]) -> dict:
    files: list[Path] = []
    for relative in relative_paths:
        base = root / relative
        if base.is_dir():
            files.extend(sorted(p for p in base.rglob("TEST-*.xml") if _fixture_isolated(p)))
    if not files:
        raise ValueError(f"No test XML files found in {', '.join(relative_paths)}")
    result = {field: 0 for field in COUNT_FIELDS}
    result["suites"] = 0
    result["sources"] = []
    for path in files:
        parsed = ET.parse(path).getroot()
        suites = [parsed] if parsed.tag == "testsuite" else list(parsed.findall("testsuite"))
        if not suites:
            raise ValueError(f"Unexpected root element in {path}")
        for suite in suites:
            values = {}
            for field in COUNT_FIELDS:
                raw = suite.attrib.get(field)
                if raw is None or not raw.isdigit():
                    raise ValueError(f"Invalid {field} count in {path}")
                values[field] = int(raw)
                result[field] += values[field]
            if values["tests"] != values["failures"] + values["errors"] + values["skipped"] + sum(
                1 for case in suite.iter("testcase") if not list(case)
            ):
                raise ValueError(f"Inconsistent test counts in {path}")
            result["suites"] += 1
        result["sources"].append(path.relative_to(root).as_posix())
    if result["tests"] < 1:
        raise ValueError(f"Empty test group in {', '.join(relative_paths)}")
    if any(result[field] for field in ("failures", "errors", "skipped")):
        raise ValueError(f"Unacceptable test result in {', '.join(relative_paths)}: {result}")
    return result


def collect(root: Path) -> dict:
    summary = {group: collect_group(root, paths) for group, paths in GROUP_PATHS.items()}
    for group, (path, class_filter) in FILTERED_GROUPS.items():
        summary[group] = collect_filtered(root, path, class_filter)
    summary["totals"] = {
        field: sum(summary[group][field] for group in (*GROUP_PATHS, *FILTERED_GROUPS))
        for field in ("tests", "failures", "errors", "skipped", "suites")
    }
    return summary


def write_fixture(root: Path) -> None:
    for group, paths in {**GROUP_PATHS, **{g: (p,) for g, (p, _) in FILTERED_GROUPS.items()}}.items():
        for index, relative in enumerate(paths):
            directory = root / relative / ("debug" if group in GROUP_PATHS else f"debug-{group}")
            directory.mkdir(parents=True, exist_ok=True)
            (directory / f"TEST-{group}-{index}.xml").write_text(
                '<testsuite name="fixture" tests="3" failures="0" errors="0" skipped="0">'
                '<testcase name="one" classname="Fixture"/>'
                '<testcase name="two" classname="Fixture"/>'
                '<testcase name="three" classname="dev.nacho.wibox.externalapi.ExternalApiDenyByDefaultTest"/>'
                '</testsuite>',
                encoding="utf-8",
            )


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="wibox-test-summary-") as temporary:
        root = Path(temporary)
        write_fixture(root)
        result = collect(root)
        assert result["totals"]["tests"] == 13
        assert result["external_api_instrumented"]["tests"] == 1
        for group, paths in GROUP_PATHS.items():
            broken = Path(temporary) / "broken"
            write_fixture(broken)
            target = broken / paths[0] / "debug" / f"TEST-{group}-0.xml"
            target.write_text(
                '<testsuite name="fixture" tests="1" failures="1" errors="0" skipped="0">'
                '<testcase name="bad" classname="Fixture"><failure/></testcase>'
                '</testsuite>',
                encoding="utf-8",
            )
            try:
                collect(broken)
            except ValueError:
                pass
            else:
                raise AssertionError(f"Negative control accepted: {group}")
        missing = Path(temporary) / "missing"
        write_fixture(missing)
        first = next((missing / GROUP_PATHS["core"][0]).rglob("TEST-*.xml"))
        first.unlink()
        try:
            collect(missing)
        except ValueError:
            pass
        else:
            raise AssertionError("Negative control accepted: missing group")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test-only", action="store_true")
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    self_test()
    if args.self_test_only:
        print("WIBOX_TEST_SUMMARY_SELF_TEST_VERIFIED")
        return 0
    summary = collect(args.root.resolve())
    output = args.output or args.root / "analysis/final-audit/test-summary.json"
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(
        "WIBOX_TEST_SUMMARY_VERIFIED "
        f"tests={summary['totals']['tests']} suites={summary['totals']['suites']} "
        "failures=0 errors=0 skipped=0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
