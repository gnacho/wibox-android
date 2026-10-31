# Análisis estático de Wibox Fermax v2.2

Fecha del análisis: 2026-10-31. Hoja 1.1.2. Alcance exclusivamente estático y local.

## 1. Resumen ejecutivo

Wibox v2.2 es una app Android nativa basada en Views XML, AppCompat, fragments, ViewBinding y una arquitectura MVP. No usa Jetpack Compose. El paquete público es `com.fermax.wibox`, pero la implementación principal conserva el espacio `com.quvii.ubell` y el SDK Qualvision. La app combina:

- Alta de dispositivos por QR, AP WiFi, audio, LAN e invitaciones.
- Cuenta creada de forma transparente con `userFreeRegister`, sin pantalla de login visible.
- Credenciales dinámicas sincronizadas con el backend y persistencia local con SQLCipher.
- Vídeo y audio mediante túneles P2P nativos y URLs `quii://` hacia puertos locales mapeados.
- FCM para llamada, llamada atendida y movimiento.
- APIs HTTP/XML/JSON, OAuth, device shadow HTTP y un canal MQTT presente en el SDK.

Hallazgos prioritarios para la réplica y para contrastar con otras variantes:

1. El transporte principal no es SIP ni WebRTC de señalización, sino P2P propio con `libqv-p2p-v2.so`, `liblive_player.so` y `libWebrtcAudio.so` (`E011`, `E021`, `E023`, `E042`).
2. Existe MQTT shadow con OAuth y reconexión, pero `SUPPORT_DEVICE_SHADOW=false` bloquea su inicialización por defecto y las clases `org.eclipse.paho` no aparecen en la salida JADX. Por tanto, no se afirma que esté activo ni su comportamiento dinámico (`E037` a `E041`, `E063`, `E073` y `analysis/apk/evidence/decompilation-limits.txt`).
3. La app no aplica certificate pinning. Hay clientes con `AllowAllHostnameVerifier` y un `X509TrustManager` que no valida nada (`E030`, `E049`).
4. En una build `release` se activan `DEBUG_HTTP` y `DEV_MODE`, con logging BODY. Hay logs que contienen credenciales, tokens y enlaces de invitación (`E002`, `E003`, `E027`, `E056`, `E057`).
5. La base de datos usa SQLCipher 4.5.4 y las preferencias sensibles se cifran, pero la configuración de cifrado es AES/ECB y existe una clave privada PEM en assets. Los secretos no se han copiado al repo (`E045` a `E047`, `E072`).
6. No hay capturas reales de ejecución en esta hoja. Las imágenes `start.png` y `guide_display*.png` son recursos, no capturas de la app funcionando.

## 2. Artefacto, integridad y herramientas

### 2.1 Identidad verificada

| Campo | Valor verificado | Evidencia |
|---|---|---|
| Ruta original | `/home/nacho/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk` | Comando `stat`, `analysis/apk/evidence/toolchain.txt` |
| Tamaño | 45.816.991 bytes | `stat` |
| SHA-256 | `08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce` | `sha256sum`, `analysis/apk/evidence/apk-integrity.txt:1-2` |
| Integridad ZIP | Sin errores en todos los entries | `unzip -t`, `analysis/apk/evidence/apk-integrity.txt:3-6` |
| Paquete | `com.fermax.wibox` | `analysis/apk/evidence/AndroidManifest.xml:7` |
| versionName / versionCode | `v2.2` / `18` | `analysis/apk/evidence/AndroidManifest.xml:3-4` |
| minSdk / targetSdk / compileSdk | 21 / 35 / 35 | `analysis/apk/evidence/AndroidManifest.xml:5,10-12` |
| BuildConfig | `release`, `DEBUG=false`, `DEBUG_MODE=false` | `com/quvii/ubell/BuildConfig.java:5-11` |
| Build timestamp | 1744710181402 ms, `2025-04-15T09:43:01Z` | `analysis/apk/evidence/toolchain.txt:11-12` |
| Firma JAR | `jar verified` | `jarsigner -verify`, `analysis/apk/evidence/apk-integrity.txt:33-35` |
| Certificado | `CN=Qualvision`, autofirmado, RSA 2048, SHA256withRSA | `analysis/apk/evidence/apk-integrity.txt:10-20` |
| SHA-256 del certificado | `5D:DA:34:C8:E6:DC:DD:45:BA:F5:8D:AB:64:5B:F5:96:48:B9:4C:9B:B4:36:02:9C:72:78:D0:C9:10:40:47:81` | `analysis/apk/evidence/apk-integrity.txt:15-17` |

`jarsigner` también informa de certificado autofirmado, cadena no validada, ausencia de timestamp e inconsistencias de lectura propias del empaquetado. Son advertencias registradas, no se han ocultado. La verificación criptográfica del APK se repite en `verify_analysis.py`.

