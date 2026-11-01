# Implementación Android y correspondencia visual

Fecha de implementación y verificación: 2026-10-31. Hoja leaf-1.2.2.

## 1. Alcance y honestidad funcional

La aplicación es una app Android real escrita en Kotlin con Jetpack Compose. Arranca con una portada breve y continúa a las cuatro pestañas principales, sin login inventado. La navegación, los formularios, las acciones y los estados se ejecutan contra un repositorio local. No es una colección de imágenes ni una maqueta estática.

El modo está identificado como `LOCAL DEMO MODE` en las pantallas principales y se explica en Ajustes. `FakeWiboxCloud` implementa la interfaz pública `WiboxCloud` y devuelve datos deterministas. Sus reconocimientos contienen `local-demo-ack-no-physical-action`. La UI los presenta como reconocimientos locales, nunca como apertura, vídeo, conexión o compatibilidad con el cloud original.

No se contacta con servicios reales y no hay credenciales originales. El manifiesto declara solo `CAMERA`, y se pide en ejecución cuando el usuario pulsa el botón de la pantalla de escaneo: si la deniega, el campo manual sigue funcionando. No se solicitan micrófono, ubicación, biometría ni acceso WiFi. Las acciones físicas no se reintentan automáticamente.

## 2. Arquitectura

- `MainActivity`: crea el repositorio y monta Compose.
- `WiboxApp`: tema, `NavHost`, efectos de navegación y snackbars.
- `ui/Routes.kt`: rutas tipadas y cobertura de las 33 pantallas alcanzables o representaciones del inventario. Las cinco pantallas legacy quedan fuera.
- `ui/AppViewModel.kt`: estado inmutable de pestañas, dispositivos, eventos, galería, ajustes, selección y acciones.
- `ui/AddDeviceViewModel.kt`: borrador de alta, parseo, descubrimiento local simulado, bind y máquina de estados de pairing.
- `ui/PreviewViewModel.kt`: preview simulado, uno o cuatro paneles, audio local, captura, grabación y acciones.
- `data/FakeWiboxCloud.kt`: implementación local de toda la interfaz pública cloud. Los métodos sin equivalente local devuelven `CloudError.Unsupported`.
- `data/WiboxRepository.kt`: frontera entre ViewModels, fake y persistencia.
- `data/LocalDemoStore.kt`: `DataStore` de preferencias con un snapshot JSON versionado. `InMemoryDemoStore` aísla las pruebas.
- `data/DeviceCodeParser.kt`: validación de QR e invitaciones, incluida codificación UTF-8 y rechazo de duplicados.

La app solo importa API pública de `dev.nacho.wibox.core.cloud`. No importa Ktor, OkHttp, DTOs internos, paquetes del APK ni código decompilado.

## 3. Matriz inventario a implementación

La columna de prueba distingue pruebas automatizadas y evidencia visual. `RoutesTest` comprueba la cobertura de rutas, no certifica por sí sola el comportamiento de una pantalla.

