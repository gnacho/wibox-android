#!/usr/bin/env python3
from pathlib import Path
import re
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_FILES = {
    "README.md": 1200,
    "docs/rea-analysis.md": 5000,
    "docs/apk-analysis.md": 5000,
    "docs/ui-inventory.md": 5000,
    "docs/protocol.md": 3000,
    "docs/ui-implementation.md": 2000,
    "analysis/integration-contract.json": 500,
    "core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt": 300,
    "core/cloud/verify_protocol.py": 500,
    "core/cloud/verify_no_secrets.py": 500,
}
FORBIDDEN_BINARY_SUFFIXES = {".apk", ".aar", ".jar", ".so", ".bks"}
PLACEHOLDER_PATTERN = re.compile(r"\bTODO\s*\(|\bFIXME\b|NotImplementedError")


def read(root: Path, relative: str) -> str:
    return (root / relative).read_text(encoding="utf-8")


def require_text(errors: list[str], text: str, required: str, label: str) -> None:
    if required not in text:
        errors.append(f"{label} does not contain required text: {required}")


def scan_sources(root: Path) -> list[str]:
    errors: list[str] = []
    for base in (root / "app/src", root / "core/cloud/src"):
        if not base.is_dir():
            continue
        for path in base.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(root)
            if path.suffix.lower() in FORBIDDEN_BINARY_SUFFIXES:
                errors.append(f"Reference or opaque binary under source tree: {relative}")
            if path.suffix == ".kt":
                text = path.read_text(encoding="utf-8")
                match = PLACEHOLDER_PATTERN.search(text)
                if match:
                    errors.append(f"Implementation placeholder in {relative}: {match.group(0)}")
                if "-----BEGIN PRIVATE KEY-----" in text or "-----BEGIN RSA PRIVATE KEY-----" in text:
                    errors.append(f"Private key material in {relative}")
    for base in (root / "app/src", root / "core/cloud/src"):
        candidate = base / "main/java/com/fermax"
        if candidate.exists():
            errors.append(f"Original package tree copied into implementation: {candidate.relative_to(root)}")
    return errors


def screenshot_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a PNG with an IHDR header")
    return struct.unpack(">II", data[16:24])


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="wibox-project-check-") as directory:
        root = Path(directory)
        source = root / "app/src/main/kotlin/dev/nacho/wibox/Valid.kt"
        source.parent.mkdir(parents=True)
        source.write_text("package dev.nacho.wibox\n\nclass Valid\n", encoding="utf-8")
        if scan_sources(root):
            raise AssertionError("Positive source control failed")
        source.write_text("class Valid { fun missing() = TODO() }\n", encoding="utf-8")
        if not any("placeholder" in error for error in scan_sources(root)):
            raise AssertionError("Negative placeholder control failed")
        errors: list[str] = []
        require_text(errors, "alpha beta", "gamma", "control")
        if not errors:
            raise AssertionError("Negative documentation control failed")


def verify(root: Path) -> tuple[list[str], dict[str, int]]:
    errors = scan_sources(root)
    for relative, minimum in REQUIRED_FILES.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"Missing required artifact: {relative}")
        elif path.stat().st_size < minimum:
            errors.append(f"Artifact is unexpectedly small: {relative} ({path.stat().st_size} bytes)")

    if not errors or all((root / relative).is_file() for relative in REQUIRED_FILES):
        readme = read(root, "README.md")
        for required in ("./gradlew", ":core:cloud:test", "assembleDebug", "connectedDebugAndroidTest", "ANDROID_HOME"):
            require_text(errors, readme, required, "README.md")
        if "mock" not in readme.lower() and "simulad" not in readme.lower():
            errors.append("README.md does not document simulated tests")

        protocol = read(root, "docs/protocol.md")
        for required in ("/qvoauthv2/token", "/openapi-tdk/device/status", "MQTT", "P2P", "refresh"):
            require_text(errors, protocol, required, "docs/protocol.md")

        ui = read(root, "docs/ui-implementation.md")
        for required in ("#78c9e7", "#f4f4f4", "fastlane", ".png"):
            require_text(errors, ui, required, "docs/ui-implementation.md")

        rea = read(root, "docs/rea-analysis.md")
        require_text(errors, rea, "MIT", "docs/rea-analysis.md")
        require_text(errors, rea, "d48e472fa6a664cacbb400560e673533005795ce", "docs/rea-analysis.md")

    gitignore = read(root, ".gitignore") if (root / ".gitignore").is_file() else ""
    for required in (".unlazy/", "local.properties", "*.apk", "*.keystore"):
        require_text(errors, gitignore, required, ".gitignore")

    core_tests = list((root / "core/cloud/src/test").rglob("*.kt"))
    app_unit_tests = list((root / "app/src/test").rglob("*.kt"))
    android_tests = list((root / "app/src/androidTest").rglob("*.kt"))
    if not core_tests:
        errors.append("No core unit tests found")
    if not app_unit_tests:
        errors.append("No app unit tests found")
    if not android_tests:
        errors.append("No Android instrumented tests found")

    screenshots = sorted((root / "fastlane").rglob("*.png"))
    if len(screenshots) < 3:
        errors.append(f"At least three real UI screenshots are required, found {len(screenshots)}")
    for screenshot in screenshots:
        try:
            width, height = screenshot_size(screenshot)
            if width < 360 or height < 640:
                errors.append(f"Screenshot dimensions are too small: {screenshot.relative_to(root)} ({width}x{height})")
        except (OSError, ValueError) as exc:
            errors.append(f"Invalid screenshot {screenshot.relative_to(root)}: {exc}")

    apk = root / "app/build/outputs/apk/debug/app-debug.apk"
    apk_size = 0
    if not apk.is_file():
        errors.append("Debug APK has not been assembled")
    else:
        apk_size = apk.stat().st_size
        try:
            with zipfile.ZipFile(apk) as archive:
                names = set(archive.namelist())
                if "AndroidManifest.xml" not in names or "classes.dex" not in names:
                    errors.append("APK lacks AndroidManifest.xml or classes.dex")
                corrupt = archive.testzip()
                if corrupt:
                    errors.append(f"Corrupt APK member: {corrupt}")
        except zipfile.BadZipFile:
            errors.append("Debug APK is not a valid ZIP")

    metrics = {
        "core_tests": len(core_tests),
        "app_unit_tests": len(app_unit_tests),
        "android_tests": len(android_tests),
        "screenshots": len(screenshots),
        "apk_bytes": apk_size,
    }
    return errors, metrics


def main() -> int:
    self_test()
    errors, metrics = verify(ROOT)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(
        "Artifacts: "
        f"{metrics['core_tests']} core test files, "
        f"{metrics['app_unit_tests']} app unit test files, "
        f"{metrics['android_tests']} instrumented test files, "
        f"{metrics['screenshots']} screenshots, "
        f"APK {metrics['apk_bytes']} bytes"
    )
    print("WIBOX_PROJECT_VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