### 2.2 Herramientas

| Herramienta | Versión/origen | Hash o registro |
|---|---|---|
| JADX | 1.5.6, release oficial `skylot/jadx` | ZIP `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974` |
| OpenJDK | 17.0.20.1 | `java -version` |
| Python | 3.14.8 | `python3 --version` |
| UnZip | 6.00 | `unzip -v` |
| ImageMagick | 7.1.2-34 Q16-HDRI | `identify -version` |

Comando principal:

```bash
/home/nacho/temp/opencode-work/jadx-1.5.6/bin/jadx \
  --threads-count 4 --show-bad-code \
  --output-dir /home/nacho/temp/opencode-work/wibox-jadx \
  /home/nacho/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk
```

Resultado: exit 3, 6.637 archivos fuente y 1.772 recursos. JADX reportó siete errores. Hay cuatro archivos con bloque explícito `JADX ERROR` y otros métodos marcados como incorrectos. El método `OkHttpUtil.replaceString` quedó sin decompilar, pero las rutas TLS citadas sí están presentes de forma legible. No se afirma cobertura perfecta del bytecode (`analysis/apk/evidence/decompilation-limits.txt`).

## 3. Manifiesto y superficie exportada

La aplicación declara `allowBackup="false"`, `supportsRtl="true"`, `resizeableActivity="false"` y `extractNativeLibs="true"`. No declara `networkSecurityConfig` ni `usesCleartextTraffic`; no se inventa una política personalizada. Al ser targetSdk 35 se aplica el comportamiento por defecto de plataforma, pero los clientes permisivos descritos más abajo debilitan TLS independientemente de cleartext (`analysis/apk/evidence/AndroidManifest.xml:61-71`).

Permisos: 27 entradas, 26 únicas. `USE_FULL_SCREEN_INTENT` está duplicado. Incluye Internet, WiFi, localización fina/gruesa, cámara, micrófono, teléfono, almacenamiento externo legacy, Bluetooth, notificaciones, foreground service, wake lock, biometría y FCM. La lista completa está en `analysis/apk/inventory.json`, sección `permissions`.

Regla aplicada a `exported`: se conserva el valor explícito y se calcula el efectivo. Si falta, se usa `true` solo cuando hay intent-filter, en otro caso `false`.

### 3.1 Activities

| Clase corta | exported efectivo | Notas |
|---|---:|---|
| `com.quvii.ubell.main.view.LoadingActivity` | true | Launcher, singleTask, portrait y deep link `wibox://wibox.com` |
| `com.quvii.ubell.push.view.CacheActivity` | false | Relay sin UI, `taskAffinity=""`, excluida de recientes |
| `com.quvii.ubell.main.view.MainTabActivity` | false | singleTask, portrait |
| `com.quvii.ubell.device.add.view.DANewTypeChooseActivity` | false | Alta nueva o online |
| `com.quvii.ubell.video.view.PreviewActivity` | false | singleTask, portrait inicial, `configChanges` |
| `com.quvii.ubell.device.add.view.DAOnlineTypeChooseActivity` | false | Online o manual |
| `com.quvii.ubell.device.add.view.DANewProduceChooseActivity` | false | Selector de producto |
| `com.quvii.ubell.device.add.view.DANewProduceIntroduceActivity` | false | Reset y QR |
| `com.quvii.ubell.device.add.view.DANewHandleWifiActivity` | false | Paso manual al AP |
| `com.quvii.ubell.device.manage.view.DMWifiListActivity` | false | Lista WiFi legacy |
| `com.quvii.ubell.device.manage.view.DMWifiSetActivity` | false | Configuración WiFi legacy |
| `com.quvii.ubell.device.add.view.DADeviceBindActivity` | false | Formulario de bind, singleTask |
| `com.quvii.ubell.device.add.view.DAOnlineDeviceListActivity` | false | Resultados LAN |
| `com.quvii.ubell.device.add.view.DACaptureActivity` | false | Escáner QR |
| `com.quvii.ubell.device.manage.view.DMConfigActivity` | false | Ajustes, singleTask |
| `com.quvii.ubell.device.manage.view.DMPasswordModifyActivity` | false | Cambio de contraseñas |
| `com.quvii.ubell.device.manage.view.DMUnlockSettingActivity` | false | Quick unlock y biometría |
| `com.quvii.ubell.device.manage.view.DMDeviceInfoEditActivity` | false | Información de dispositivo |
| `com.quvii.ubell.me.view.MeAboutActivity` | false | About y acceso debug |
| `com.quvii.ubell.fileManage.view.FMImagePagerActivity` | false | Visor de imágenes |
| `com.quvii.ubell.fileManage.view.FMLocalVideoPlayActivity` | false | Reproductor local |
| `com.quvii.ubell.push.view.PushCallActivity` | false | Llamada entrante, singleTask |
| `com.quvii.ubell.test.view.TestInfoActivity` | false | Debug |
| `com.quvii.ubell.device.manage.view.DMUpgradeActivity` | false | Firmware |
| `com.quvii.ubell.main.view.StartGuideActivity` | false | Guía declarada |
| `com.quvii.ubell.device.add.view.DAAddTypeActivity` | false | AP o audio |
| `com.quvii.ubell.device.add.view.DARouterInfoActivity` | false | Router y contraseña |
| `com.quvii.ubell.device.add.view.DAVoiceSetActivity` | false | Alta por audio |
| `com.quvii.ubell.device.add.view.DAWifiSetActivity` | false | Progreso de alta |
| `com.quvii.ubell.share.view.ShareDeviceActivity` | false | QR y enlace de invitación |
| `com.quvii.ubell.device.add.view.DADeviceConfigFailActivity` | false | Pairing error |
| `com.quvii.ubell.share.view.ShareManageActivity` | false | Gestión de usuarios |
| `com.quvii.ubell.share.view.ShareBindActivity` | false | Aceptar invitación |
| `com.quvii.qvlib.util.QvPermissionActivity` | false | Permisos |
| `com.google.android.gms.common.api.GoogleApiActivity` | false | GMS |
| `com.yanzhenjie.permission.PermissionActivity` | false | Permisos |
| `com.huawei.hms.hmsscankit.ScanKitActivity` | false | Huawei ScanKit |

