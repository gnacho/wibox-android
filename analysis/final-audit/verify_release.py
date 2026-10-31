#!/usr/bin/env python3
from __future__ import annotations

import copy
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys

REPO = Path(__file__).resolve().parents[2]
METADATA = Path(__file__).with_name("release-metadata.json")
REQUIRED_EVIDENCE = ("signature", "alignment", "install", "screenshot")


def metadata_errors(metadata: dict) -> list[str]:
    errors: list[str] = []
    if metadata.get("package") != "dev.nacho.wibox":
        errors.append("Unexpected package")
    if not isinstance(metadata.get("versionCode"), int) or metadata["versionCode"] < 1:
        errors.append("Invalid versionCode")
    if not isinstance(metadata.get("versionName"), str) or not metadata["versionName"]:
        errors.append("Invalid versionName")
    if not isinstance(metadata.get("sizeBytes"), int) or metadata["sizeBytes"] <= 0:
        errors.append("Invalid APK size")
    if not re.fullmatch(r"[0-9a-f]{64}", str(metadata.get("sha256", ""))):
        errors.append("Invalid SHA-256")
    for field in ("signatureVerified", "zipAlignmentVerified", "installedAndStarted"):
        if metadata.get(field) is not True:
            errors.append(f"{field} is not true")
    if not str(metadata.get("installSerial", "")).startswith("emulator-"):
        errors.append("Installation target is not an emulator")
    for field in REQUIRED_EVIDENCE:
        if not isinstance(metadata.get("evidence", {}).get(field), str):
            errors.append(f"Missing evidence path: {field}")
    return errors


def self_test(metadata: dict) -> None:
    if metadata_errors(metadata):
        raise AssertionError(f"Positive metadata control failed: {metadata_errors(metadata)}")
    mutations = (
        ("installSerial", "761559e4"),
        ("signatureVerified", False),
        ("sha256", "not-a-sha256"),
        ("sizeBytes", 0),
    )
    for key, value in mutations:
        candidate = copy.deepcopy(metadata)
        candidate[key] = value
        if not metadata_errors(candidate):
            raise AssertionError(f"Negative metadata control was accepted: {key}")
    print("RELEASE_METADATA_CONTROLS_VERIFIED")


def run(command: list[str]) -> str:
    result = subprocess.run(command, text=True, capture_output=True, timeout=120, check=False)
    if result.returncode != 0:
        output = (result.stdout + result.stderr).strip()[-3000:]
        raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(command)}\n{output}")
    return result.stdout + result.stderr


def main() -> int:
    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    self_test(metadata)
    errors = metadata_errors(metadata)
    if errors:
        raise AssertionError(errors)

    apk = Path(metadata["apk"]).expanduser().resolve()
    if not apk.is_file():
        raise FileNotFoundError(apk)
    payload = apk.read_bytes()
    if len(payload) != metadata["sizeBytes"]:
        raise AssertionError("APK size does not match metadata")
    digest = hashlib.sha256(payload).hexdigest()
    if digest != metadata["sha256"]:
        raise AssertionError("APK SHA-256 does not match metadata")

    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
    candidates = sorted((sdk / "build-tools").glob("*"), key=lambda path: path.name)
    if not candidates:
        raise FileNotFoundError("No Android build-tools found")
    tools = candidates[-1]
    apksigner = tools / "apksigner"
    aapt = tools / "aapt"
    zipalign = tools / "zipalign"
    for tool in (apksigner, aapt, zipalign):
        if not tool.is_file():
            raise FileNotFoundError(tool)

    run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)])
    run([str(zipalign), "-c", "-P", "4", "-v", "4", str(apk)])
    badging = run([str(aapt), "dump", "badging", str(apk)])
    package_line = badging.splitlines()[0]
    expected = f"package: name='{metadata['package']}' versionCode='{metadata['versionCode']}' versionName='{metadata['versionName']}'"
    if expected not in package_line:
        raise AssertionError(f"Unexpected APK badging: {package_line}")

    evidence: dict[str, Path] = {}
    for name in REQUIRED_EVIDENCE:
        evidence[name] = Path(metadata["evidence"][name]).resolve()
        if not evidence[name].is_file():
            raise FileNotFoundError(evidence[name])
    signature_text = evidence["signature"].read_text(encoding="utf-8", errors="replace")
    if "FAILED" in signature_text or "DOES NOT VERIFY" in signature_text:
        raise AssertionError("Signature evidence contains a failure")
    install_text = evidence["install"].read_text(encoding="utf-8", errors="replace")
    for token in ("Success", "Status: ok", "LaunchState: COLD", "Main screen ready before screenshot"):
        if token not in install_text:
            raise AssertionError(f"Missing install evidence token: {token}")
    png = evidence["screenshot"].read_bytes()
    if len(png) < 24 or png[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError("Release screenshot is not a PNG")
    width, height = struct.unpack(">II", png[16:24])
    if width < 240 or height < 320:
        raise AssertionError("Release screenshot dimensions are invalid")

    print(
        "WIBOX_FINAL_RELEASE_VERIFIED "
        f"package={metadata['package']} version={metadata['versionName']} "
        f"size={metadata['sizeBytes']} sha256={digest} screenshot={width}x{height}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"RELEASE_VERIFICATION_FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