| Inventario original | Ruta o representación Compose | Recurso o sustitución | Estados cubiertos | Prueba o captura |
|---|---|---|---|---|
| `loading` | `loading`, `LoadingScreen` | `start.png` original | Portada de 450 ms y transición a main | `MainActivityInstrumentedTest` |
| `cache` | Representación sin UI dentro del arranque de `MainActivity` | Ninguno, igual que el original | Sin pantalla propia. No hay relay push en modo local | `MainActivityInstrumentedTest` |
| `main_tab` | `main`, `MainScreen` | Cabecera y bottom tabs propios con iconos originales | Cuatro selecciones, edición con tabs ocultas, rail en pantalla ancha | `MainActivityInstrumentedTest`, capturas 01 a 04 |
| `add_type` | `add_type`, `AddTypeScreen` | `option_button` rectangular propio | Nueva y online | `LocalFlowInstrumentedTest`, [06-add-device](../fastlane/06-add-device.png) |
| `preview` | `preview/{deviceId}`, `PreviewScreen` | Preview negro, vectores Material propios | Connecting, playing simulado, stopped, offline, unsupported, uno/cuatro paneles, portrait/landscape, escucha, habla, grabación, captura y acciones locales | `PreviewViewModelTest`, `StateAndInvitationInstrumentedTest`, [05-preview](../fastlane/05-preview.png) |
| `online_type` | `online_type`, `OnlineTypeScreen` | Botones rectangulares propios | Búsqueda local simulada y manual | `RoutesTest`, `AddDeviceViewModelTest` |
| `product_choose` | Omitida | `legacy_no_inbound` | Sin entrada estática en el original | `omittedLegacyScreens` en `RoutesTest` |
| `product_intro` | `product_intro`, `ProductIntroScreen` | `reset_device_img.png` original | Instrucciones y paso manual. No se fingen permisos | `LocalFlowInstrumentedTest` |
| `handle_wifi` | `handle_wifi`, `HandleWifiScreen` | Texto e icono de copiado propios | Instrucciones, AP demo, copiado y continuación | `RoutesTest` |
| `wifi_list_legacy` | Omitida | `legacy_no_inbound` | Sin entrada estática | `omittedLegacyScreens` |
| `wifi_set_legacy` | Omitida | `legacy_no_inbound` | Solo cuelga de la lista legacy | `omittedLegacyScreens` |
| `device_bind` | `device_bind`, `DeviceBindScreen(shared=false)` | Campos rectangulares propios | Manual, QR bloqueado, validación, duplicado, binding y alta persistente | `AddDeviceViewModelTest`, `LocalFlowInstrumentedTest` |
| `online_devices` | `online_devices`, `OnlineDevicesScreen` | Filas e icono de dispositivo propios | Loading, resultados, vacío y error | `AddDeviceViewModelTest` |
| `scan` | `scan`, `ScanCodeScreen` | Tarjeta de cámara sobre superficie tonal, marco de encuadre y campo manual debajo | Escaneo con CameraX y ZXing sin Google Play Services, permiso en ejecución con motivo previo, rechazo que deja el campo manual, y pegado a mano | `DeviceCodeParserTest`, `QrDecoderTest`, flujos instrumentados de alta e invitación |
| `config` | `config/{deviceId}`, `DeviceConfigScreen` | Filas de 50 dp, switches Material y diálogos de opción con radios | Owner, shared, alarmas, valores editables, borrado, sync local, los tres modos de visualización del stock (alta, baja y foto), Intercom Way (full duplex y pulsar para hablar), tiempo de F1 de 1 a 10 s y sincronización de fecha y hora con resultado visible | `AppViewModelTest`, `SecondaryFlowsInstrumentedTest`, [07-config](../fastlane/07-config.png) |
| `password_modify` | `password_modify/{deviceId}/{mode}`, `PasswordModifyScreen` | Formulario propio | Device, admin, unlock, campos vacíos, mismatch y validación local sin guardar secretos | `RoutesTest` |
| `unlock_setting` | `unlock_setting/{deviceId}`, `UnlockSettingScreen` | Switches propios | Quick on/off persistente y confirmación biométrica persistente en `AppPreferences`, pedida por el sistema solo cuando está activa | `AppViewModelTest` |
| `device_info` | `device_info/{deviceId}`, `DeviceInfoScreen` | Formulario propio | UID read-only, edición, duplicado y guardado | `AppViewModelTest` |
| `about` | `about`, `AboutScreen` | `logo.png` original | Versión local, privacidad local y gesto de cinco pulsaciones | `SecondaryFlowsInstrumentedTest` |
| `image_pager` | `image_pager/{mediaId}`, `ImagePagerScreen` | Marcadores multimedia honestos, sin fotos falsas | Blanco, negro, página, navegación, share unsupported y borrado | `SecondaryFlowsInstrumentedTest` |
| `video_player` | `video_player/{mediaId}`, `VideoPlayerScreen` | Controles propios sobre panel negro | Playing, paused, muted, seeking y controles visibles/ocultos, todo simulado | `SecondaryFlowsInstrumentedTest` |
| `push_call` | `push_call/{deviceId}`, `PushCallScreen` | `backgroud_calling.png`, `btn_accept.png`, `btn_refuse.png`, `ico_logo.png` originales | Ringing local, vibración, aceptar, rechazar y cierre a 30 s | `SecondaryFlowsInstrumentedTest`, [08-call](../fastlane/08-call.png) |
| `debug` | `debug`, `DebugScreen` | Filas propias | Tipo fake, ausencia de token, escenarios, reset y reinicio de loading | `SecondaryFlowsInstrumentedTest` |
| `upgrade` | `upgrade/{deviceId}`, `UpgradeScreen` | Filas y botones propios | Query local, up-to-date y upgrade deshabilitado | `RoutesTest` |
| `guide` | Omitida | `legacy_no_inbound` | Loading original siempre abre main | `omittedLegacyScreens` |
| `add_method_legacy` | Omitida | `legacy_no_inbound` | Sin entrada estática | `omittedLegacyScreens` |
| `router` | `router?modify={modify}`, `RouterScreen` | Lista WiFi y formulario propios | Selección, password visible/oculta, preferencia de guardado sin persistir secretos, modo nuevo y modificación | `LocalFlowInstrumentedTest` |
| `voice` | `voice`, `VoiceProvisioningScreen` | `device_add_sound_waver.png` original | Sending, waiting y ready. Sin emitir audio | `AddDeviceViewModelTest` |
| `wifi_progress` | `wifi_progress`, `WifiProvisioningScreen` | `device_add_wifi_set.png` original | Connecting, sending, waiting, ready y failure manual | `AddDeviceViewModelTest`, `LocalFlowInstrumentedTest` |
| `share_device` | `share_device/{invitationId}`, `ShareDeviceScreen` | QR real sobre fondo blanco, enlace, código, caducidad y permisos concedidos | QR generado con ZXing que el propio escáner de la app lee, enlace, chooser Android, expiración y lista de permisos de la invitación | `SecondaryFlowsInstrumentedTest`, `QrEncoderTest` |
| `pairing_fail` | `pairing_fail`, `PairingFailScreen` | Texto y botón propios | Error simulado y regreso a main | `AddDeviceViewModelTest` |
| `share_manage` | `share_manage/{deviceId}`, `ShareManageScreen` | Filas de invitación propias y diálogo de usuario | Loading, empty, list, create con nombre y permisos, edit con nombre y permisos, delete, límite y error | `SecondaryFlowsInstrumentedTest`, `ViewModelsTest` |
| `share_bind` | `share_bind`, `DeviceBindScreen(shared=true)` | Formulario propio con UID/código bloqueados | Invitación válida, duplicado, aceptación y alta compartida | `StateAndInvitationInstrumentedTest` |
| `tab_devices` | Contenido de `main`, `DevicesScreen` | `device_cover_default.png`, estados online/offline originales | Loading, empty, list, offline, acciones deshabilitadas, resultado local y confirmación con la credencial del sistema antes de abrir o disparar F1 cuando el ajuste está activo | `LocalFlowInstrumentedTest`, [01-devices](../fastlane/01-devices.png) |
| `tab_events` | Contenido de `main`, `EventsScreen` | Iconos Material propios | Loading, empty, list, warning, edit, selección y borrado | `AppViewModelTest`, [02-events](../fastlane/02-events.png) |
| `tab_event_list` | `EventRow` y paginación dentro de `EventsScreen` | Filas de 55 dp propias | Empty, refresh manual, load more cada 15 y edición | `AppViewModelTest` |
| `tab_gallery` | Contenido de `main`, `GalleryScreen` | Cuadrícula de tres columnas con marcadores locales | Empty, grid, edit, selected, select all, save/share y delete | `AppViewModelTest`, [03-gallery](../fastlane/03-gallery.png) |
| `tab_settings` | Contenido de `main`, `SettingsScreen` | Switches Material | Sound/vibration persistentes, about, escenarios, reset y salida | `MainActivityInstrumentedTest`, [04-settings](../fastlane/04-settings.png) |

