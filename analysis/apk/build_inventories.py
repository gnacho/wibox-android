#!/usr/bin/env python3
"""Build structured static-analysis inventories for the Wibox APK."""
from __future__ import annotations

import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

APK_DIR = Path(__file__).resolve().parent
ROOT = APK_DIR.parents[1]
DECOMPILED = Path('/home/nacho/temp/opencode-work/wibox-jadx')
MANIFEST = APK_DIR / 'evidence/AndroidManifest.xml'
ANDROID = '{http://schemas.android.com/apk/res/android}'
APK_PATH = Path('/home/nacho/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk')
EXPECTED_HASH = '08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce'


def load_json(path: Path):
    return json.loads(path.read_text())


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def manifest_components():
    root = ET.parse(MANIFEST).getroot()
    app = root.find('application')
    result = {}
    for kind in ('activity', 'service', 'receiver', 'provider'):
        values = []
        for node in app.findall(kind):
            filters = node.findall('intent-filter')
            explicit = node.get(ANDROID + 'exported')
            item = {
                'name': node.get(ANDROID + 'name'),
                'exported_explicit': explicit,
                'exported_effective': explicit if explicit is not None else ('true' if filters else 'false'),
                'intent_filter_count': len(filters),
            }
            for key in ('permission', 'authorities', 'launchMode', 'screenOrientation', 'foregroundServiceType', 'taskAffinity', 'excludeFromRecents', 'grantUriPermissions'):
                value = node.get(ANDROID + key)
                if value is not None:
                    item[key] = value
            if kind == 'activity' and node.get(ANDROID + 'name') == 'com.quvii.ubell.main.view.LoadingActivity':
                item['deep_links'] = [{'scheme': 'wibox', 'host': 'wibox.com'}]
            values.append(item)
        result[kind + 's'] = values
    return root, app, result


def evidence_registry():
    text = (APK_DIR / 'evidence/code-evidence.md').read_text()
    entries = []
    current = None
    for line in text.splitlines():
        match = re.match(r'## (E\d{3}) ([a-z0-9_]+)', line)
        if match:
            current = {'id': match.group(1), 'marker': match.group(2), 'path': 'analysis/apk/evidence/code-evidence.md'}
            entries.append(current)
        elif current and line.startswith('Origen: `'):
            current['original'] = line.removeprefix('Origen: `').removesuffix('`')
    return entries