Total: 37 activities, 33 propias de Wibox. La referencia completa con launch modes y flags está en `analysis/apk/inventory.json`.

### 3.2 Services, receivers y providers

| Tipo | Clase | exported efectivo | Notas |
|---|---|---:|---|
| service | `com.quvii.ubell.push.fcm.MyFirebaseMessagingService` | false | `MESSAGING_EVENT` |
| service | `com.quvii.ubell.push.PushHelperService` | false | `shortService`, llamada foreground |
| service | `org.eclipse.paho.android.service.MqttService` | false | Declarado; clases Paho no halladas en sources |
| service | `com.google.firebase.messaging.FirebaseMessagingService` | false | Base FCM |
| service | `com.google.firebase.components.ComponentDiscoveryService` | false | Firebase |
| service | `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` | false | Transport |
| service | `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService` | false | Protegido por `BIND_JOB_SERVICE` |
| service | `com.huawei.agconnect.core.ServiceDiscovery` | false | Huawei |
| receiver | `com.quvii.ubell.push.PushHelperReceiver` | false | Rechazo de notificación |
| receiver | `com.google.firebase.iid.FirebaseInstanceIdReceiver` | true | Limitado por permiso `com.google.android.c2dm.permission.SEND` |
| receiver | `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver` | false | Transport |
| provider | `androidx.core.content.FileProvider` | false | `com.fermax.wibox.file_provider`, URI grants |
| provider | `com.google.firebase.provider.FirebaseInitProvider` | false | Inicialización Firebase |
| provider | `com.yanzhenjie.permission.FileProvider` | false | `com.fermax.wibox.file.path.share` |
| provider | `razerdp.basepopup.BasePopupInitializer` | false | Inicializador |
| provider | `com.huawei.hms.mlsdk.common.provider.MLInitializerProvider` | false | Huawei |
| provider | `com.huawei.agconnect.core.provider.AGConnectInitializeProvider` | false | Huawei |

Totales: 8 services, 3 receivers y 6 providers. Solo LoadingActivity y FirebaseInstanceIdReceiver son exportados, el segundo con permiso de firma de Google. No hay receivers de boot propios pese al permiso `RECEIVE_BOOT_COMPLETED`.

## 4. Navegación y tecnología UI

### 4.1 Stack

- XML y Android Views: 74 layouts propios extraídos, sin layouts de librerías en el conjunto seleccionado.
- `AppTheme` deriva de `Theme.AppCompat.Light.NoActionBar`, con `colorPrimary` y `colorPrimaryDark` `#78c9e7` (`values/styles.xml:24-29`, `values/colors.xml:39-41`).
- ViewBinding generado para activities/fragments. MVP con `BasePresenter`, modelos y contratos.
- Cuatro fragments en `MainTabActivity`: dispositivos, eventos, galería y ajustes (`E053`).
- Vídeo con `MyGLSurfaceView`; no Compose. Hay Kotlin y coroutines, pero no APIs Compose ni `setContent`.
- `values-night/colors.xml` está vacío. No hay un tema oscuro propio. El visor negro y el preview negro son estados locales.

### 4.2 Flujo principal