## 4. Recursos originales y sustituciones

Se copiaron solo 20 imágenes representativas desde `analysis/apk/resources/drawable-xhdpi` a `app/src/main/res/drawable-xhdpi`:

- `start.png`, `device_cover_default.png`, `reset_device_img.png`.
- `backgroud_calling.png`, `btn_accept.png`, `btn_refuse.png`, `ico_logo.png`, `logo.png`.
- `device_add_sound_waver.png`, `device_add_wifi_set.png`.
- Ocho iconos de tabs normales y seleccionados: `main_btn_device*`, `main_btn_call_log*`, `main_btn_file*`, `main_btn_setting*`.
- `device_status_online.png` y `device_status_offline.png`.

Su origen y sus hashes son comprobables en `analysis/apk/resources/manifest.json`. Por ejemplo, `start.png` conserva SHA-256 `53a1229992f5df3e8039d3cd760c7dfbb18bb1f1235a574e8e1ceb7e9ae8648f` y `device_cover_default.png` conserva `429e0a1ae5172d9017752de81876c5ad4b7df38012fe071bdecd111099ce3b40`. No se copiaron fuentes, certificados, credenciales, binarios nativos, layouts completos ni árboles JADX.

El resto de iconos son vectores propios con geometría Material o equivalentes simples. Sustituyen acciones, chevrons y controles que no eran necesarios copiar. La portada y los controles de llamada sí reutilizan recursos originales por su peso visual.