def build_inventory():
    manifest_root, app, components = manifest_components()
    permissions = sorted({node.get(ANDROID + 'name') for node in manifest_root.findall('uses-permission')})
    registry = evidence_registry()
    cloud_areas = {
        'bootstrap': {
            'endpoint': 'https://fermax.qvcloud.net:443',
            'purpose': 'Punto de arranque QvCore y descubrimiento P2P',
            'evidence': ['E001', 'E005'],
        },
        'app_upgrade': {
            'endpoint': 'https://matainde.qvcloud.net:1443/webs/version/app/version_check.jsp',
            'method': 'GET',
            'query': ['os=android', 'appid=2021', 'version=18', 'clientid=<runtime>'],
            'headers': ['Accept-Language'],
            'timeouts_seconds': {'connect': 10, 'read': 10, 'write': 10},
            'evidence': ['E049', 'E050'],
        },
        'service_discovery': {
            'endpoint': 'Dinámico, devuelto por QvLocationManager/P2P',
            'purpose': 'URLs regionales HTTPS para user, alarm, MQTT, OAuth, shadow y OpenAPI',
            'statically_resolved': False,
            'evidence': ['E034', 'E040'],
        },
        'user_auth': {
            'paths': ['/auth/nologin;jus_duplex=up', '/auth/nologin;jus_duplex=down', '/auth/user;jus_duplex=up', '/auth/user;jus_duplex=down'],
            'purpose': 'Registro anónimo, login, lista de dispositivos, credenciales dinámicas e invitaciones',
            'evidence': ['E004', 'E010', 'E020'],
        },
        'oauth': {
            'path': '/qvoauthv2/token',
            'method': 'GET',
            'grant_types': ['password', 'refresh_token'],
            'password_query': ['grant_type', 'client_id', 'client_type', 'oemid', 'appid', 'usr', 'pwd', 'region_id', 'client_flag'],
            'token_header': 'token',
            'evidence': ['E029', 'E033', 'E034', 'E035', 'E036'],
        },
        'alarm': {
            'paths': ['UserAlarm', 'DeviceAlarm'],
            'purpose': 'Eventos, ajustes de llamada/movimiento y borrado, páginas de 15',
            'evidence': ['E051', 'E052', 'E057'],
        },
        'push_openapi': {
            'paths': ['/openapi-tdk/client/v2/push/token', '/openapi-tdk/client/push/token', '/pushserver/api/v1/client/register'],
            'purpose': 'Registro de token FCM en backend Qualvision',
            'evidence': ['E056', 'E057'],
        },
        'shadow_http': {
            'paths': ['/openapi-tdk/device/status', '/openapi-tdk/device/status/multi', '/openapi-tdk/device/status/sub', '/openapi-tdk/device/status/unsub'],
            'token_header': 'token',
            'evidence': ['E041'],
        },
        'mqtt': {
            'endpoint': 'Dinámico: ssl:// para mqtts o tcp:// para mqtt',
            'authentication': 'clientId C_<account>_<alarmClient> y access token como password',
            'topics': ['tdk/client_api/users/get/', 'tdk/client_api/sys/get/', 'tdk/client_api/sys/update/'],
            'qos': 1,
            'evidence': ['E037', 'E038', 'E039', 'E040', 'E041'],
        },
        'local_device': {
            'endpoint': 'https://<ip-o-127.0.0.1>:<cgiPort>/tdkcgi y /tdkcgi/passwordencryption',
            'legacy_http': 'getUnbindDeviceApiUrl fuerza http:// y getDirectDeviceApi admite HTTP según dispositivo/cgiScheme',
            'pairing_ap_fallback': 'https://192.168.111.1:443',
            'authentication': 'Credenciales de dispositivo, Digest MD5 cuando está disponible',
            'evidence': ['E014', 'E031', 'E032', 'E068', 'E074'],
        },
        'media_p2p': {
            'endpoint': 'quii://<user>:<password>@127.0.0.1:<mappedPort>/...',
            'native_library': 'libqv-p2p-v2.so y liblive_player.so',
            'evidence': ['E011', 'E021', 'E023', 'E042'],
        },
        'firebase': {
            'endpoint': 'FCM y configuración embebida tdkvdp.firebaseio.com',
            'purpose': 'Eventos 19 llamada, 20 llamada atendida y 2 movimiento',
            'embedded_api_key': 'Presente, no reproducido en este inventario',
            'evidence': ['E056', 'E057'],
        },
        'privacy': {
            'endpoint': 'http://94.101.81.118/privacy/privacy_policy.html',
            'usage': 'Constante y cadena presentes; el diálogo actual carga conditions/<idioma>.html local',
            'evidence': ['E064'],
        },
        'connectivity_probes': {
            'endpoints': ['https://www.apple.com', 'https://www.google.com'],
            'purpose': 'Comprobación de conectividad del SDK',
            'evidence': ['E065'],
        },
    }
    data = {
        'schema_version': 1,
        'analysis_date': '2026-10-31',
        'scope': {
            'mode': 'static_local_only',
            'app_installed': False,
            'device_used': False,
            'cloud_contacted_by_analysis': False,
            'dynamic_behavior_claimed': False,
            'hypothesis_label_required': 'HIPÓTESIS',
        },
        'apk': {
            'path': str(APK_PATH),
            'package': manifest_root.get('package'),
            'version_name': manifest_root.get(ANDROID + 'versionName'),
            'version_code': int(manifest_root.get(ANDROID + 'versionCode')),
            'min_sdk': int(manifest_root.find('uses-sdk').get(ANDROID + 'minSdkVersion')),
            'target_sdk': int(manifest_root.find('uses-sdk').get(ANDROID + 'targetSdkVersion')),
            'compile_sdk': int(manifest_root.get(ANDROID + 'compileSdkVersion')),
            'sha256': sha256(APK_PATH),
            'expected_sha256': EXPECTED_HASH,
            'size_bytes': APK_PATH.stat().st_size,
            'zip_integrity': 'unzip -t: no errors; Python ZipFile.testzip is checked by verifier',
            'jar_signature': 'jarsigner: jar verified; signer self-signed; chain warning recorded',
            'signer': {
                'subject': 'CN=Qualvision',
                'issuer': 'CN=Qualvision',
                'certificate_sha256': '5D:DA:34:C8:E6:DC:DD:45:BA:F5:8D:AB:64:5B:F5:96:48:B9:4C:9B:B4:36:02:9C:72:78:D0:C9:10:40:47:81',
                'public_key': 'RSA 2048 bits',
                'signature_algorithm': 'SHA256withRSA',
            },
            'build_timestamp_ms': 1744710181402,
            'build_timestamp_utc': '2025-04-15T09:43:01Z',
            'build_type': 'release',
            'build_config_debug': False,
        },
        'tools': [
            {'name': 'JADX', 'version': '1.5.6', 'source': 'https://github.com/skylot/jadx/releases/tag/v1.5.6', 'archive_sha256': '545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974'},
            {'name': 'OpenJDK', 'version': '17.0.20.1'},
            {'name': 'Python', 'version': '3.14.8'},
            {'name': 'Info-ZIP UnZip', 'version': '6.00'},
            {'name': 'ImageMagick identify', 'version_command': 'identify -version, recorded in evidence/toolchain.txt'},
        ],
        'decompilation': {
            'output_dir': '/home/nacho/temp/opencode-work/wibox-jadx',
            'jadx_exit': 3,
            'reported_error_count': 7,
            'source_file_count': 6637,
            'resource_file_count': 1772,
            'explicit_jadx_error_files': ['com/google/android/gms/common/api/internal/zah.java', 'com/huawei/hms/scankit/p/C0216tc.java', 'com/quvii/qvweb/publico/utils/OkHttpUtil.java', 'o/g.java'],
            'limitations': ['No se afirma cobertura perfecta de bytecode', 'Hay métodos con código decompilado incorrecto', 'Las clases org.eclipse.paho no aparecen en sources aunque existen referencias y MqttService en el manifiesto', 'No se ejecutó código nativo ni se hizo análisis dinámico'],
        },
        'application': {
            'label': 'WI-BOX',
            'application_class': app.get(ANDROID + 'name'),
            'allow_backup': app.get(ANDROID + 'allowBackup') == 'true',
            'supports_rtl': app.get(ANDROID + 'supportsRtl') == 'true',
            'resizeable_activity': app.get(ANDROID + 'resizeableActivity') == 'true',
            'extract_native_libs': app.get(ANDROID + 'extractNativeLibs') == 'true',
            'network_security_config': None,
            'uses_cleartext_traffic_explicit': None,
            'themes': ['AppTheme', 'AppTheme.Launcher'],
        },
        'permissions': {
            'unique_count': len(permissions),
            'manifest_entries': 27,
            'duplicates': ['android.permission.USE_FULL_SCREEN_INTENT'],
            'names': permissions,
        },
        'components': components,
        'component_summary': {
            'activities': len(components['activitys']),
            'services': len(components['services']),
            'receivers': len(components['receivers']),
            'providers': len(components['providers']),
            'exported': [item for values in components.values() for item in values if item['exported_effective'] == 'true'],
        },
        'ui_technology': {
            'stack': ['XML layouts', 'Android Views', 'AppCompat Light.NoActionBar', 'Fragments', 'ViewBinding', 'MVP', 'MyGLSurfaceView'],
            'compose_used': False,
            'kotlin_present': True,
            'java_decompiled': True,
            'main_tabs': ['MainDeviceListFragment', 'MainMessageListFragment/MainMessageTypeFragment', 'MainFileFragment', 'MainMeFragment'],
            'evidence': ['E053', 'E054', 'E060'],
        },
        'cloud_areas': cloud_areas,
        'protocols': {
            'websocket': {'status': 'no_app_usage_found', 'detail': 'Solo aparece la API de OkHttp, sin newWebSocket/WebSocketListener en código propio', 'evidence': ['E029']},
            'mqtt': {'status': 'implemented_but_disabled_by_default', 'detail': 'Paho con OAuth, QoS 1 y reconexión; SUPPORT_DEVICE_SHADOW=false impide la inicialización por defecto y no se encontró override. Clases Paho no localizadas en sources, alcance dinámico no probado', 'evidence': ['E037', 'E038', 'E039', 'E040', 'E063', 'E073']},
            'sip': {'status': 'no_call_client_found', 'detail': 'Modelos para configurar parámetros SIP de dispositivo; no cliente SIP de llamada en Wibox', 'evidence': ['E021', 'E023']},
            'webrtc': {'status': 'no_peerconnection_found', 'detail': 'libWebrtcAudio.so indica procesado de audio, no prueba señalización WebRTC', 'evidence': ['E021', 'E023']},
            'p2p': {'status': 'primary_media_transport', 'detail': 'Túneles P2P nativos y URLs quii hacia puertos locales mapeados', 'evidence': ['E011', 'E021', 'E042', 'E068']},
        },
        'authentication': {
            'user_visible_login_screen': False,
            'initial_registration': 'userFreeRegister con UUID aleatorio como contraseña, seguido de userLogin',
            'device_binding': 'adminapp, authCode/QR, SHA-256 condicionado por capability y contraseña dinámica',
            'oauth': 'Password grant con usr/pwd en query GET y tokens JWT; header token, no Bearer',
            'device_http': 'Digest MD5 tras 401 cuando el dispositivo lo soporta',
            'biometrics': 'BiometricPrompt para unlock y F1, con fallback a contraseña',
            'evidence': ['E004', 'E010', 'E019', 'E031', 'E033'],
        },
        'storage': {
            'database': {'name': 'database', 'version': 11, 'engine': 'DBFlow + SQLCipher 4.5.4', 'secret': 'AES de QvEncryptKeyStoreUtil', 'records': ['Device', 'AppInfo', 'AppTask']},
            'shared_preferences': {'config': 'Credenciales, token FCM, servidor y SSID cifrados; ajustes en claro', 'oauth': 'Access y refresh tokens cifrados con EncryptUtil AES'},
            'wifi_password_default_save': True,
            'media_paths': ['SDK <29: almacenamiento externo/WI-BOX', '29: externalFilesDir Pictures/Movies', '>=30: filesDir image/video', 'cache: thumbnail, qrcode y record'],
            'evidence': ['E043', 'E044', 'E045', 'E046', 'E047', 'E069'],
        },
        'pairing': {
            'qr_format': 'apName uid authCode model separados por espacios',
            'share_query_fields': ['InviteCode', 'OwnerId', 'OemId', 'AppVersion', 'DeviceId', 'From', 'AppId'],
            'oem_id': 'G0021',
            'flows': ['QR -> reset -> AP -> router -> progreso -> bind', 'QR -> reset -> audio -> bind', 'Online LAN/manual -> bind', 'Invitación -> ShareBind/DADeviceBind'],
            'ap_defaults': {'ip': '192.168.111.1', 'port': 443, 'username': 'admin', 'password': 'admin', 'secure_alternative': 'adminapp2 + SHA-256(authCode)'},
            'audio': {'frequencies_hz': '4000 + n*150, n=0..18', 'payload': 'SSID y últimos 4 UID + contraseña WiFi', 'timeout_seconds': 150},
            'timeouts': {'bind_seconds': 35, 'online_poll_seconds': 2, 'bind_model_poll_count': 15, 'config_countdown_seconds': 150},
            'evidence': ['E007', 'E008', 'E009', 'E010', 'E012', 'E014', 'E015', 'E016', 'E017', 'E018'],
        },
        'device_features': {
            'list': 'Base local sincronizada con getDevList y estado online/offline P2P',
            'preview': 'Uno o cuatro paneles, canales, modos alta/baja/audio+foto, temporizador 90 s',
            'actions': ['unlock lock1', 'F1', 'callGuard', 'escucha', 'habla', 'captura', 'grabación', 'compartir', 'sincronizar hora', 'WiFi', 'firmware'],
            'sharing': 'Invitaciones con permisos preview/playback/alarm/unlock/F1/callGuard, límite de 100 grupos en UI',
            'events': 'Llamada 19, atendida 20, movimiento 2, páginas de 15 y foto asociada',
            'gallery': 'Fotos/vídeos locales con PhotoView, FileProvider y edición múltiple',
            'evidence': ['E019', 'E020', 'E021', 'E023', 'E024', 'E025', 'E051', 'E055', 'E062'],
        },
        'errors_and_retries': [
            {'area': 'OkHttp', 'behavior': 'retryOnConnectionFailure=true; timeout base 20 s; auth/alarm 30/35/60 s según cliente', 'evidence': ['E027', 'E050']},
            {'area': 'OAuth', 'behavior': 'Un retry si había token cacheado, lo borra; 403 corta; otros errores investigan servicio', 'evidence': ['E035']},
            {'area': 'bind', 'behavior': 'retry(4) solo errores 100152003/100152005 y modo permitido, espera 5 s; timeout 35 s', 'evidence': ['E009', 'E010']},
            {'area': 'CGI', 'behavior': 'retry(1) con limpieza de puerto para timeout y caso local', 'evidence': ['E009', 'E018']},
            {'area': 'device status', 'behavior': 'Timeout 20 s y retry(2) solo TimeoutException en método deprecated', 'evidence': ['E018']},
            {'area': 'pairing', 'behavior': 'Cuenta atrás 150 s, polling 2 s, retry manual y pantalla de fallo', 'evidence': ['E017', 'E018']},
            {'area': 'preview', 'behavior': '90 s por defecto; reconexión P2P y hasta tres reanudaciones', 'evidence': ['E024', 'E026']},
            {'area': 'unlock', 'behavior': 'Timeout 15 s; éxito/fallo/timeout/contraseña', 'evidence': ['E025']},
            {'area': 'push call', 'behavior': 'Auto-rechazo a 30 s y evento 20 cierra si se atiende en otro terminal', 'evidence': ['E056', 'E059']},
            {'area': 'alarms', 'behavior': 'Errores 502/XML/código se mapean a fallo de búsqueda; refresh manual', 'evidence': ['E052']},
        ],
        'cryptography': {
            'device_password': 'SHA-256 hexadecimal si la capability lo exige; cadenas >=64 se conservan',
            'push_signature': 'HmacSHA1(clientId-timestamp, clientId)',
            'device_digest': 'MD5 con nc/cnonce fijos en el código',
            'preferences': 'AES/ECB/PKCS7Padding, clave AES-128 generada localmente y envuelta con RSA AndroidKeyStore',
            'database': 'SQLCipher con la misma utilidad de clave; PRAGMA cipher_migrate',
            'tls': 'BKS cliente/servidor en un camino; TrustManager vacío y AllowAllHostnameVerifier en otros',
            'evidence': ['E028', 'E030', 'E031', 'E045', 'E046', 'E047', 'E048', 'E049'],
        },
        'security_findings': [
            {'id': 'SEC-01', 'severity': 'high', 'finding': 'Clientes HTTP comunes desactivan validación de hostname y certificado; no hay pinning de app', 'evidence': ['E030', 'E049']},
            {'id': 'SEC-02', 'severity': 'high', 'finding': 'Logging BODY activo en release y DEBUG_HTTP/DEV_MODE=true', 'evidence': ['E002', 'E027']},
            {'id': 'SEC-03', 'severity': 'high', 'finding': 'Logs incluyen usuario, contraseña, accountId, token FCM, share links, claves y datos WiFi', 'evidence': ['E003', 'E008', 'E016', 'E021', 'E056', 'E057']},
            {'id': 'SEC-04', 'severity': 'high', 'finding': 'client.txt contiene una clave privada PEM y los BKS usan contraseña embebida; no se copiaron al repo', 'evidence': ['E028', 'E072']},
            {'id': 'SEC-05', 'severity': 'medium', 'finding': 'Fallback AP usa admin/admin y el CGI puede formar URLs HTTPS con credenciales userinfo', 'evidence': ['E014']},
            {'id': 'SEC-06', 'severity': 'medium', 'finding': 'AES-ECB para preferencias y Digest MD5 con nc/cnonce constantes', 'evidence': ['E031', 'E046', 'E047']},
            {'id': 'SEC-07', 'severity': 'medium', 'finding': 'URLs quii contienen contraseña del dispositivo; se pasan al reproductor nativo', 'evidence': ['E011']},
            {'id': 'SEC-08', 'severity': 'medium', 'finding': 'Hay rutas CGI legacy HTTP y una URL de privacidad HTTP, sin Network Security Config ni usesCleartextTraffic explícito; no se prueba conexión en runtime', 'evidence': ['E064', 'E074']},
            {'id': 'SEC-09', 'severity': 'info', 'finding': 'Existe Utils.isRooted, pero no se encontró llamada que imponga el bloqueo root', 'evidence': ['E071']},
            {'id': 'SEC-10', 'severity': 'medium', 'finding': 'FileProvider no exportado permite URI grants y declara root-path/external-path amplio; exposición real depende de las URIs generadas', 'evidence': ['E075']},
        ],
        'obfuscation': {
            'status': 'partial_identifier_and_resource_obfuscation',
            'details': ['Paquetes de una letra y clases renombradas junto a nombres descriptivos com.quvii.ubell', 'Recursos APK acortados que JADX reasigna a nombres originales', 'No hay mapping de R8/ProGuard en el APK', 'No se observa cifrado general de DEX'],
            'evidence_files': ['analysis/apk/evidence/decompilation-limits.txt'],
        },
        'libraries': [
            {'name': 'Android Gradle Plugin', 'version': '8.6.0'},
            {'name': 'ViewBinding', 'version': '8.6.0'},
            {'name': 'AppCompat', 'version': '1.2.0'},
            {'name': 'Material Components', 'version': '1.4.0'},
            {'name': 'AndroidX Biometric', 'version': '1.1.0'},
            {'name': 'Kotlin coroutines', 'version': '1.6.1'},
            {'name': 'RxJava 2', 'version': '2.1.3'},
            {'name': 'OkHttp', 'version': '3.9.0'},
            {'name': 'Retrofit', 'version': 'no determinada estáticamente'},
            {'name': 'SQLCipher for Android', 'version': '4.5.4'},
            {'name': 'Firebase cloud messaging', 'version': '17.0.1'},
            {'name': 'Firebase components', 'version': '17.1.0'},
            {'name': 'Google Play services basement', 'version': '18.1.0'},
            {'name': 'Huawei ScanKit', 'version': 'scanplus:2.1.0.300'},
            {'name': 'Huawei AGConnect Core', 'version': '1.4.0.300'},
            {'name': 'Qualvision Qv SDK', 'version': 'P2P log declara v3.2; no equivale a versión global del SDK'},
        ],
        'native': {
            'abis': ['arm64-v8a', 'armeabi-v7a'],
            'libraries': ['libWebrtcAudio.so', 'libavcodec.so', 'libavfilter.so', 'libavformat.so', 'libavutil.so', 'libeapilrender.so', 'liblive_player.so', 'libmsoptimize.so (solo arm64)', 'libqv-p2p-v2.so', 'libscannative.so', 'libsqlcipher.so', 'libswresample.so', 'libswscale.so', 'libvoiceRecog.so'],
            'hashes_evidence': 'analysis/apk/evidence/native-hashes.txt',
        },
        'morluto_rea_comparison': {
            'artifacts_available_in_this_leaf': True,
            'literal_identifiers_found': False,
            'command_result': 'El APK no contiene identificadores morluto/rea; analysis/rea aporta referencias externas verificadas por su hoja',
            'secondary_sources': [
                {'path': 'docs/rea-analysis.md', 'role': 'REA, duhow y blog cmos486, no evidencia directa del APK', 'verification': 'analysis/rea/verification.txt: REA_ANALYSIS_VERIFIED, exit 0'},
            ],
            'confirmed_comparisons': [
                {'area': 'REA', 'result': 'REA es una herramienta genérica de ingeniería inversa, no un cliente Wibox ni una fuente de endpoints'},
                {'area': 'P2P', 'result': 'Las notas duhow describen NTS/P2P y el APK contiene P2P nativo, pero no se prueba compatibilidad de protocolo'},
                {'area': 'MQTT', 'result': 'APK: shadow con OAuth, QoS 1 y endpoints dinámicos. duhow: MQTT local plano 1883 con topics propios. No se deben intercambiar'},
                {'area': 'Media', 'result': 'APK: quii/P2P nativo. Blog cmos486: WebRTC/RTSP custom. No es evidencia de stock'},
                {'area': 'Acciones', 'result': 'Unlock, F1, guardia y llamadas comparten dominio, no necesariamente transporte ni payloads'},
                {'area': 'OEM', 'result': 'G0021 aparece en APK y firmwares duhow B007/B013, sin demostrar compatibilidad cloud universal'},
            ],
            'contrast_checklist': ['Signer CN=Qualvision y hash del certificado', 'Paquete com.quvii.ubell frente a com.fermax.wibox', 'OEM G0021, APP_ID 2021, hosts fermax/matainde.qvcloud.net', 'Conjunto libqv-p2p-v2/live_player/WebrtcAudio', 'Trust-all, BKS y logging BODY', 'Flujo noLogin/freeRegister y credenciales dinámicas', 'Paleta #78c9e7 y estructura XML/MVP'],
            'warning': 'REA no es un cliente Wibox. La compatibilidad NTS/cloud y los payloads no se prueba por analogía',
        },
        'evidence_registry': registry,
        'evidence_files': [
            'analysis/apk/evidence/AndroidManifest.xml',
            'analysis/apk/evidence/apk-integrity.txt',
            'analysis/apk/evidence/toolchain.txt',
            'analysis/apk/evidence/library-versions.txt',
            'analysis/apk/evidence/native-hashes.txt',
            'analysis/apk/evidence/decompilation-limits.txt',
            'analysis/apk/evidence/jadx-decompile.log',
            'analysis/apk/evidence/code-evidence.md',
            'analysis/apk/evidence/string-stats.json',
            'analysis/apk/evidence/asset-hashes.txt',
            'analysis/apk/evidence/security-search.txt',
            'analysis/apk/evidence/provider_paths.xml',
            'analysis/apk/evidence/permission_file_paths.xml',
        ],
    }
    return data