```text
LoadingActivity
  -> MainTabActivity
       -> Dispositivos -> DANewTypeChooseActivity / PreviewActivity / DMConfigActivity
       -> Eventos -> MainMessageTypeFragment -> foto de alarma
       -> Galería -> FMImagePagerActivity -> FMLocalVideoPlayActivity
       -> Ajustes -> MeAboutActivity -> TestInfoActivity (gesto oculto)

DANewTypeChooseActivity
  -> DANewProduceIntroduceActivity -> DACaptureActivity
  -> DAOnlineTypeChooseActivity -> DAOnlineDeviceListActivity / DADeviceBindActivity

Alta nueva:
  QR -> DARouterInfoActivity -> DAWifiSetActivity o DAVoiceSetActivity
     -> DADeviceBindActivity -> MainTabActivity
```

`StartGuideActivity`, `DANewProduceChooseActivity`, `DAAddTypeActivity` y `DMWifiListActivity` no tienen referencias externas de entrada en el código analizado. `DMWifiSetActivity` solo es llamada desde esa lista legacy. Se inventarían relaciones si se dieran por activas, por eso se marcan como `legacy_no_inbound` en el inventario UI. `LoadingPresenter` abre `MainTabActivity` en ambas ramas, nunca `StartGuideActivity` (`com/quvii/ubell/main/presenter/LoadingPresenter.java:55-64`, `E006`).

El deep link declarado abre `LoadingActivity`, pero no se encontró lectura de `Intent.getData()` en esa clase. No se afirma resolución de parámetros del enlace. La invitación sí se procesa al escanear QR o pegar texto con `DeviceHelper.getShareInfo` (`E008`).

El inventario accionable de 38 pantallas y estados está en `docs/ui-inventory.md` y `analysis/apk/ui-inventory.json`.

## 5. Red, endpoints, headers y autenticación

No se ha contactado con estos servicios. Los hosts dinámicos no se pueden resolver estáticamente.

| Área | Endpoint/contrato estático | Autenticación/headers | Evidencia |
|---|---|---|---|
| Bootstrap | `https://fermax.qvcloud.net:443` | OEM `G0021`, APP_ID 2021 | `AppConfig.java:23-36`, `E001`; `SdkManager.java:283-318`, `E005` |
| Upgrade | `https://matainde.qvcloud.net:1443/webs/version/app/version_check.jsp` | GET, query `os/appid/version/clientid`, `Accept-Language` | `AppVersionManager.java:272-313`, `E050` |
| Descubrimiento | URLs regionales devueltas por P2P | `QvLocationManager`, caché regional | `QvLocationManager.java:70-96`; `E034` |
| User/no-login | `/auth/nologin;jus_duplex=up/down`, `/auth/user;jus_duplex=up/down` | Cookies y credenciales de cuenta/dispositivo | `HttpDeviceConst.java:29-44`; `E010` |
| OAuth | `/qvoauthv2/token`, GET | Query `grant_type/client_id/usr/pwd/...`; header `token`, no Bearer | `QvOAuthManager.java:85-113`, `E033`; `OkHttpUtil.java:407-414`, `E029` |
| Alarm | `UserAlarm`, `DeviceAlarm` | Cookie y payload XML | `HttpDeviceConst.java:31,42`; `E051` |
| Push | `/openapi-tdk/client/v2/push/token`, `/client/push/token`, `/pushserver/api/v1/client/register` | Token FCM, IDs, HMAC-SHA1 en v2 | `QvPushHelper.java:64-76`; `E048`, `E057` |
| Shadow HTTP | `/openapi-tdk/device/status`, `/multi`, `/sub`, `/unsub` | Header `token` | `QvShadowConfig.java:11-18`, `E041` |
| MQTT | `ssl://...` para mqtts, `tcp://...` para mqtt | `C_<account>_<client>`, access token como password | `QvMqttManager.java:416-434`, `E040` |
| CGI local | `https://<ip>:<cgiPort>/tdkcgi`; rutas legacy `http://` en unbind y según dispositivo/cgiScheme | Credenciales o Digest MD5 | `DeviceCustomRequestHelp.java:38-51`, `E031`; `RetrofitUtil.java:299-309,433-482`, `E074` |
| Password encryption | `https://<user>:<password>@<ip>:<port>/tdkcgi/passwordencryption` | Credenciales en userinfo | `QvNetDeviceCoreHelper.java:107-122` |
| AP pairing | `192.168.111.1:443` | Fallback `admin/admin`; alternativa `adminapp2` + hash | `DAWifiSetModel.java:51-100`, `E014` |
| Media | `quii://<user>:<password>@127.0.0.1:<port>/...` | Contraseña dinámica y `dataEncodeKey` | `DeviceHelper.java:846-915`, `E011` |
| Firebase | FCM y `tdkvdp.firebaseio.com` | Config embebida; API key no reproducida | `values/strings.xml:60,73-81`; `E056` |
| Privacidad | `http://94.101.81.118/privacy/privacy_policy.html` | Constante; flujo actual usa HTML local | `AppConst.java:69`, `E064`; `MainTabActivity.java:66-76` |
| Conectividad | `https://www.apple.com`, `https://www.google.com` | GET con timeout de 5 s | `QvNetUtil.java:225-240`, `E065` |