## 5. Sistema visual y accesibilidad

- Paleta fija: `#78c9e7`, fondo `#f4f4f4`, texto `#4d4d4d`, tab inactivo `#929292`, selección `#00a3dd`, error `#e73e04` y preview negro.
- Sans-serif del sistema. Títulos de 20 sp, botones de 18 sp y tabs de 12 sp.
- Cabecera de 60 dp, tabs de 55 dp y botones de al menos 50 dp. Las filas pueden crecer si el texto lo necesita para no cortar contenido con escalas de fuente mayores.
- Portadas de 200 dp, separación rectangular y jerarquía del inventario. No se añadió un dashboard ni un tema Material genérico distinto.
- No hay tema oscuro propio. El negro se limita a preview y visores.
- Se respetan insets, back del sistema y objetivos táctiles de al menos 48 dp. En anchos de 600 dp o más, las tabs pasan a rail para no estirar una barra de teléfono.
- Hay 213 claves localizadas en inglés y español. Las cadenas originales sin traducir se tradujeron de forma consciente, por ejemplo `TODA LA ACTIVIDAD` en español.
- Los iconos tienen content descriptions o van acompañados de texto. Los tags estables siguen el prefijo de cada pantalla y acción.

Se inspeccionaron las ocho capturas en 1080x2400 y estados de inicio, tabs, preview, alta, configuración y llamada. También se comprobó dispositivos y alta con `font_scale=1.3`, restaurando después el valor original `1.0`. No se observaron solapes ni recortes en esas evidencias. Esta comprobación no equivale a una comparación dinámica con el APK original, del que no existen capturas runtime en el inventario.

## 6. Estados transversales y pruebas

| Estado | Representación | Evidencia |
|---|---|---|
| Loading | Portada y `LoadingContent` en listas y pairing | Escenario LOADING en instrumentación |
| Contenido | Fixtures locales reactivos y persistidos | 36 tests unitarios, 45 en `core:cloud` y flujo E2E |
| Vacío | Botones/mensajes específicos en devices, events, gallery y usuarios | Escenario EMPTY y borrado de invitación |
| Error | Mensaje tipado, color `#e73e04` y retry manual | Escenario ERROR y tests de `CloudError` |
| Deshabilitado | Offline, permisos insuficientes, upgrade y credencial del sistema no disponible | `StateAndInvitationInstrumentedTest`, capturas de dispositivos |
| Unsupported | Texto explícito, sin éxito ni reintento | Escenario UNSUPPORTED y tests de fake/ViewModels |

La app tiene 36 tests unitarios en cinco ficheros: parser de códigos, rutas, ViewModels y el codificador y el decodificador de QR. `core:cloud` suma 45 en siete ficheros. Los instrumentados son 24 en seis clases: actividad real, E2E local, estados, invitación, flujos secundarios, API externa y captura de superficies. Todos usan repositorios en memoria o DataStore temporal y ninguno usa red.