def state(identifier, description):
    return {'id': identifier, 'description': description}


def screen(identifier, class_name, layout, title_en, title_es, states, navigation, kind='activity', reachability='linked', note=''):
    return {
        'id': identifier,
        'kind': kind,
        'class': class_name,
        'title': {'en': title_en, 'es': title_es},
        'layout': f'analysis/apk/resources/layout/{layout}' if layout else None,
        'states': states,
        'navigation': navigation,
        'reachability': reachability,
        'note': note,
        'evidence': {'code': f'analysis/apk/evidence/ui-code-evidence.md#{identifier}', 'layout': f'analysis/apk/resources/layout/{layout}' if layout else None},
    }


def build_ui_inventory():
    screens = [
        screen('loading', 'com.quvii.ubell.main.view.LoadingActivity', 'activity_loading.xml', 'WI-BOX', 'WI-BOX', [state('splash', 'Fondo start a pantalla completa'), state('storage_permission', 'Diálogo de almacenamiento y ajustes'), state('phone_permission', 'Resultado READ_PHONE_STATE no bloquea el arranque'), state('startup_wait', 'Espera de login o timeout de 10 s'), state('push_redirect', 'Redirige evento 19 a llamada o preview')], ['main_tab', 'push_call', 'preview'], reachability='entry'),
        screen('cache', 'com.quvii.ubell.push.view.CacheActivity', None, 'Push relay', 'Relay push', [state('relay', 'Copia extras a LoadingActivity'), state('finish', 'Finaliza sin UI propia')], ['loading'], note='Actividad sin layout, excluida de recientes'),
        screen('main_tab', 'com.quvii.ubell.main.view.MainTabActivity', 'activity_main_tab.xml', 'Main tabs', 'Pestañas principales', [state('devices_selected', 'Pestaña Dispositivo activa por defecto'), state('events_selected', 'Pestaña Eventos'), state('gallery_selected', 'Pestaña Galería'), state('settings_selected', 'Pestaña Ajustes'), state('terms', 'Diálogo de términos no cancelable'), state('android_hint', 'Aviso Android 10 con no mostrar más'), state('edit_mode', 'Barra inferior oculta en edición')], ['tab_devices', 'tab_events', 'tab_gallery', 'tab_settings'], reachability='root'),
        screen('add_type', 'com.quvii.ubell.device.add.view.DANewTypeChooseActivity', 'activity_da_type_choose.xml', 'Add Device', 'Añadir Dispositivo', [state('new_device', 'Opción nuevo dispositivo'), state('online_device', 'Opción añadir por red')], ['product_intro', 'online_type']),
        screen('preview', 'com.quvii.ubell.video.view.PreviewActivity', 'activity_preview.xml', 'Preview', 'Autoencendido', [state('empty', 'Hueco para añadir dispositivo'), state('connecting', 'Conectando o buffering'), state('playing', 'Reproduciendo con controles'), state('stopped', 'Detenido'), state('refresh', 'Error y botón reintentar'), state('password_error', 'Credencial rechazada'), state('offline', 'Dispositivo o canal offline'), state('one_window', 'Un panel'), state('four_windows', 'Cuatro paneles'), state('portrait', 'Controles verticales'), state('landscape', 'Controles horizontales'), state('listening', 'Audio entrante activo'), state('talking', 'Micrófono activo'), state('recording', 'Grabación y tiempo visible'), state('snapshot', 'Confirmación de captura'), state('unlock_progress', 'Abriendo'), state('unlock_success', 'Apertura correcta'), state('unlock_failure', 'Fallo o timeout de apertura'), state('f1', 'Estados F1 enviando/éxito/fallo'), state('timeout', 'Fin del tiempo de preview'), state('no_permission', 'Función no permitida')], ['config', 'password_modify']),
        screen('online_type', 'com.quvii.ubell.device.add.view.DAOnlineTypeChooseActivity', 'activity_da_online_type_choose.xml', 'Add Online', 'Añadir por Red', [state('scan_online', 'Buscar dispositivos online'), state('manual', 'Alta manual')], ['online_devices', 'device_bind']),
        screen('product_choose', 'com.quvii.ubell.device.add.view.DANewProduceChooseActivity', 'activity_da_new_produce_choose.xml', 'Setup Wizard', 'Setup Wizard', [state('doorbell', 'Wi-Fi Doorbell tipo 7410'), state('monitor', 'Wi-Fi Monitor tipo 9440')], ['product_intro'], reachability='legacy_no_inbound', note='No se encontró referencia de entrada en el código actual'),
        screen('product_intro', 'com.quvii.ubell.device.add.view.DANewProduceIntroduceActivity', 'activity_da_new_produce_introduce.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('reset_instructions', 'Instrucciones e imagen de reset'), state('location_permission', 'Localización desactivada o denegada'), state('camera_permission', 'Solicitud de cámara'), state('scan_error', 'QR inválido'), state('wifi_switch_error', 'Paso manual al AP')], ['scan', 'router', 'handle_wifi']),
        screen('handle_wifi', 'com.quvii.ubell.device.add.view.DANewHandleWifiActivity', 'activity_da_new_handle_wifi.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('instructions', 'SSID y contraseña visibles con copiar'), state('copied', 'Confirmación de copiado'), state('ap_mismatch', 'Snackbar para abrir ajustes WiFi'), state('ap_connected', 'Continúa a router o progreso')], ['router', 'wifi_progress']),
        screen('wifi_list_legacy', 'com.quvii.ubell.device.manage.view.DMWifiListActivity', 'activity_dm_wifi_list.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('loading', 'Refresco de redes'), state('list', 'Lista de SSID'), state('error', 'Aviso sin lista'), state('password', 'Diálogo de contraseña'), state('connect_failure', 'Error de conexión y ajustes'), state('success', 'Finaliza al conectar')], ['wifi_set_legacy'], reachability='legacy_no_inbound', note='No se encontró referencia de entrada en el código actual'),
        screen('wifi_set_legacy', 'com.quvii.ubell.device.manage.view.DMWifiSetActivity', 'activity_dm_wifi_set.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('password_hidden', 'Contraseña oculta'), state('password_visible', 'Contraseña visible'), state('countdown', 'Espera con contador'), state('connect_error', 'Diálogo para abrir WiFi'), state('modify_success', 'Vuelve a ajustes'), state('set_failure', 'Error y posible reinicio de alta')], ['config', 'product_intro'], reachability='legacy_no_inbound', note='Solo enlazada desde wifi_list_legacy, que tampoco tiene entrada'),
        screen('device_bind', 'com.quvii.ubell.device.add.view.DADeviceBindActivity', 'activity_da_device_bind.xml', 'Add Device', 'Añadir Dispositivo', [state('qr_locked', 'UID bloqueado por QR'), state('manual', 'UID editable y escáner'), state('password_hidden', 'Contraseña oculta'), state('password_visible', 'Contraseña visible'), state('share_link', 'Enlace compartido desactiva campos incompatibles'), state('validation_error', 'Nombre/UID duplicado o vacío'), state('binding', 'Progreso no cancelable'), state('success', 'Configuración correcta'), state('invitation_error', 'Invitación inválida o usada')], ['main_tab', 'scan']),
        screen('online_devices', 'com.quvii.ubell.device.add.view.DAOnlineDeviceListActivity', 'activity_da_online_device_list.xml', 'Add Online', 'Añadir por Red', [state('refreshing', 'Búsqueda LAN'), state('results', 'Lista de dispositivos'), state('empty', 'Mensaje no device found'), state('error', 'Finaliza refresco sin resultados')], ['device_bind']),
        screen('scan', 'com.quvii.ubell.device.add.view.DACaptureActivity', 'activity_da_capture.xml', 'Scan QR-Code', 'Escanee el código QR', [state('scanning', 'Visor y marco QR'), state('pick_image', 'Escaneo desde galería con progreso'), state('invalid', 'Diálogo QR inválido y reintento'), state('result', 'Devuelve resultado al llamador'), state('new_device', 'Continúa alta nueva'), state('share', 'Abre invitación')], ['product_intro', 'device_bind', 'share_bind']),
        screen('config', 'com.quvii.ubell.device.manage.view.DMConfigActivity', 'activity_dm_config.xml', 'Settings', 'Ajustes', [state('owner', 'Todas las opciones del propietario'), state('shared', 'Oculta contraseña, WiFi, alarmas, usuarios y firmware'), state('loading', 'Consulta de info y alarmas'), state('alarm_on', 'Llamada/movimiento activado'), state('alarm_off', 'Llamada/movimiento desactivado'), state('upgrade_available', 'Icono de nueva versión'), state('delete_confirm', 'Confirmación de borrado'), state('delete_error', 'Error de borrado'), state('bind_required', 'Diálogo de contraseña')], ['device_info', 'password_modify', 'unlock_setting', 'router', 'upgrade', 'share_manage', 'main_tab']),
        screen('password_modify', 'com.quvii.ubell.device.manage.view.DMPasswordModifyActivity', 'activity_dm_password_modify.xml', 'Modify password', 'Modificar contraseña', [state('device_mode', 'Cambia contraseña de dispositivo'), state('admin_mode', 'Cambia admin y desbloqueo'), state('unlock_mode', 'Solo contraseña de apertura'), state('init_mode', 'Inicialización sin campo antiguo'), state('mismatch', 'Etiquetas en rojo'), state('empty', 'Error de campos vacíos'), state('success', 'Contraseña modificada')], ['config']),
        screen('unlock_setting', 'com.quvii.ubell.device.manage.view.DMUnlockSettingActivity', 'activity_dm_unlock_setting.xml', 'Unlock setting', 'Configuración de desbloqueo', [state('quick_on', 'Apertura rápida activa'), state('quick_off', 'Apertura rápida inactiva'), state('bio_on', 'Confirmación biométrica activa'), state('bio_off', 'Confirmación biométrica inactiva'), state('bio_unavailable', 'Fila biométrica oculta'), state('warning', 'Diálogo explicativo con no mostrar más'), state('saved', 'Guarda y finaliza')], ['config']),
        screen('device_info', 'com.quvii.ubell.device.manage.view.DMDeviceInfoEditActivity', 'activity_dm_device_info_edit.xml', 'Device Info', 'Información del dispositivo', [state('read_only', 'Nombre y UID no editables'), state('editing', 'Nombre editable y botón guardar'), state('validation_error', 'Nombre vacío o duplicado'), state('success', 'Guardado local/remoto'), state('failure', 'Error de modificación')], ['config']),
        screen('about', 'com.quvii.ubell.me.view.MeAboutActivity', 'activity_me_about.xml', 'About', 'Info', [state('version', 'Logo, WI-BOX y versión'), state('privacy', 'Diálogo con HTML local'), state('debug_trigger', 'Secuencia oculta abre TestInfo')], ['debug']),
        screen('image_pager', 'com.quvii.ubell.fileManage.view.FMImagePagerActivity', 'activity_fm_image_pager.xml', 'Gallery pager', 'Visor de galería', [state('white', 'Fondo blanco con título y acciones'), state('black', 'Fondo negro sin título ni acciones'), state('page', 'Indicador posición/total'), state('share', 'Compartir elemento'), state('delete_confirm', 'Confirmación de borrado'), state('save_result', 'SnackBar de guardado')], ['video_player', 'tab_gallery']),
        screen('video_player', 'com.quvii.ubell.fileManage.view.FMLocalVideoPlayActivity', 'activity_fm_local_video_play.xml', 'Video player', 'Reproductor de vídeo', [state('playing', 'Reproducción y botón pausa'), state('paused', 'Overlay y botón play'), state('stopped', 'Overlay visible'), state('muted', 'Icono mute'), state('unmuted', 'Icono sonido'), state('seeking', 'Pausa temporal y seek'), state('tools_visible', 'Controles visibles'), state('tools_hidden', 'Oculta controles tras 5 s'), state('landscape', 'Vídeo a pantalla completa')], ['image_pager']),
        screen('push_call', 'com.quvii.ubell.push.view.PushCallActivity', 'activity_push_call.xml', 'Call from panel', 'llamada de placa de calle', [state('ringing', 'Nombre, aceptar y rechazar con tono'), state('vibration', 'Vibración opcional'), state('accept', 'Abre preview y registra aceptación'), state('refuse', 'Cierra o envía a segundo plano'), state('auto_refuse', 'Auto-rechazo a 30 s'), state('answered_elsewhere', 'Mensaje y cierre por evento 20')], ['preview', 'main_tab']),
        screen('debug', 'com.quvii.ubell.test.view.TestInfoActivity', 'activity_test_info.xml', 'Debug', 'Debug', [state('debug_toggle', 'Modo debug y aviso de reinicio'), state('forwarding', 'Fuerza forwarding'), state('connection_type', 'Muestra tipo de conexión'), state('advanced', 'Opciones tras código'), state('service_switch', 'Cambio de servidor y borrado local'), state('send_log', 'Compartir log'), state('token', 'Token disponible o null')], ['loading'], reachability='hidden_gesture', note='Acceso por secuencia en About y código para opciones avanzadas'),
        screen('upgrade', 'com.quvii.ubell.device.manage.view.DMUpgradeActivity', 'activity_dm_upgrade.xml', 'Firmware version', 'Versión Firmware', [state('query', 'Consulta versión actual y disponible'), state('up_to_date', 'Firmware actualizado'), state('available', 'Diálogo de confirmación'), state('progress', 'Progreso no cancelable'), state('success', 'Diálogo de éxito y reinicio posterior'), state('failure', 'Diálogo de fallo')], ['config']),
        screen('guide', 'com.quvii.ubell.main.view.StartGuideActivity', 'activity_start_guide.xml', 'Start guide', 'Guía de inicio', [state('page_locks', 'Guía More locks'), state('page_cameras', 'Guía More Cameras'), state('page_playback', 'Guía Quick Playback'), state('start', 'Botón Start Now')], ['main_tab'], reachability='legacy_no_inbound', note='Declarada pero LoadingPresenter siempre abre MainTabActivity'),
        screen('add_method_legacy', 'com.quvii.ubell.device.add.view.DAAddTypeActivity', 'activity_da_add_type.xml', 'Add Device', 'Añadir Dispositivo', [state('ap', 'AP add'), state('voice', 'Sound waves to add'), state('switch_success', 'Continúa al router'), state('switch_failure', 'Paso manual WiFi')], ['router', 'handle_wifi'], reachability='legacy_no_inbound', note='No se encontró referencia de entrada en el código actual'),
        screen('router', 'com.quvii.ubell.device.add.view.DARouterInfoActivity', 'activity_da_router_info.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('loading', 'Refresco de redes'), state('selected', 'SSID elegido con nivel wifi_1 a wifi_4'), state('password_hidden', 'Contraseña oculta'), state('password_visible', 'Contraseña visible'), state('save_password', 'Guardar contraseña activo por defecto'), state('validation', 'Contraseña corta'), state('new_mode', 'Continúa AP o audio'), state('modify_mode', 'Textos cambiar y notas adicionales')], ['wifi_progress', 'voice', 'config']),
        screen('voice', 'com.quvii.ubell.device.add.view.DAVoiceSetActivity', 'activity_da_voice_set.xml', 'Sound waves to add', 'Sound waves to add', [state('sending', 'Envío acústico'), state('waiting', 'Espera de dispositivo online'), state('volume_low', 'Aviso de volumen insuficiente'), state('failure', 'Fallo y botón retry'), state('success', 'Continúa bind o main')], ['device_bind', 'main_tab']),
        screen('wifi_progress', 'com.quvii.ubell.device.add.view.DAWifiSetActivity', 'activity_da_wifi_set.xml', 'Wi-Fi Setup', 'Ajustes Wi-Fi', [state('connecting', 'Conecta dispositivo'), state('sending', 'Envía datos'), state('waiting', 'Espera online con cuenta atrás'), state('network_error', 'Aviso de red y restauración manual'), state('retry', 'Botón retry'), state('failure', 'Pantalla de pairing error'), state('success', 'Continúa bind o main')], ['pairing_fail', 'device_bind', 'main_tab', 'handle_wifi']),
        screen('share_device', 'com.quvii.ubell.share.view.ShareDeviceActivity', 'activity_share_device.xml', 'New user', 'Nuevo usuario', [state('qr', 'QR de 120 dp'), state('link', 'Enlace textual'), state('expiration', 'Disponible antes de fecha'), state('copied', 'Enlace copiado'), state('save_result', 'Resultado al guardar QR'), state('share_sheet', 'Chooser Android')], ['share_manage']),
        screen('pairing_fail', 'com.quvii.ubell.device.add.view.DADeviceConfigFailActivity', 'activity_da_device_config_fail.xml', 'Error', 'Error', [state('error', 'Explicación de pairing fallido'), state('main_only', 'Solo botón siguiente, back físico ignorado')], ['main_tab']),
        screen('share_manage', 'com.quvii.ubell.share.view.ShareManageActivity', 'activity_share_manage.xml', 'Users', 'Usuarios', [state('loading', 'Refresco de invitaciones'), state('empty', 'No Users'), state('list', 'Usuarios con permisos'), state('create', 'Nueva invitación con permisos por defecto'), state('edit', 'Editar nombre/permisos'), state('delete', 'Confirmación de borrado'), state('limit', 'Botón añadir oculto a 100'), state('error', 'No autorizado o fallo')], ['share_device', 'config']),
        screen('share_bind', 'com.quvii.ubell.share.view.ShareBindActivity', 'activity_share_bind.xml', 'Add Device', 'Añadir Dispositivo', [state('form', 'UID/código bloqueados y nombre editable'), state('validation', 'Nombre vacío o duplicado'), state('accept', 'Aceptación con progreso'), state('success', 'Vuelve a main'), state('invalid', 'Invitación inválida o usada')], ['main_tab']),
        screen('tab_devices', 'com.quvii.ubell.main.view.MainDeviceListFragment', 'fragment_device_list.xml', 'Device', 'Dispositivo', [state('empty', 'Botón central añadir'), state('list', 'Tarjetas con portada y acciones'), state('refresh', 'SwipeRefresh y estado online'), state('offline', 'Estado offline'), state('password', 'Diálogo de apertura/F1'), state('biometric', 'Verificación biométrica'), state('action_result', 'Toast éxito/fallo')], ['add_type', 'preview', 'config'], kind='fragment', reachability='main_tab'),
        screen('tab_events', 'com.quvii.ubell.main.view.MainMessageListFragment', 'fragment_message_list.xml', 'Events', 'Eventos', [state('loading', 'Carga inicial y refresh'), state('empty', 'Texto de ausencia de eventos'), state('list', 'Una sola lista All ACTIVITY'), state('filters_hidden', 'RINGS/MOTION ocultos'), state('warning', 'Aviso sobre fotos y firmware'), state('edit', 'Selección y barra inferior'), state('delete', 'Confirmación de borrado'), state('image', 'Diálogo de foto')], ['tab_event_list'], kind='fragment', reachability='main_tab'),
        screen('tab_event_list', 'com.quvii.ubell.main.view.MainMessageTypeFragment', 'fragment_main_message_type.xml', 'Activity list', 'Lista de actividad', [state('empty_all', 'Mensaje de llamadas del hogar'), state('empty_call', 'Mensaje vacío de llamadas'), state('empty_motion', 'Mensaje e icono de movimiento'), state('refresh', 'Tirar para refrescar'), state('load_more', 'Carga más a partir de 15'), state('edit', 'Selección múltiple')], ['tab_events'], kind='fragment', reachability='nested_fragment'),
        screen('tab_gallery', 'com.quvii.ubell.main.view.MainFileFragment', 'fragment_flie.xml', 'Gallery', 'Galería', [state('empty', 'Texto sin fotos ni vídeos'), state('grid', 'Tres columnas agrupadas por fecha'), state('edit', 'Barra de selección y pestañas oculta'), state('selected', 'Contador en título'), state('select_all', 'Seleccionar todo'), state('share', 'Compartir selección'), state('save', 'Guardar selección y resultado'), state('delete', 'Confirmación de borrado')], ['image_pager'], kind='fragment', reachability='main_tab'),
        screen('tab_settings', 'com.quvii.ubell.main.view.MainMeFragment', 'fragment_me.xml', 'Settings', 'Ajustes', [state('sound_on', 'Sonido activado'), state('sound_off', 'Sonido desactivado'), state('vibration_on', 'Vibración activada'), state('vibration_off', 'Vibración desactivada'), state('about', 'Abre información'), state('exit_confirm', 'Diálogo de salida'), state('hidden_options', 'Auto rotate, canales y preview time ocultos')], ['about'], kind='fragment', reachability='main_tab'),
    ]
    resource_manifest = load_json(APK_DIR / 'resources/manifest.json')
    by_path = {entry['path']: entry for entry in resource_manifest['files']}
    for item in screens:
        layout = item['layout']
        if not layout:
            item['widgets'] = []
            item['resources'] = []
            continue
        relative = layout.removeprefix('analysis/apk/resources/')
        root = ET.parse(APK_DIR / 'resources' / relative).getroot()
        widgets = set()
        refs = set()
        for node in root.iter():
            for key, value in node.attrib.items():
                local = key.split('}')[-1]
                if local == 'id' and value.startswith('@+id/'):
                    widgets.add(value[5:])
                if isinstance(value, str) and re.match(r'^@(color|dimen|drawable|string|style)/', value):
                    refs.add(value[1:])
        item['widgets'] = sorted(widgets)
        item['resources'] = sorted(refs)
        item['resource_evidence'] = by_path.get(relative)
    colors = {}
    color_root = ET.parse(APK_DIR / 'resources/values/app-colors.xml').getroot()
    for node in color_root.findall('color'):
        colors[node.get('name')] = node.text
    dimensions = {}
    dimen_root = ET.parse(APK_DIR / 'resources/values/app-dimens.xml').getroot()
    for node in dimen_root.findall('dimen'):
        dimensions[node.get('name')] = node.text
    strings_en = {node.get('name'): node.text or '' for node in ET.parse(APK_DIR / 'resources/values/strings-en.xml').getroot().findall('string')}
    strings_es = {node.get('name'): node.text or '' for node in ET.parse(APK_DIR / 'resources/values/strings-es.xml').getroot().findall('string')}
    densities = {}
    for entry in resource_manifest['files']:
        parts = entry['path'].split('/')
        if parts[0].startswith('drawable-') and 'width' in entry:
            density = parts[0]
            densities.setdefault(density, {'files': 0, 'examples': []})
            densities[density]['files'] += 1
            if len(densities[density]['examples']) < 8:
                densities[density]['examples'].append({'path': entry['path'], 'width': entry['width'], 'height': entry['height']})
    return {
        'schema_version': 1,
        'analysis_date': '2026-10-31',
        'source': {'package': 'com.fermax.wibox', 'version_name': 'v2.2', 'version_code': 18, 'sha256': EXPECTED_HASH, 'mode': 'static_resources_only'},
        'captures': {'real_runtime_screenshots': False, 'statement': 'No hay capturas reales de ejecución. start.png y guide_display*.png son recursos ilustrativos, no se presentan como capturas.'},
        'technology': {'views_xml': True, 'compose': False, 'view_binding': True, 'app_compat_theme': 'Theme.AppCompat.Light.NoActionBar', 'portrait_activities': True, 'preview_rotation': True},
        'screens': screens,
        'screen_count': len(screens),
        'navigation_edges': [{'from': item['id'], 'to': target} for item in screens for target in item['navigation']],
        'visual_system': {
            'colors': colors,
            'palette_notes': {'primary': '#78c9e7 usado en título y barra inferior', 'background': '#f4f4f4 como main_ui_color_white', 'text': '#4d4d4d como textColor; blanco en cabecera', 'selected_event': '#00a3dd', 'error': '#e73e04', 'preview': 'negro'},
            'typography': {'default_family': 'sans-serif del sistema; FontHelper.Init(false) desactiva alger.ttf', 'bundled_font': 'fonts/alger.ttf presente pero no aplicado por defecto', 'styles': ['CustomButton 18sp normal', 'CustomTextView 15sp', 'Título 20sp', 'Bottom tabs 12sp', 'Guía 30sp bold', 'About app name 25sp', 'Textos auxiliares 10/13/14/15/16/18sp'], 'evidence': ['E060']},
            'dimensions': dimensions,
            'key_dimensions': {'title_height': '60dp', 'main_bottom_height': '55dp', 'button_height': '50dp', 'button_horizontal_margin': '30dp en option_button', 'device_cover': '200dp de alto', 'guide_image': '640x960 px xhdpi', 'qr_code': '120x120 dp', 'tab_icons': '60x60 px xhdpi'},
            'iconography': 'PNGs propios y selectores XML para normal/pressed/selected/on/off; no Lucide ni Material Symbols en el APK original',
            'themes': {'light': 'AppTheme Light.NoActionBar con colorPrimary/primaryDark #78c9e7', 'dark': 'values-night/colors.xml está vacío; no hay tema oscuro propio. La pantalla de vídeo y el visor negro son estados locales, no DayNight', 'night_resources_evidence': 'analysis/apk/resources/values-night/colors.xml'},
        },
        'localization': {
            'default_english_app_strings': 282,
            'spanish_app_strings': 281,
            'identical_in_spanish': 59,
            'identical_note': 'Texto idéntico en ambos recursos; incluye cadenas sin traducir y marcas/abreviaturas',
            'missing_in_spanish': ['key_is_phone_calling'],
            'english_fallback': 'values-en solo contiene recursos de librerías; las cadenas base en values/strings.xml están en inglés',
            'examples': [
                {'key': 'key_device', 'en': strings_en.get('key_device'), 'es': strings_es.get('key_device')},
                {'key': 'key_device_add', 'en': strings_en.get('key_device_add'), 'es': strings_es.get('key_device_add')},
                {'key': 'key_unlock', 'en': strings_en.get('key_unlock'), 'es': strings_es.get('key_unlock')},
                {'key': 'key_album', 'en': strings_en.get('key_album'), 'es': strings_es.get('key_album')},
                {'key': 'key_setting', 'en': strings_en.get('key_setting'), 'es': strings_es.get('key_setting')},
                {'key': 'key_alarm_title_all', 'en': strings_en.get('key_alarm_title_all'), 'es': strings_es.get('key_alarm_title_all'), 'note': 'Sin traducir'},
            ],
            'evidence': 'analysis/apk/evidence/string-stats.json',
        },
        'resource_densities': densities,
        'resource_manifest': 'analysis/apk/resources/manifest.json',
        'evidence': {'ui_code': 'analysis/apk/evidence/ui-code-evidence.md', 'layouts': 'analysis/apk/resources/layout', 'resource_hashes': 'analysis/apk/resources/manifest.json'},
    }


def main():
    inventory = build_inventory()
    (APK_DIR / 'inventory.json').write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + '\n')
    ui = build_ui_inventory()
    (APK_DIR / 'ui-inventory.json').write_text(json.dumps(ui, ensure_ascii=False, indent=2) + '\n')
    print(f"inventory_components={inventory['component_summary']['activities']}/{inventory['component_summary']['services']}/{inventory['component_summary']['receivers']}/{inventory['component_summary']['providers']}")
    print(f"evidence_blocks={len(inventory['evidence_registry'])}")
    print(f"ui_screens={ui['screen_count']} navigation_edges={len(ui['navigation_edges'])}")


if __name__ == '__main__':
    main()