Las rutas HTTP se documentan como construcciones de código, no como conexiones comprobadas. La ausencia de `usesCleartextTraffic` puede bloquearlas según plataforma, pero no convierte el código en HTTPS ni elimina el riesgo en clientes personalizados (`E074`).

### 5.1 Cuenta y credenciales

No hay pantalla de login de usuario. Al primer bind, `DeviceHelper` elige `bindDevRegister` si no existe username. Genera un UUID, llama a `userFreeRegister`, guarda cuenta y contraseña, y después realiza `userLogin`. En arranques siguientes se reutiliza `AppInfo` o las preferencias migradas (`E004`, `E010`). Esto no significa ausencia de autenticación: la app crea una cuenta opaca para el usuario y luego autentica contra el backend.

OAuth usa `grant_type=password` y envía `usr` y `pwd` como parámetros de query en un GET. Access y refresh tokens son JWT; el cliente decodifica `exp` del payload. Usa token cacheado si quedan más de cinco minutos, refresh si también supera ese margen y header `token` (`E033`, `E034`). Los tokens se guardan cifrados en SharedPreferences `oauth` (`E036`). La autenticación de dispositivo puede usar Digest MD5 tras un 401, con `nc00001` y `suiji` constantes (`E031`, `E032`).

## 6. Pairing, dispositivos y acciones

### 6.1 Formatos

QR de dispositivo: cuatro campos separados por espacios, `apName uid authCode model` (`E007`, `E012`). Invitación: query con `InviteCode`, `OwnerId`, `OemId`, `AppVersion`, `DeviceId`, `From` y `AppId`; se exige compatibilidad con `G0021` (`E008`). La comparación de OEM usa `"G0021".contains(deviceShareInfo.getOemId())`, no igualdad estricta, detalle que conviene no copiar sin decidir su semántica.

### 6.2 AP y audio

El alta AP busca el dispositivo en LAN y, si no hay resultados, usa `192.168.111.1:443` con `admin/admin`. Si hay respuesta, prueba una alternativa con `adminapp2` y `SHA-256(authCode)` (`E014`). SSID y contraseña del router se codifican en Base64, no constituyen cifrado (`E015`).

El alta por audio configura 19 frecuencias desde 4.000 Hz en pasos de 150 Hz y emite SSID más los últimos cuatro caracteres del UID y la contraseña WiFi (`E016`). El progreso tiene 150 segundos, polling cada 2 segundos, estados conectando/enviando/esperando/fallo y retry manual (`E017`, `E018`). El bind tiene timeout de 35 segundos y `retry(4)` solo para dos códigos y en el modo que lo permite, con espera de 5 segundos (`E009`, `E010`).

### 6.3 Lista, preview y acciones

La lista se carga de SQLCipher, se contrasta con `getDevList` y se actualiza con credenciales dinámicas. Los estados visibles son vacío, lista, refrescando, online/offline y permisos (`E020`). Las acciones incluyen:

- Preview de uno o cuatro paneles, canales, calidad alta/baja/audio+foto, escucha, habla, captura y grabación (`E021`, `E023`).
- Apertura lock1 y F1 con quick unlock, contraseña o biometría. Apertura tiene timeout de 15 segundos (`E019`, `E025`).
- Llamada a conserjería, compartir con permisos, cambiar WiFi, sincronizar hora, editar nombre y actualizar firmware.
- Invitaciones con permisos preview/playback/alarm/unlock/F1/callGuard. La UI oculta el botón de crear al llegar a 100 (`ShareManageActivity.java:139-150`).
- Eventos en páginas de 15, foto asociada, borrado individual/total y galería local (`E051`, `E052`, `E062`).

## 7. WebSocket, MQTT, SIP, WebRTC y P2P

