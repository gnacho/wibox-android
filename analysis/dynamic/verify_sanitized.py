#!/usr/bin/env python3
import ipaddress
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DYNAMIC_DIR = ROOT / "analysis/dynamic"
REPORT_PATH = ROOT / "docs/dynamic-analysis.md"
ALLOWED_SUFFIXES = {".json", ".md", ".py", ".txt"}
FORBIDDEN_SUFFIXES = {".pcap", ".pcapng", ".cap", ".har", ".flow", ".mitm", ".log", ".bin", ".apk", ".so", ".db", ".sqlite", ".png", ".jpg", ".jpeg", ".webp"}
SUCCESS = "WIBOX_DYNAMIC_SANITIZED_VERIFIED"

JWT_RE = re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b")
UUID_RE = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b")
IP_RE = re.compile(r"(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])")
EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
USERINFO_RE = re.compile(r"https?://[^\s/:@]+:[^\s/@]+@", re.IGNORECASE)
AUTH_RE = re.compile(r"\b(?:authorization|proxy-authorization)\s*:\s*(?:bearer|basic|digest)\s+([^\s`\"']+)", re.IGNORECASE)
SECRET_RE = re.compile(r"\b(?:access_token|refresh_token|id_token|password|passwd|pwd|secret|api[_-]?key|cookie|set-cookie)\b[\"']?\s*[:=]\s*[\"']?([^\s,\"'}\]`]+)", re.IGNORECASE)
OPAQUE_RE = re.compile(r"\b(?=[A-Za-z0-9]{24,63}\b)(?=[A-Za-z0-9]*[A-Za-z])(?=[A-Za-z0-9]*[0-9])[A-Za-z0-9]+\b")
HEX32_RE = re.compile(r"\b[0-9a-fA-F]{32}\b")
ALLOWED_SECRET_VALUES = {"redacted", "masked", "none", "null", "false", "true", "unknown", "not_observed", "not_generated", "n/a"}


def is_placeholder(value):
    normalized = value.strip("\"'").lower()
    return normalized.startswith("<") or normalized.startswith("not_") or normalized.startswith("no_") or normalized.startswith("fixture-") or normalized in ALLOWED_SECRET_VALUES


def forbidden_name(path):
    return path.suffix.lower() in FORBIDDEN_SUFFIXES or path.suffix.lower() not in ALLOWED_SUFFIXES


def scan_text(text, label):
    errors = []
    if "\x00" in text:
        errors.append(f"{label}: contenido binario")

    for pattern, message in (
        (JWT_RE, "JWT real"),
        (UUID_RE, "UUID opaco"),
        (EMAIL_RE, "email"),
        (HEX32_RE, "identificador hexadecimal de 32 caracteres"),
    ):
        for match in pattern.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            errors.append(f"{label}:{line}: {message}")

    for match in IP_RE.finditer(text):
        candidate = match.group(0)
        try:
            address = ipaddress.ip_address(candidate)
        except ValueError:
            continue
        if address.is_private or address.is_loopback or address.is_link_local:
            line = text.count("\n", 0, match.start()) + 1
            errors.append(f"{label}:{line}: IP privada o local")

    for match in USERINFO_RE.finditer(text):
        if "<" not in match.group(0):
            line = text.count("\n", 0, match.start()) + 1
            errors.append(f"{label}:{line}: credenciales en userinfo")

    for pattern, message in ((AUTH_RE, "authorization con valor"), (SECRET_RE, "secreto o cookie con valor")):
        for match in pattern.finditer(text):
            value = match.group(1)
            if not is_placeholder(value):
                line = text.count("\n", 0, match.start()) + 1
                errors.append(f"{label}:{line}: {message}")

    for match in OPAQUE_RE.finditer(text):
        value = match.group(0)
        if value.lower() in {"wibox_dynamic_analysis_verified", "wibox_dynamic_sanitized_verified"}:
            continue
        line = text.count("\n", 0, match.start()) + 1
        errors.append(f"{label}:{line}: posible ID opaco {value[:6]}...")

    for line_number, line in enumerate(text.splitlines(), 1):
        if chr(0x2014) in line or chr(0x2013) in line:
            errors.append(f"{label}:{line_number}: guion tipográfico prohibido")
    return errors


def self_tests():
    errors = []
    positive = "\n".join((
        "Host documentado: fermax.qvcloud.net",
        "token: <redacted>",
        "access_token=not_generated",
        "password: <password>",
        "https://<user>:<password>@unit.test/tdkcgi",
        "Cookie: not_observed",
    ))
    require_positive = scan_text(positive, "positive-control")
    if require_positive:
        errors.append("control positivo sanitizado rechazado: " + "; ".join(require_positive))

    negative_values = {
        "jwt": "eyJ" + "a" * 20 + "." + "b" * 20 + "." + "c" * 20,
        "uuid": "550e8400-" + "e29b-41d4-a716-446655440000",
        "private_ip": "192" + ".168.1.50",
        "email": "fixture" + "@example.com",
        "secret_value": "access_" + "token=" + "A" * 32,
        "auth": "Authorization: " + "Bearer " + "B" * 32,
        "userinfo": "https://fixture:" + "fixture@" + "unit.test/path",
        "opaque": "ab12" * 8,
        "hex32": "a1" * 16,
    }
    expected_labels = {
        "jwt": "JWT real",
        "uuid": "UUID opaco",
        "private_ip": "IP privada",
        "email": "email",
        "secret_value": "secreto o cookie",
        "auth": "authorization",
        "userinfo": "userinfo",
        "opaque": "ID opaco",
        "hex32": "32 caracteres",
    }
    for name, value in negative_values.items():
        findings = scan_text(value, name)
        if not any(expected_labels[name] in finding for finding in findings):
            errors.append(f"control negativo no detectado: {name}")

    if not forbidden_name(Path("capture." + "pcap")):
        errors.append("control negativo PCAP no detectado")
    if not forbidden_name(Path("flows." + "mitm")):
        errors.append("control negativo MITM no detectado")
    if not forbidden_name(Path("screen." + "png")):
        errors.append("control negativo de captura no detectado")
    if forbidden_name(Path("inventory." + "json")):
        errors.append("control positivo JSON rechazado")
    if not scan_text("texto" + chr(0) + "binario", "binary-control"):
        errors.append("control negativo binario no detectado")
    if not scan_text("texto con guion " + chr(0x2014), "dash-control"):
        errors.append("control negativo de guion no detectado")
    return errors


def main():
    errors = self_tests()
    files = sorted(path for path in DYNAMIC_DIR.rglob("*") if path.is_file())
    files.append(REPORT_PATH)
    if not files or not REPORT_PATH.is_file():
        errors.append("artefactos obligatorios ausentes")
    for path in files:
        label = str(path.relative_to(ROOT))
        if forbidden_name(path):
            errors.append(f"{label}: formato raw o no publicable")
            continue
        try:
            data = path.read_bytes()
            text = data.decode("utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            errors.append(f"{label}: lectura o UTF-8 inválido: {exc}")
            continue
        errors.extend(scan_text(text, label))
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(SUCCESS)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
