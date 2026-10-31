#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

serial="emulator-5554"
package="dev.nacho.wibox"
activity="dev.nacho.wibox/.MainActivity"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
build_tools="$sdk/build-tools/37.0.0"
adb="$sdk/platform-tools/adb"
key_dir="$HOME/.config/android-keystores"
keystore="$key_dir/wibox-release.keystore"
pass_file="$key_dir/wibox-release.pass"
key_pass_file="$key_dir/wibox-release.keypass"
alias="wibox"
dist="$root/dist"
download_dir="$HOME/Descargas/wibox-kotlin"
evidence="$root/analysis/final-audit"
unsigned="$root/app/build/outputs/apk/release/app-release-unsigned.apk"
aligned="$dist/Wibox-QVCloud-aligned.apk"
final_apk="$dist/Wibox-QVCloud.apk"
download_apk="$download_dir/Wibox-QVCloud.apk"

for tool in "$adb" "$build_tools/zipalign" "$build_tools/apksigner" "$build_tools/aapt2"; do
    test -x "$tool"
done
command -v keytool >/dev/null
command -v python3 >/dev/null
test "$("$adb" -s "$serial" get-state)" = "device"

./gradlew clean :app:assembleRelease
test -s "$unsigned"

umask 077
mkdir -p "$key_dir" "$dist" "$download_dir" "$evidence"
if [ ! -f "$pass_file" ]; then
    python3 -c 'import secrets; print(secrets.token_urlsafe(48))' > "$pass_file"
fi
chmod 600 "$pass_file"
if [ ! -f "$key_pass_file" ]; then
    cp "$pass_file" "$key_pass_file"
fi
chmod 600 "$key_pass_file"
if [ ! -f "$keystore" ]; then
    password="$(cat "$pass_file")"
    keytool -genkeypair -noprompt \
        -keystore "$keystore" \
        -storepass "$password" \
        -keypass "$password" \
        -alias "$alias" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "CN=Wibox Kotlin, OU=Local Release, O=Personal, C=ES" \
        > "$evidence/release-key-create.txt" 2>&1
fi
chmod 600 "$keystore"

"$build_tools/zipalign" -p -f 4 "$unsigned" "$aligned"
"$build_tools/apksigner" sign \
    --ks "$keystore" \
    --ks-key-alias "$alias" \
    --ks-pass "file:$pass_file" \
    --key-pass "file:$key_pass_file" \
    --out "$final_apk" \
    "$aligned"
rm -f "$aligned"

"$build_tools/apksigner" verify --verbose --print-certs "$final_apk" > "$evidence/release-signature.txt"
"$build_tools/zipalign" -c -v 4 "$final_apk" > "$evidence/release-alignment.txt"
"$build_tools/aapt2" dump badging "$final_apk" > "$evidence/release-badging.txt"
cp "$final_apk" "$download_apk"
test "$(sha256sum "$final_apk" | awk '{print $1}')" = "$(sha256sum "$download_apk" | awk '{print $1}')"
(
    cd "$dist"
    sha256sum "Wibox-QVCloud.apk"
) > "$dist/SHA256SUMS"
cp "$dist/SHA256SUMS" "$download_dir/SHA256SUMS"

{
    "$adb" -s "$serial" install -r "$download_apk"
    "$adb" -s "$serial" shell pm clear "$package"
    "$adb" -s "$serial" shell am start -W -n "$activity"
    pid="$("$adb" -s "$serial" shell pidof "$package" | tr -d '\r')"
    test -n "$pid"
    printf 'Running package pid: %s\n' "$pid"
} > "$evidence/release-install.txt" 2>&1
ready=0
for ((attempt=0; attempt<30; attempt++)); do
    if "$adb" -s "$serial" shell uiautomator dump /sdcard/wibox-release-ui.xml >/dev/null 2>&1 \
        && "$adb" -s "$serial" shell cat /sdcard/wibox-release-ui.xml 2>/dev/null | grep -q 'Local demo mode'; then
        ready=1
        break
    fi
    sleep 2
done
if [ "$ready" -ne 1 ]; then
    echo 'The release main screen did not become ready' >&2
    exit 1
fi
printf 'Main screen ready before screenshot\n' >> "$evidence/release-install.txt"
"$adb" -s "$serial" exec-out screencap -p > "$evidence/release-smoke.png"
python3 - <<'PY'
from pathlib import Path
import struct
p = Path("analysis/final-audit/release-smoke.png")
data = p.read_bytes()
assert data[:8] == b"\x89PNG\r\n\x1a\n"
width, height = struct.unpack(">II", data[16:24])
assert width > 0 and height > 0
print(f"Release screenshot: {width}x{height}")
PY

badging="$(cat "$evidence/release-signature.txt" >/dev/null; head -n 1 "$evidence/release-badging.txt")"
package_name="$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
version_code="$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p")"
version_name="$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")"
test "$package_name" = "$package"
test -n "$version_code"
test -n "$version_name"
APK_PATH="$download_apk" \
APK_SHA256="$(sha256sum "$download_apk" | awk '{print $1}')" \
APK_SIZE="$(stat -c %s "$download_apk")" \
PACKAGE_NAME="$package_name" \
VERSION_CODE="$version_code" \
VERSION_NAME="$version_name" \
INSTALL_SERIAL="$serial" \
python3 - <<'PY'
import json
import os
from pathlib import Path
metadata = {
    "package": os.environ["PACKAGE_NAME"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "apk": os.environ["APK_PATH"],
    "sizeBytes": int(os.environ["APK_SIZE"]),
    "sha256": os.environ["APK_SHA256"],
    "signatureVerified": True,
    "zipAlignmentVerified": True,
    "installedAndStarted": True,
    "installSerial": os.environ["INSTALL_SERIAL"],
    "evidence": {
        "signature": "analysis/final-audit/release-signature.txt",
        "alignment": "analysis/final-audit/release-alignment.txt",
        "install": "analysis/final-audit/release-install.txt",
        "screenshot": "analysis/final-audit/release-smoke.png",
    },
}
Path("analysis/final-audit/release-metadata.json").write_text(
    json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(metadata, indent=2))
PY

printf 'WIBOX_RELEASE_PACKAGE_VERIFIED\n'