| Tecnología | Resultado estático | Evidencia/alcance |
|---|---|---|
| WebSocket | Solo la API de OkHttp, sin `newWebSocket` ni `WebSocketListener` en `com.quvii` | `analysis/apk/evidence/security-search.txt` |
| MQTT | Implementado en shadow SDK con Paho, QoS 1, token OAuth y reconexión incremental, pero desactivado por defecto | `SUPPORT_DEVICE_SHADOW=false` impide `QvShadowHelper.init()` y no se encontró override (`E063`, `E073`). Clases Paho no localizadas en sources, comportamiento no ejecutado |
| SIP | Modelos para parámetros SIP del dispositivo, sin cliente SIP de llamada en Wibox | La reproducción usa P2P, `E021`, `E023`; búsqueda negativa registrada |
| WebRTC | `libWebrtcAudio.so` aporta procesado de audio, no prueba PeerConnection | No hay `org.webrtc`/`PeerConnection` en `com.quvii`; `E023` |
| P2P | Transporte principal: `libqv-p2p-v2.so`, puertos locales, `quii://` y player nativo | `E011`, `E021`, `E042`, `E068` |

MQTT suscribe `tdk/client_api/users/get/<account>/<client>/#` y `tdk/client_api/sys/get/<client>/#` con QoS 1. Publica heartbeat en `tdk/client_api/sys/update/<client>` cada 300 segundos. La espera de reconexión aumenta 0 a 10 segundos y se investiga el servicio al superar ese nivel (`E037` a `E040`).

## 8. Persistencia y criptografía

| Área | Implementación estática | Evidencia |
|---|---|---|
| DBFlow | Base `database`, versión 11, entidades Device/AppInfo/AppTask | `AppDatabase.java:14-17`, `E069` |
| SQLCipher | 4.5.4, clave de `QvEncryptKeyStoreUtil.get()`, `PRAGMA cipher_migrate` | `SQLCipherHelperImpl.java:15-36`, `E045` |
| Preferencias `config` | Token FCM, servidor y valores sensibles con AES; ajustes normales en claro | `SpUtil.java:61-91`, `E043` |
| WiFi | Guardar contraseña está activo por defecto; SSID y valor se cifran | `SpUtil.java:188-190,212-214,348-350`, `E044` |
| OAuth | Access/refresh cifrados en SharedPreferences `oauth` | `QvOAuthSpUtil.java:23-47`, `E036` |
| Clave local | AES-128 aleatoria, envuelta con RSA AndroidKeyStore | `QvEncryptKeyStoreUtil.java:240-251,452-498`, `E046`, `E047` |
| Modo AES | `AES/ECB/PKCS7Padding` | `QvEncryptKeyStoreUtil.java:33-49` |
| Dispositivo | SHA-256 hex si capability, conserva valores ya >=64 caracteres | `QvEncrypt.java:17-19`, `E048` |
| Push | `HmacSHA1(clientId-timestamp, clientId)` | `QvPushHelper.java:64-76`; `E048` |
| Digest | MD5 con nc y cnonce fijos | `DeviceAuthHeaderInterceptor.java:24-44`, `E031` |
| Media | Paths por API: externo legacy, externalFilesDir 29, filesDir >=30, caché separada | `SdkManager.java:252-282`, `E005` |
| FileProvider | No exportado, URI grants; `root-path path=""` y `external-path path="."`. El provider de permisos también declara rutas `.` | `res/xml/provider_paths.xml:3-17`, `res/xml/permission_file_paths.xml:3-20`, `E075` |

## 9. Errores, timeouts y reintentos

| Área | Política estática | Evidencia |
|---|---|---|
| Arranque | Abre main al recibir estado de login o a los 10 s | `LoadingPresenter.java:29-51`, `E006` |
| HTTP general | `retryOnConnectionFailure=true`; timeout base 20 s | `OkHttpUtil.java:112-120,562-588`, `E027`, `E030` |
| Auth/alarm | Connect 30 s; read 35 s auth y 60 s alarm; write 20/60 s | `OkHttpUtil.java:136-170` |
| OAuth | Un retry si existe token cacheado, lo limpia; 403 corta | `QvOAuthManager.java:290-320`, `E035` |
| Bind | Timeout 35 s, `retry(4)` solo dos códigos y modo permitido, espera 5 s | `DeviceHelper.java:339-355,706-785`, `E009`, `E010` |
| CGI | `retry(1)` con limpieza de puerto para timeout/caso local | `DeviceCore.java:244-301`; `E009` |
| Device status | Método deprecated: timeout 20 s y `retry(2)` solo timeout | `DeviceHelper.java:965-977` |
| Pairing | 150 s, polling 2 s, retry manual y pantalla de error | `DAWifiSetPresenter.java:156-200,243-294`, `E017`, `E018` |
| Preview | 90 s por defecto; reconexión y `needResume=3` | `PreviewPresenter.java:513-550,955-965`, `E024`, `E026` |
| Unlock | Timeout 15 s, estados enviando/éxito/fallo/timeout | `PreviewPresenter.java:673-696`, `E025` |
| Llamada | Auto-rechazo a 30 s; evento 20 cierra si se atendió en otro terminal | `PushCallPresenter.java:33-49`, `E059` |
| Eventos | Páginas de 15, 502/XML/código derivan en error visible, retry manual | `MainMessageListModel.java:206-253`, `E052` |
| Firmware | Progreso cada 1 s; reintenta errores durante 60 s; `-2` completa | `DMUpgradeModel.java:23-68,80-104` |