Comandos de referencia:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks
python3 scripts/verify_architecture.py
./gradlew :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
python3 scripts/verify_project.py
```

El `ANDROID_SERIAL` es obligatorio en este entorno. Todas las acciones ADB se hicieron con `adb -s emulator-5554`. No se instaló ni ejecutó nada en el OnePlus físico.

## 7. Capturas reales

Todas proceden de la app debug ejecutándose en `emulator-5554`, Android 17/API 37, a 1080x2400. Se obtuvieron con `adb -s emulator-5554 exec-out screencap -p`.

1. [Dispositivos](../fastlane/01-devices.png)
2. [Eventos](../fastlane/02-events.png)
3. [Galería](../fastlane/03-gallery.png)
4. [Ajustes](../fastlane/04-settings.png)
5. [Preview simulado](../fastlane/05-preview.png)
6. [Alta de dispositivo](../fastlane/06-add-device.png)
7. [Configuración de dispositivo](../fastlane/07-config.png)
8. [Llamada simulada](../fastlane/08-call.png)

## 8. Diferencias y limitaciones deliberadas

- El escaneo usa CameraX con `com.google.zxing:core`, que decodifica en el propio dispositivo y no habla con Google Play Services. Se abre con la cámara trasera si existe y, si no, con la frontal. En el AVD sin ventana el escenario virtual entrega fotogramas negros y no hay nada que decodificar: eso es una limitación del emulador, no del lector, y la decodificación está cubierta por `QrDecoderTest`.
- El QR del sticker trae cinco campos (`apName udid password ssid mac`) y el token de fábrica documentado trae cuatro. El parser acepta las dos formas y guarda `ssid` y `mac` cuando están. Ese es el único dato que se usa para el alta: el QR no lleva credenciales de cloud.
- Las invitaciones solo existen en el snapshot local y su enlace apunta a `example.invalid`, así que el dominio no resuelve en un navegador. El QR sí es escaneable: lo genera ZXing con corrección de errores M y zona de silencio de cuatro módulos, y el escáner de esta misma app lo lee y lo interpreta como invitación. Lo que no hace es funcionar fuera de la app.
- El QR se dibuja con los módulos alineados a píxeles enteros sobre un fondo blanco fijo, también en tema oscuro, porque un QR sobre superficie oscura no se lee.
- Preview y galería usan marcadores sintéticos etiquetados. No contienen vídeo, fotos ni audio originales. Capturar o grabar añade una entrada local, no un archivo multimedia real.
- AP, LAN, audio y WiFi son máquinas de estados locales. No cambian la red del teléfono, no emiten sonido y no prueban compatibilidad de pairing.
- La aceptación de llamada abre preview local. No registra FCM ni confirma una llamada real.
- Las contraseñas de formularios no se persisten. Los ajustes no sensibles, dispositivos, eventos, galería e invitaciones sí usan DataStore.
- La confirmación biométrica no usa `BiometricPrompt`: abre la pantalla de credencial del sistema (`KeyguardManager.createConfirmDeviceCredentialIntent`), que pide huella, PIN o patrón. Si el teléfono no tiene ninguna credencial configurada, el intent vuelve nulo y la acción se ejecuta sin gate, que es el mismo comportamiento que tendría el sistema. La sincronización de hora escribe una marca local en el dispositivo demo, no habla con el portero. El firmware sigue deshabilitado y no hay actualizaciones reales.
- Los ajustes del dispositivo replican las filas del original: nombre, contraseñas, apertura, Intercom Way, modo de visualización con los tres valores del stock, Wi-Fi, usuarios, tiempo de F1 de 1 a 10 s, recepción de llamada, alertas de movimiento, versión de firmware, sincronizar hora y borrar. La fila `Lock Number` del original está oculta con `visibility="gone"` y no se replica.
- El banner local y el selector de escenarios son diferencias necesarias para evitar que una demo se confunda con una conexión operativa.
- Las cinco pantallas `legacy_no_inbound` se omiten porque no tienen navegación de entrada en el inventario actual.
- La fidelidad afirmada es estructural y visual frente a layouts y recursos estáticos. No se declara equivalencia cloud, compatibilidad con dispositivos ni equivalencia visual dinámica sin evidencia.
