#!/usr/bin/env python3
"""Verify the Wibox static analysis and exercise a negative control."""
from __future__ import annotations

import copy
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APK_DIR = ROOT / 'analysis/apk'
EXPECTED_PACKAGE = 'com.fermax.wibox'
EXPECTED_VERSION = 'v2.2'
EXPECTED_CODE = 18
EXPECTED_HASH = '08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce'
ANDROID = '{http://schemas.android.com/apk/res/android}'
REQUIRED_CLOUD = {
    'bootstrap', 'app_upgrade', 'service_discovery', 'user_auth', 'oauth',
    'alarm', 'push_openapi', 'shadow_http', 'mqtt', 'local_device',
    'media_p2p', 'firebase', 'privacy', 'connectivity_probes',
}
REQUIRED_REPORT_MARKERS = [
    '## 2. Artefacto, integridad y herramientas',
    '## 3. Manifiesto y superficie exportada',
    '## 4. Navegación y tecnología UI',
    '## 5. Red, endpoints, headers y autenticación',
    '## 6. Pairing, dispositivos y acciones',
    '## 7. WebSocket, MQTT, SIP, WebRTC y P2P',
    '## 8. Persistencia y criptografía',
    '## 9. Errores, timeouts y reintentos',
    '## 10. Seguridad, obfuscación y pinning',
    '## 11. SDKs y bibliotecas nativas',
    '## 12. Contraste con morluto/rea y firmware relacionado',
    '## 13. Limitaciones e HIPÓTESIS explícitas',
]
REQUIRED_SNIPPETS = {
    'E001': 'SERVER_ADDRESS = "fermax.qvcloud.net"',
    'E002': 'SDKConfig.DEBUG_HTTP = true',
    'E014': 'qvDevice.setPassword("admin")',
    'E027': 'QvHttpLoggingInterceptor.Level.BODY',
    'E028': 'keyStore.load(inputStreamOpen, "123456".toCharArray())',
    'E030': 'checkServerTrusted(X509Certificate[] x509CertificateArr, String str)',
    'E033': 'linkedHashMap.put("grant_type", "password")',
    'E037': 'QvShadowConfig.MQTT_SUBSCRIBE_PATH_CUSTOM',
    'E045': 'PRAGMA cipher_migrate',
    'E049': 'SSLContext.getInstance("SSL")',
    'E050': '/webs/version/app/version_check.jsp',
    'E056': 'data.get("AlarmEvent")',
    'E064': 'http://94.101.81.118/privacy/privacy_policy.html',
    'E071': 'public static boolean isRooted()',
    'E072': 'c2df4ea5ac3d277b197bf5c2c05c38efb7a4bdf339db4078fb5c2737c1a6bd9a',
    'E073': 'if (SDKConfig.SUPPORT_DEVICE_SHADOW)',
    'E074': 'QvWebConst.HTTP_PROTOCOL + useIp',
    'E075': '<root-path name="root_path" path=""/>',
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def validate(inv: dict, report: str) -> list[str]:
    errors = []

    def need(condition: bool, message: str):
        if not condition:
            errors.append(message)

    need(inv.get('schema_version') == 1, 'inventory schema_version must be 1')
    apk = inv.get('apk', {})
    need(apk.get('package') == EXPECTED_PACKAGE, 'package mismatch in inventory')
    need(apk.get('version_name') == EXPECTED_VERSION, 'versionName mismatch in inventory')
    need(apk.get('version_code') == EXPECTED_CODE, 'versionCode mismatch in inventory')
    need(apk.get('sha256') == EXPECTED_HASH, 'APK hash mismatch in inventory')
    need(apk.get('expected_sha256') == EXPECTED_HASH, 'expected APK hash mismatch')
    need(apk.get('min_sdk') == 21 and apk.get('target_sdk') == 35 and apk.get('compile_sdk') == 35, 'SDK levels mismatch')
    signer = apk.get('signer', {})
    need(signer.get('subject') == 'CN=Qualvision' and signer.get('issuer') == 'CN=Qualvision', 'APK signer identity mismatch')
    need(signer.get('certificate_sha256') == '5D:DA:34:C8:E6:DC:DD:45:BA:F5:8D:AB:64:5B:F5:96:48:B9:4C:9B:B4:36:02:9C:72:78:D0:C9:10:40:47:81', 'APK signing certificate hash mismatch')
    need('jar verified' in (APK_DIR / 'evidence/apk-integrity.txt').read_text(), 'JAR signature verification evidence missing')

    apk_path = Path(apk.get('path', ''))
    need(apk_path.is_file(), 'original APK is missing')
    if apk_path.is_file():
        need(sha256(apk_path) == EXPECTED_HASH, 'original APK content hash mismatch')
        try:
            with zipfile.ZipFile(apk_path) as archive:
                need(archive.testzip() is None, 'ZIP CRC validation failed')
                need('AndroidManifest.xml' in archive.namelist(), 'APK manifest is missing')
                need({'classes.dex', 'classes2.dex'}.issubset(archive.namelist()), 'expected DEX files are missing')
        except (zipfile.BadZipFile, OSError) as exc:
            errors.append(f'original APK is not a valid ZIP: {exc}')

    manifest_path = APK_DIR / 'evidence/AndroidManifest.xml'
    need(manifest_path.is_file(), 'decoded manifest evidence is missing')
    if manifest_path.is_file():
        try:
            manifest = ET.parse(manifest_path).getroot()
            need(manifest.get('package') == EXPECTED_PACKAGE, 'package mismatch in decoded manifest')
            need(manifest.get(ANDROID + 'versionName') == EXPECTED_VERSION, 'versionName mismatch in decoded manifest')
            need(manifest.get(ANDROID + 'versionCode') == str(EXPECTED_CODE), 'versionCode mismatch in decoded manifest')
            sdk = manifest.find('uses-sdk')
            need(sdk is not None and sdk.get(ANDROID + 'targetSdkVersion') == '35', 'targetSdk mismatch in decoded manifest')
            app = manifest.find('application')
            need(app is not None and app.get(ANDROID + 'allowBackup') == 'false', 'allowBackup must be false')
        except ET.ParseError as exc:
            errors.append(f'decoded manifest is invalid XML: {exc}')

    scope = inv.get('scope', {})
    need(scope.get('mode') == 'static_local_only', 'analysis scope must be static local only')
    need(scope.get('app_installed') is False and scope.get('device_used') is False, 'scope must prohibit install/device use')
    need(scope.get('cloud_contacted_by_analysis') is False, 'scope must prohibit cloud contact')
    need(scope.get('dynamic_behavior_claimed') is False, 'dynamic behavior must not be claimed')

    for marker in REQUIRED_REPORT_MARKERS:
        need(marker in report, f'report section missing: {marker}')
    for sec in range(1, 11):
        need(f'SEC-{sec:02d}' in report, f'report security finding SEC-{sec:02d} missing')
    need('No hay capturas reales' in report, 'report must state that real screenshots are unavailable')
    need('**HIPÓTESIS:**' in report, 'report must label hypotheses explicitly')
    need('E075' in report, 'report must reference evidence through E075')

    summary = inv.get('component_summary', {})
    need(summary.get('activities') == 37, 'expected 37 activities')
    need(summary.get('services') == 8, 'expected 8 services')
    need(summary.get('receivers') == 3, 'expected 3 receivers')
    need(summary.get('providers') == 6, 'expected 6 providers')
    exported = summary.get('exported', [])
    exported_names = {item.get('name') for item in exported}
    need(exported_names == {'com.quvii.ubell.main.view.LoadingActivity', 'com.google.firebase.iid.FirebaseInstanceIdReceiver'}, 'exported component set mismatch')

    permissions = inv.get('permissions', {})
    need(permissions.get('unique_count') == 26 and permissions.get('manifest_entries') == 27, 'permission counts mismatch')
    need('android.permission.USE_FULL_SCREEN_INTENT' in permissions.get('duplicates', []), 'duplicate full-screen permission not recorded')

    cloud = inv.get('cloud_areas', {})
    need(set(cloud) == REQUIRED_CLOUD, f'cloud areas mismatch: expected {sorted(REQUIRED_CLOUD)}')
    for area, data in cloud.items():
        need(bool(data.get('evidence')), f'cloud area has no evidence: {area}')
    need(cloud.get('oauth', {}).get('token_header') == 'token', 'OAuth token header mismatch')
    need(cloud.get('mqtt', {}).get('qos') == 1, 'MQTT QoS mismatch')
    need(cloud.get('service_discovery', {}).get('statically_resolved') is False, 'dynamic discovery must not be presented as resolved')

    registry = inv.get('evidence_registry', [])
    ids = [entry.get('id') for entry in registry]
    expected_ids = [f'E{i:03d}' for i in range(1, 76)]
    need(ids == expected_ids, 'evidence registry must contain ordered E001-E075')
    registry_ids = set(ids)
    for area, data in cloud.items():
        for evidence_id in data.get('evidence', []):
            need(evidence_id in registry_ids, f'unknown evidence {evidence_id} in cloud area {area}')
    code_path = APK_DIR / 'evidence/code-evidence.md'
    code = code_path.read_text() if code_path.is_file() else ''
    for evidence_id, snippet in REQUIRED_SNIPPETS.items():
        need(snippet in code, f'critical evidence snippet missing for {evidence_id}')
    for path in inv.get('evidence_files', []):
        need((ROOT / path).is_file(), f'evidence file missing: {path}')
    for forbidden in ('*.apk', '*.so', '*.bks', 'client.txt'):
        need(not list((APK_DIR / 'resources').rglob(forbidden)), f'forbidden sensitive/bulk resource included: {forbidden}')

    decompilation = inv.get('decompilation', {})
    need(decompilation.get('jadx_exit') == 3 and decompilation.get('reported_error_count') == 7, 'JADX limitations not recorded accurately')
    need(len(decompilation.get('explicit_jadx_error_files', [])) == 4, 'explicit JADX error files mismatch')
    need(bool(decompilation.get('limitations')), 'decompilation limitations are required')

    need(inv.get('ui_technology', {}).get('compose_used') is False, 'UI must be XML, not Compose')
    need(inv.get('protocols', {}).get('websocket', {}).get('status') == 'no_app_usage_found', 'WebSocket negative evidence missing')
    need(inv.get('protocols', {}).get('webrtc', {}).get('status') == 'no_peerconnection_found', 'WebRTC evidence mismatch')
    need(inv.get('protocols', {}).get('mqtt', {}).get('status') == 'implemented_but_disabled_by_default', 'MQTT disabled-by-default evidence missing')
    need(inv.get('protocols', {}).get('p2p', {}).get('status') == 'primary_media_transport', 'P2P transport finding missing')
    need(bool(inv.get('morluto_rea_comparison', {}).get('contrast_checklist')), 'morluto/rea comparison checklist missing')
    need(inv.get('morluto_rea_comparison', {}).get('literal_identifiers_found') is False, 'morluto/rea literal result mismatch')

    resource_manifest_path = APK_DIR / 'resources/manifest.json'
    need(resource_manifest_path.is_file(), 'resource manifest missing')
    if resource_manifest_path.is_file():
        resource_manifest = json.loads(resource_manifest_path.read_text())
        files = resource_manifest.get('files', [])
        need(resource_manifest.get('file_count') == len(files) == 189, 'resource count mismatch')
        for entry in files:
            path = APK_DIR / 'resources' / entry['path']
            need(path.is_file(), f'resource missing: {entry["path"]}')
            if path.is_file():
                need(sha256(path) == entry['sha256'], f'resource hash mismatch: {entry["path"]}')
    return errors


def main() -> int:
    try:
        inv = json.loads((APK_DIR / 'inventory.json').read_text())
        report = (ROOT / 'docs/apk-analysis.md').read_text()
    except (OSError, json.JSONDecodeError) as exc:
        print(f'FAIL: unable to load analysis inputs: {exc}')
        return 1

    errors = validate(inv, report)
    if errors:
        for error in errors:
            print(f'FAIL: {error}')
        return 1

    negative = copy.deepcopy(inv)
    negative['apk']['package'] = 'com.invalid.negative-control'
    negative_errors = validate(negative, report)
    if not any('package mismatch in inventory' in error for error in negative_errors):
        print('FAIL: negative control was not detected')
        return 1

    print('WIBOX_APK_ANALYSIS_VERIFIED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