No se encontró un backoff exponencial generalizado. Los mecanismos son contadores, delays fijos, polling y retry limitado por área.

## 10. Seguridad, obfuscación y pinning

| ID | Hallazgo estático | Severidad | Evidencia |
|---|---|---|---|
| SEC-01 | TrustManager vacío y `AllowAllHostnameVerifier` en clientes comunes y actualizador | Alta | `OkHttpUtil.java:562-588`, `E030`; `AppVersionManager.java:101-126`, `E049` |
| SEC-02 | `DEBUG_HTTP=true`, `DEV_MODE=true` y logging BODY en release | Alta | `App.java:185-192`, `E002`; `OkHttpUtil.java:67-90`, `E027` |
| SEC-03 | Logs con usuario/password/accountId, FCM, share links, claves y datos WiFi | Alta | `App.java:148-151`, `E003`; `SdkManager.java:90-96`; `PushHelper.java:82-123`; `DAVoiceSetPresenter.java:187-197` |
| SEC-04 | `client.txt` es clave privada PEM; BKS con contraseña embebida | Alta | `E028`, `E072`; hashes en `asset-hashes.txt`, contenido no copiado |
| SEC-05 | Fallback `admin/admin`; otra ruta CGI incluye credenciales en userinfo | Media | `DAWifiSetModel.java:51-100`, `E014`; `QvNetDeviceCoreHelper.java:107-122` |
| SEC-06 | AES-ECB y Digest MD5 con nc/cnonce fijos | Media | `E031`, `E046`, `E047` |
| SEC-07 | URLs `quii://` con contraseña y logs de `dataEncodeKey` | Media | `DeviceHelper.java:846-915`, `E011`; `PreviewModel.java:174-181`, `E021` |
| SEC-08 | Rutas CGI legacy HTTP y URL de privacidad HTTP, sin Network Security Config ni cleartext explícito | Media | `AndroidManifest.xml:61-71`; `AppConst.java:69`, `E064`; `RetrofitUtil.java:299-309,433-482`, `E074` |
| SEC-09 | `Utils.isRooted` existe, pero no se encontró llamada que imponga bloqueo | Informativa | `Utils.java:245-255`, `E071`; `security-search.txt` |
| SEC-10 | FileProvider no exportado permite URI grants y declara root/external paths amplios | Media | `provider_paths.xml:3-17`, `permission_file_paths.xml:3-20`, `E075`. La exposición real depende de las URIs generadas |

La clase `CertificatePinner` pertenece a OkHttp, pero no se usa en `com.quvii`. Un trust store propio no equivale a pinning. La comprobación de hostname también se desactiva en los clientes citados. La lógica interna de las bibliotecas nativas no se ha decompilado; no se afirma que todo el tráfico nativo comparta esta debilidad.

Ofuscación: hay paquetes de una letra y clases renombradas junto a nombres descriptivos `com.quvii.ubell`, y recursos acortados que JADX reasigna. No hay mapping R8/ProGuard ni cifrado general de DEX. Es ofuscación parcial, no una barrera completa.

## 11. SDKs y bibliotecas nativas

Versiones con evidencia en `analysis/apk/evidence/library-versions.txt`:

| Componente | Versión |
|---|---|
| Android Gradle Plugin / ViewBinding | 8.6.0 |
| AppCompat / Material | 1.2.0 / 1.4.0 |
| AndroidX Biometric | 1.1.0 |
| Kotlin coroutines | 1.6.1 |
| RxJava 2 | 2.1.3 |
| OkHttp | 3.9.0 (`okhttp3/internal/Version.java:8-9`) |
| Retrofit | No determinada estáticamente |
| SQLCipher for Android | 4.5.4 |
| Firebase cloud messaging / components | 17.0.1 / 17.1.0 |
| Google Play services basement | 18.1.0 |
| Huawei ScanKit | `scanplus:2.1.0.300` |
| Huawei AGConnect Core | 1.4.0.300 |
| Qualvision P2P | El log declara `p2p version: v3.2`; no se equipara a versión global del SDK |

ABIs: `arm64-v8a` y `armeabi-v7a`. Bibliotecas: `libqv-p2p-v2`, `liblive_player`, `libWebrtcAudio`, `libvoiceRecog`, `libsqlcipher`, `libscannative`, `libeapilrender`, FFmpeg (`avcodec`, `avfilter`, `avformat`, `avutil`, `swresample`, `swscale`) y `libmsoptimize` solo en arm64. Hashes SHA-256 por archivo en `analysis/apk/evidence/native-hashes.txt`.

