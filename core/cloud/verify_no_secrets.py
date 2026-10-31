#!/usr/bin/env python3
import base64
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SCAN_ROOTS = [ROOT / "core/cloud", ROOT / "docs/protocol.md"]
SKIP_PARTS = {"build", ".gradle", "__pycache__"}
TEXT_SUFFIXES = {".kt", ".kts", ".json", ".md", ".txt", ".py"}

FORBIDDEN_LITERALS = [
    "-----BEGIN " + "PRIVATE KEY-----",
    "-----BEGIN " + "RSA PRIVATE KEY-----",
    "AllowAll" + "HostnameVerifier",
    "trust" + "AllCerts",
    "LogLevel." + "BODY",
    "HttpLoggingInterceptor.Level." + "BODY",
    "AES/" + "ECB",
    "admin/" + "admin",
    "retryOnConnectionFailure(" + "true)",
    "Authorization: " + "Bearer ",
    "qv" + "cloud.net",
    "firebase" + "io.com",
    "47.254." + "155.166",
    "192.168." + "111.1",
    "94.101." + "81.118",
]

CREDENTIAL_PATTERN = re.compile(
    r"(?i)(?:password|passwd|pwd|access[_-]?token|refresh[_-]?token|api[_-]?key|secret)"
    r"\s*[=:]\s*[\"']([^\"']+)[\"']"
)
JWT_PATTERN = re.compile(r"[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{3,}")
PRIVATE_PATH_PATTERN = re.compile("(?:/ho" + "me/|/Us" + "ers/|[A-Za-z]:\\\\\\\\Users\\\\\\\\)")
GOOGLE_KEY_PATTERN = re.compile(r"AIza[0-9A-Za-z_-]{20,}")


def fixture_jwt(value: str) -> bool:
    parts = value.split(".")
    if len(parts) != 3 or parts[2] != "fixture-signature":
        return False
    try:
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
    except (ValueError, json.JSONDecodeError):
        return False
    return payload.get("fixture") is True


def scan_text(relative: str, text: str) -> list[str]:
    errors: list[str] = []
    for literal in FORBIDDEN_LITERALS:
        if literal in text:
            errors.append(f"{relative}: forbidden security or endpoint material")
    if PRIVATE_PATH_PATTERN.search(text):
        errors.append(f"{relative}: personal filesystem path")
    if GOOGLE_KEY_PATTERN.search(text):
        errors.append(f"{relative}: embedded API key shape")
    for match in CREDENTIAL_PATTERN.finditer(text):
        value = match.group(1)
        if "fixture" not in value.lower() and not fixture_jwt(value):
            errors.append(f"{relative}: non-fixture credential assignment")
    for match in JWT_PATTERN.finditer(text):
        if not fixture_jwt(match.group(0)):
            errors.append(f"{relative}: non-fixture JWT material")
    return errors


def scanned_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for scan_root in SCAN_ROOTS:
        candidates = [scan_root] if scan_root.is_file() else scan_root.rglob("*")
        for path in candidates:
            if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
                continue
            if any(part in SKIP_PARTS for part in path.parts):
                continue
            files.append(path)
    return sorted(set(files))


def verify(root: Path, overrides: dict[str, str] | None = None) -> list[str]:
    overrides = overrides or {}
    errors: list[str] = []
    for path in scanned_files(root):
        relative = path.relative_to(root).as_posix()
        text = overrides.get(relative)
        if text is None:
            text = path.read_text(encoding="utf-8")
        errors.extend(scan_text(relative, text))
    return errors


def self_test(root: Path) -> None:
    baseline = verify(root)
    if baseline:
        raise AssertionError(f"Positive secret control failed: {baseline}")
    negative_values = [
        "-----BEGIN " + "PRIVATE KEY-----\nfixture",
        "Authorization: " + "Bearer fixture",
        "https://" + "qv" + "cloud.net",
        "pass" + "word = \"correct-horse\"",
        "AllowAll" + "HostnameVerifier",
        "/ho" + "me/private-user/file",
        "eyJhbGciOiJub25lIn0" + "." + "eyJleHAiOjE4MDAwMDM2MDB9" + "." + "not-a-fixture",
    ]
    for index, value in enumerate(negative_values):
        if not scan_text(f"negative-{index}", value):
            raise AssertionError(f"Negative secret control did not fail: {index}")
    fixture = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJleHAiOjE4MDAwMDM2MDAsImZpeHR1cmUiOnRydWUsIm5hbWUiOiJhY2Nlc3MtcGFzc3dvcmQifQ.fixture-signature"
    if scan_text("positive-fixture", f'accessToken = "{fixture}"'):
        raise AssertionError("Narrow fixture allowlist rejected a fictitious token")


def main() -> int:
    self_test(ROOT)
    errors = verify(ROOT)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("WIBOX_NO_SECRETS_VERIFIED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
