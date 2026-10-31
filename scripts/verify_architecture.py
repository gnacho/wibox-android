#!/usr/bin/env python3
from pathlib import Path
import argparse
import re
import tempfile

PUBLIC_API = Path("core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt")
APP_BUILD = Path("app/build.gradle.kts")
FORBIDDEN_IMPORTS = (
    re.compile(r"^import\s+io\.ktor\."),
    re.compile(r"^import\s+okhttp3\."),
    re.compile(r"^import\s+java\.net\."),
    re.compile(r"^import\s+dev\.nacho\.wibox\.core\.cloud\.internal\."),
    re.compile(r"^import\s+dev\.nacho\.wibox\.core\.cloud\..*\.dto\."),
    re.compile(r"^import\s+com\.fermax\."),
)


def verify(root: Path) -> list[str]:
    errors: list[str] = []
    api = root / PUBLIC_API
    if not api.is_file():
        errors.append(f"Missing public cloud API: {PUBLIC_API}")

    build = root / APP_BUILD
    if not build.is_file():
        errors.append(f"Missing app build file: {APP_BUILD}")
    else:
        text = build.read_text(encoding="utf-8")
        if 'project(":core:cloud")' not in text:
            errors.append("The app does not depend on :core:cloud")
        if "libs.ktor" in text or "libs.okhttp" in text:
            errors.append("The app declares a direct HTTP transport dependency")

    sources = sorted((root / "app/src").rglob("*.kt")) if (root / "app/src").is_dir() else []
    if not sources:
        errors.append("No Kotlin sources found under app/src")
    uses_public_api = False
    for source in sources:
        text = source.read_text(encoding="utf-8")
        if re.search(r"^import\s+dev\.nacho\.wibox\.core\.cloud(\.|$)", text, re.MULTILINE):
            uses_public_api = True
        for number, line in enumerate(text.splitlines(), start=1):
            if any(pattern.search(line) for pattern in FORBIDDEN_IMPORTS):
                errors.append(f"Forbidden app dependency at {source.relative_to(root)}:{number}: {line.strip()}")
    if sources and not uses_public_api:
        errors.append("No app Kotlin source imports the public cloud API")
    return errors


def make_fixture(root: Path, forbidden_import: str = "") -> None:
    api = root / PUBLIC_API
    api.parent.mkdir(parents=True, exist_ok=True)
    api.write_text("package dev.nacho.wibox.core.cloud\n\ninterface WiboxCloud\n", encoding="utf-8")
    build = root / APP_BUILD
    build.parent.mkdir(parents=True, exist_ok=True)
    build.write_text('dependencies { implementation(project(":core:cloud")) }\n', encoding="utf-8")
    source = root / "app/src/main/kotlin/dev/nacho/wibox/AppViewModel.kt"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(
        "package dev.nacho.wibox\n\n"
        "import dev.nacho.wibox.core.cloud.WiboxCloud\n"
        f"{forbidden_import}\n",
        encoding="utf-8",
    )


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="wibox-architecture-valid-") as directory:
        root = Path(directory)
        make_fixture(root)
        result = verify(root)
        if result:
            raise AssertionError(f"Positive control failed: {result}")
    with tempfile.TemporaryDirectory(prefix="wibox-architecture-invalid-") as directory:
        root = Path(directory)
        make_fixture(root, "import dev.nacho.wibox.core.cloud.internal.TokenDto")
        result = verify(root)
        if not any("Forbidden app dependency" in error for error in result):
            raise AssertionError(f"Negative control failed: {result}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test-only", action="store_true")
    args = parser.parse_args()
    self_test()
    if args.self_test_only:
        print("WIBOX_ARCHITECTURE_SELF_TEST_VERIFIED")
        return 0
    errors = verify(Path.cwd())
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_ARCHITECTURE_VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