## 12. Contraste con morluto/rea y firmware relacionado

El APK no contiene identificadores literales morluto/rea en fuentes o strings DEX. La hoja `analysis/rea` sí aporta referencias externas ya verificadas (`analysis/rea/verification.txt: REA_ANALYSIS_VERIFIED`, exit 0). Son fuentes secundarias, no evidencia directa del comportamiento del APK.

| Área | Contraste confirmado | Consecuencia para la réplica |
|---|---|---|
| REA | Es una herramienta genérica de ingeniería inversa, no un cliente Wibox ni una fuente de endpoints (`docs/rea-analysis.md:5-7`) | Reutilizar ideas de análisis, no lógica Wibox |
| P2P | Las notas duhow describen NTS/P2P y el APK contiene P2P nativo, pero no hay wire format ni prueba de compatibilidad (`docs/rea-analysis.md:99-118`; `E042`) | No llamar NTS al protocolo del APK sin evidencia adicional |
| MQTT | APK: shadow con OAuth, QoS 1 y endpoint dinámico, desactivado por defecto. duhow: MQTT local plano 1883 y topics propios (`E037` a `E041`, `E063`, `E073`; `docs/rea-analysis.md:240-250`) | No intercambiar brokers, topics ni modelos de seguridad |
| Media | APK: `quii://` y P2P nativo. El blog cmos486 describe WebRTC/RTSP custom (`E011`, `E021`; `docs/rea-analysis.md:248-256`) | No presentar WebRTC/RTSP como contrato de stock |
| Acciones | Unlock, F1, guardia y llamadas comparten dominio, no necesariamente transporte ni payloads | Reutilizar vocabulario funcional con adaptadores separados |
| OEM | `G0021` aparece en APK y en firmwares duhow B007/B013, sin prueba de compatibilidad cloud universal (`E001`, `E008`; `docs/rea-analysis.md:44-46`) | No fijar compatibilidad por OEM únicamente |
| Secretos | APK y customs muestran riesgos distintos de credenciales y canales planos | Redacción de logs, almacenamiento seguro y TLS validado en cada adaptador |

Pendientes para una comparación binaria futura: certificado `CN=Qualvision` y su hash, `com.quvii.ubell`, hosts `fermax/matainde.qvcloud.net`, conjunto nativo, flujo `userFreeRegister` y estructura XML/MVP. No se afirma compatibilidad NTS/cloud ni igualdad de payloads por analogía.

## 13. Limitaciones e HIPÓTESIS explícitas

- No se instaló ni desinstaló la app, no se usó móvil, emulator, red de dispositivos ni servicios cloud reales.
- No hay capturas reales ni validación dinámica de navegación, streaming, auth, pairing o notificaciones.
- **HIPÓTESIS:** el canal MQTT podría participar en actualizaciones shadow si las clases Paho están disponibles en runtime. Prueba necesaria: inspección DEX adicional y ejecución controlada fuera de esta hoja. No se presenta como hecho.
- **HIPÓTESIS:** las pantallas `legacy_no_inbound` podrían ser restos de flujos antiguos. Prueba necesaria: revisar historial o variantes. Se documenta solo la ausencia de referencias estáticas actuales.
- **HIPÓTESIS:** el comportamiento TLS de bibliotecas nativas podría diferir del cliente Java. Prueba necesaria: análisis nativo y pruebas controladas. No se generaliza el hallazgo Java a todo el tráfico nativo.
- JADX terminó con siete errores y ciertos métodos no son legibles. Las conclusiones se limitan a evidencia visible y búsquedas complementarias.

## 14. Trazabilidad y entregables

- `analysis/apk/inventory.json`: modelo completo de componentes, red, seguridad, SDKs y evidencias.
- `analysis/apk/ui-inventory.json`: 38 pantallas, estados, 71 relaciones de navegación y recursos.
- `analysis/apk/evidence/code-evidence.md`: 75 bloques `E001` a `E075`, cada uno con origen `archivo:línea` o comando.
- `analysis/apk/evidence/ui-code-evidence.md`: 38 bloques de navegación/estados.
- `analysis/apk/evidence/AndroidManifest.xml`: manifiesto decodificado.
- `analysis/apk/evidence/apk-integrity.txt`: hashes, certificado y firma.
- `analysis/apk/evidence/library-versions.txt` y `native-hashes.txt`: versiones y hashes.
- `analysis/apk/resources/manifest.json`: 189 recursos con tamaño, dimensiones cuando aplica y SHA-256.
- `analysis/apk/verify_analysis.py` y `verify_ui_inventory.py`: validación positiva y control negativo.
- `analysis/apk/verification.txt`: comandos, exits y salidas finales.
