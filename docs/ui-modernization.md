# Modernización de la UI de Wibox

## 1. Objetivo y límites

La UI puede modernizarse sin mantener un clon píxel a píxel. La restricción principal pasa a ser funcional: conservar las 33 pantallas o representaciones no legacy alcanzables, sus estados, acciones, navegación y recursos necesarios, y documentar las 5 pantallas legacy sin entrada actual. La compatibilidad QVCloud/Quvii y la honestidad sobre lo no implementado tienen prioridad sobre el acabado visual.

Este documento es diseño y criterio de aceptación. No describe capturas nuevas ya producidas ni una implementación Compose posterior. Las capturas actuales citadas en `docs/ui-implementation.md` pertenecen a la demo existente, no prueban los cambios aquí propuestos ni compatibilidad con el APK original.

Inventario de referencia: `analysis/apk/ui-inventory.json` contiene 38 entradas y 71 aristas. De las 33 no legacy, `cache` es un relay sin UI. Por tanto, la implementación posterior debe cubrir 33 destinos o representaciones no legacy, con 32 superficies visuales y un relay, más 5 especificaciones legacy documentadas.

## 2. Diagnóstico de la implementación Compose actual

La base actual ya tiene Compose, rutas para las 33 representaciones no legacy, estados demo y etiquetas de modo local. También tiene limitaciones que una modernización real debe resolver:

1. `WiboxApp` concentra un único `NavHost` y crea tres ViewModels de larga duración para todos los flujos. El borrador de alta, el estado global y el preview comparten vida más allá de su pantalla.
2. `WiboxRepository` está construido sobre `FakeWiboxCloud`. Los ViewModels reciben `DemoDevice`, `DemoSnapshot` y `DemoScenario`, por lo que el modelo de presentación está acoplado a fixtures.
3. `LoadingScreen` navega tras 450 ms. No representa restauración de sesión, discovery, preparación de proveedor ni error de arranque.
4. La navegación usa cadenas y argumentos manuales. Es legible, pero necesita reglas explícitas para process death, deep links, llamadas duplicadas y retorno de subflujos.
5. La demo completa pairing y preview mediante temporizadores. Sus textos `PLAYING_SIMULATED` y ack local son honestos, pero esos estados no deben reutilizarse como estados de éxito stock.
6. Algunas funciones conservan la forma, no el contenido: marcadores en vez de media, metadata en vez de captura o grabación y enlaces sintéticos en vez de invitaciones reales. La modernización debe mantener esos huecos visibles hasta cerrarlos. El escaneo de QR ya no está en esta lista: la cámara lee los códigos con CameraX y ZXing, y el campo manual queda como alternativa.
7. La paleta actual reproduce texto blanco sobre `#78c9e7`. No debe darse por accesible sin medir contraste. Modernizar permite elegir contenido oscuro o un color primario más oscuro.
8. La carga demo tolerante a datos dañados no es válida para datos reales. Un snapshot corrupto no puede reiniciar silenciosamente la instalación a fixtures.

## 3. Arquitectura Compose propuesta

### 3.1 Grafos y navegación

Mantener un `NavHost` raíz pequeño y separar grafos con rutas estables y argumentos tipados:

| Grafo | Destinos | Responsabilidad |
|---|---|---|
| `session` | `loading`, relay `cache`, decisión de conexión | Restaurar, preparar proveedor, resolver push inicial y errores |
| `main` | `main_tab`, `tab_devices`, `tab_events`, `tab_event_list`, `tab_gallery`, `tab_settings` | Cuatro pestañas, estado de cada pestaña y edición |
| `pairing` | `add_type`, `online_type`, `product_intro`, `scan`, `online_devices`, `handle_wifi`, `router`, `voice`, `wifi_progress`, `device_bind`, `share_bind`, `pairing_fail` | Borrador único del alta, permisos y retorno cancelado |
| `device` | `preview`, `config`, `password_modify`, `unlock_setting`, `device_info`, `router` en modificación, `upgrade` | Dispositivo y canal seleccionados, acciones y ajustes |
| `sharing` | `share_manage`, `share_device`, aceptación desde QR | Invitaciones, permisos y navegación entre cuentas |
| `media` | `image_pager`, `video_player` | Media local real y estado del reproductor |
| `system` | `push_call`, `about`, `debug` | Llamada entrante, información y diagnóstico restringido |

Reglas:

- IDs estables para `deviceId`, `channelId`, `mediaId`, `invitationId` y `callId`. No codificar secretos en rutas o deep links.
- `SavedStateHandle` conserva identidad y posición, no tokens, passwords ni `CredentialMaterial`.
- Cada pestaña conserva scroll, paginación, selección y estado de carga al cambiar de pestaña o de proceso.
- `router` es un destino compartido con argumento `modify`, no dos implementaciones divergentes en los grafos pairing y device. Su resultado vuelve al origen correcto.
- El alta tiene un solo resultado `bound`, `cancelled`, `failed` o `outcomeUnknown`. Volver atrás no deja un borrador zombie ni un pairing activo.
- Aceptar una llamada consume un `callId` una sola vez. Rotación y recomposición no aceptan ni rechazan de nuevo.
- El back stack se prueba con las 71 aristas originales como referencia. Un cambio de agrupación es válido solo si conserva destinos, acciones y salida previsible, y tiene test.
- Las cinco pantallas legacy no se insertan por estética. Reactivar `guide`, `product_choose`, `add_method_legacy`, `wifi_list_legacy` o `wifi_set_legacy` requiere una decisión funcional, una entrada explícita y sus pruebas.

### 3.2 ViewModels y modelos de presentación

| ViewModel | Ámbito | Estado que posee |
|---|---|---|
| `SessionViewModel` | Grafo de sesión | Proveedor, modo demo/real, preparación, error y llamada pendiente |
| `DevicesViewModel` | `tab_devices` | Lista, frescura, presencia, refresh y acciones pendientes |
| `EventsViewModel` | Eventos | Páginas, filtros visibles, fotos, selección y gaps |
| `GalleryViewModel` | Galería | Archivos, selección, pager y operaciones de media |
| `PairingViewModel` | Grafo de alta | Borrador no secreto, etapa, permisos, candidatos y resultado |
| `DeviceDetailViewModel` | Un dispositivo | Detalle, Shadow, capacidades, configuración y errores |
| `PreviewViewModel` | Una `MediaSessionHandle` | Paneles, canal, reproducción, audio, captura, grabación y acciones |
| `CallViewModel` | Un `callId` | Ringing, aceptar, rechazar, timeout y answered elsewhere |
| `SharingViewModel` | Un dispositivo | Invitaciones, permisos, límite y enlaces efímeros |
| `SettingsViewModel` | Ajustes | Preferencias, privacidad, diagnóstico y proveedor |

Los Composables reciben estado inmutable y eventos. No llaman a repositorios, no parsean QR, no conocen DTOs y no deciden soporte. Un `UiMapper` traduce modelos provider-neutral a modelos de presentación. `Demo*` queda confinado al adaptador demo.

Las acciones físicas se ejecutan en el ViewModel del dispositivo correspondiente mediante un `ActionCoordinator` común. Este coordinador impide duplicados por `commandId`, conserva `ActionOutcome` y evita que dos pantallas repitan una apertura al recomponerse.

### 3.3 Diseño visual mantenible

- Compose Material 3 como base de componentes, con tokens propios para color, tipografía, elevación, estados y dimensiones. No un tema Material genérico superpuesto sin revisar.
- Componentes comunes: `ProviderStatusBanner`, `LoadContent`, `DeviceCard`, `CapabilityAction`, `ActionOutcomeText`, `PermissionRationale`, `PairingProgress`, `MediaControls`, `SelectionBar` y `ErrorPanel`.
- Iconos originales solo cuando estén trazados en `analysis/apk/resources/manifest.json` y sean necesarios para identidad o acción. Para iconografía nueva se prefiere Lucide convertido a vector o el estándar Android si no existe equivalente. No se reutilizan binarios, certificados ni recursos secretos.
- La identidad puede conservar el azul Wibox, pero el contenido sobre primario se decide por contraste medido. No se fija texto blanco sobre `#78c9e7` si no alcanza AA.
- Cuatro pestañas en teléfono y `NavigationRail` en ancho suficiente. No convertir la app en un dashboard nuevo ni eliminar la jerarquía de alta, dispositivo y galería.
- Preview y visores conservan superficie oscura por legibilidad de media. El resto no se oscurece automáticamente sin suite de contraste.

## 4. Inventario completo y destino modernizado

Convenciones de la tabla:

- La columna original resume funcionalidad, todos los IDs de estado del inventario y recursos o acciones que no se pueden perder.
- La situación actual se refiere a la app demo existente, no al APK original.
- Las pruebas y capturas nombradas son obligaciones futuras. `CAP-*` no implica que el archivo exista.
- `legacy_documented` conserva la especificación fuera del grafo principal, sin afirmar que hoy sea alcanzable.

| ID | Funcionalidad, estados y recursos originales | Situación actual | Destino modernizado | Evidencia y test requeridos |
|---|---|---|---|---|
| `loading` | Entrada y restauración. Estados: `splash`, `storage_permission`, `phone_permission`, `startup_wait`, `push_redirect`. Recurso `start.png`. Navega a `main_tab`, `push_call`, `preview`. | Portada de 450 ms y salto a main. No hay sesión ni permisos reales. | `SessionScreen` con preparación real, error recuperable y deep link; portada breve solo si no retrasa el arranque. | `SessionViewModelTest` para espera/error/push y permisos de almacenamiento/teléfono sin bloqueo indebido; `NavigationTest` para tres destinos; `CAP-loading-splash` y `CAP-loading-error` en emulador. |
| `cache` | Relay push sin layout. Estados: `relay`, `finish`. Navega a `loading`. | Representación dentro de `MainActivity`, sin relay real. | `PushRelay` sin UI, con extras validados y cierre inmediato. | `PushRelayTest` para copiar solo datos permitidos y terminar. Sin captura por no tener UI. |
| `main_tab` | Cuatro pestañas y diálogos. Estados: `devices_selected`, `events_selected`, `gallery_selected`, `settings_selected`, `terms`, `android_hint`, `edit_mode`. Recursos de tabs normales/seleccionados. Navega a `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings`. | Cuatro tabs, rail ancho y edición con tabs ocultas. Términos y aviso Android no son reales. | `MainScaffold` con estado por pestaña, términos y avisos accionables solo cuando correspondan. | `MainNavigationTest`, restauración de tab y selección; `CAP-main-tabs`, `CAP-main-edit`, `CAP-main-terms`. |
| `add_type` | Elegir alta nueva u online. Estados: `new_device`, `online_device`. Estilo `option_button`. Navega a `product_intro`, `online_type`. | Ambas opciones existen. | `AddTypeScreen` en grafo pairing, con accesos claros y capacidad de proveedor. | `PairingNavigationTest` para dos ramas; `CAP-add-type`. |
| `preview` | Video y acciones. Estados: `empty`, `connecting`, `playing`, `stopped`, `refresh`, `password_error`, `offline`, `one_window`, `four_windows`, `portrait`, `landscape`, `listening`, `talking`, `recording`, `snapshot`, `unlock_progress`, `unlock_success`, `unlock_failure`, `f1`, `timeout`, `no_permission`. Controles play, canal, calidad, escucha, habla, captura, grabación, unlock/F1. Navega a `config`, `password_modify`. | Simulación honesta con paneles, controles y acciones locales; no hay P2P, archivos reales ni ack físico. | `PreviewScreen` sobre `MediaSessionHandle`, controles por capacidad y resultados por nivel de ack. Conservar uno/cuatro paneles y rotación. | `PreviewViewModelTest` para todos los estados; `MediaContractTest`; `ActionOutcomeTest`; `CAP-preview-one`, `CAP-preview-four`, `CAP-preview-landscape`, `CAP-preview-offline`, `CAP-preview-unsupported`, `CAP-preview-action-pending`, `CAP-preview-ack`. Prueba hardware posterior para media/acciones. |
| `online_type` | Buscar online o alta manual. Estados: `scan_online`, `manual`. Navega a `online_devices`, `device_bind`. | Ambas rutas existen; búsqueda simulada. | `OnlineTypeScreen` con origen LAN real o demo etiquetado. | `PairingNavigationTest`; `DiscoveryStateTest`; `CAP-online-type`. |
| `product_choose` | Selector Wi-Fi Doorbell 7410 y Monitor 9440. Estados: `doorbell`, `monitor`. Recursos `device_type_7410`, `device_type_9440`. Navega a `product_intro`. `legacy_no_inbound`. | Omitida como legacy. | `legacy_documented`: conservar modelos, estados y recursos en especificación; no añadir entrada sin evidencia de necesidad. | `LegacyCoverageTest` para impedir borrado accidental. Si se reactiva: `PairingNavigationTest` y `CAP-product-choose`. |
| `product_intro` | Reset y acceso a QR/WiFi. Estados: `reset_instructions`, `location_permission`, `camera_permission`, `scan_error`, `wifi_switch_error`. Recurso `reset_device_img`. Navega a `scan`, `router`, `handle_wifi`. | Instrucciones y paso manual; no permisos ni cámara reales. | `ProductIntroScreen` con permisos just-in-time y recuperación a ajustes sin bloquear rutas no dependientes. | `PermissionFlowTest` para ubicación/cámara; `PairingNavigationTest`; `CAP-product-intro`, `CAP-product-intro-permission`. |
| `handle_wifi` | Paso manual al AP. Estados: `instructions`, `copied`, `ap_mismatch`, `ap_connected`. Recursos `prompt_setup_wifi`, `icon_copy`. Navega a `router`, `wifi_progress`. | Texto, copia y continuación demo. | `HandleWifiScreen` con detección real de SSID cuando exista y salida manual segura. | `WifiSelectionTest` para copiar/mismatch/connected; `CAP-handle-wifi-mismatch`. |
| `wifi_list_legacy` | Lista WiFi legacy. Estados: `loading`, `list`, `error`, `password`, `connect_failure`, `success`. Recursos `wifi_0`. Navega a `wifi_set_legacy`. `legacy_no_inbound`. | Omitida como legacy. | `legacy_documented`: no fusionar con `router` sin conservar sus seis estados en la especificación. | `LegacyCoverageTest`. Si se reactiva: `WifiLegacyTest` y `CAP-wifi-list-legacy-*` por estado. |
| `wifi_set_legacy` | Configuración WiFi legacy. Estados: `password_hidden`, `password_visible`, `countdown`, `connect_error`, `modify_success`, `set_failure`. Navega a `config`, `product_intro`. `legacy_no_inbound`. | Omitida como legacy. | `legacy_documented`: conservar cuenta atrás, errores y dos destinos documentados. | `LegacyCoverageTest`. Si se reactiva: `WifiLegacyTest` y `CAP-wifi-set-legacy-*` por estado. |
| `device_bind` | Formulario de alta. Estados: `qr_locked`, `manual`, `password_hidden`, `password_visible`, `share_link`, `validation_error`, `binding`, `success`, `invitation_error`. Acciones nombre, UID, auth code, QR y bind. Navega a `main_tab`, `scan`. | Validación, duplicados, binding y alta persistente local. | `DeviceBindScreen` con secreto efímero, `PairingResult` y resultado desconocido tras timeout. | `PairingViewModelTest` para todos los estados; `SecretStateTest`; `CAP-device-bind-manual`, `CAP-device-bind-validation`, `CAP-device-bind-pending`. |
| `online_devices` | Resultados LAN. Estados: `refreshing`, `results`, `empty`, `error`. Navega a `device_bind`. | Loading, resultados, vacío y error simulados. | `OnlineDevicesScreen` con lista real, frescura y vacío solo tras búsqueda completada. | `DiscoveryViewModelTest` para cuatro estados y duplicados; `CAP-online-devices-empty`, `CAP-online-devices-results`. |
| `scan` | Escáner QR y galería. Estados: `scanning`, `pick_image`, `invalid`, `result`, `new_device`, `share`. Recursos `qr_scan`, galería. Navega a `product_intro`, `device_bind`, `share_bind`. | Cámara con CameraX y ZXing sobre el parser, más entrada manual. Sin selección de imagen. | `ScanCodeScreen` con cámara FOSS, galería, pegar como alternativa y cierre de cámara al salir. | `CameraPermissionTest`, parser y tres destinos; `CAP-scan-camera`, `CAP-scan-pick-image`, `CAP-scan-invalid`. |
| `config` | Ajustes de dispositivo. Estados: `owner`, `shared`, `loading`, `alarm_on`, `alarm_off`, `upgrade_available`, `delete_confirm`, `delete_error`, `bind_required`. Acciones info, passwords, unlock, WiFi, usuarios, firmware, alarmas, sync hora, F1/FPS/lock/audio y borrado. Navega a `device_info`, `password_modify`, `unlock_setting`, `router`, `upgrade`, `share_manage`, `main_tab`. | Owner/shared, alarmas y edición local; upgrade y varias operaciones no son reales. | `DeviceConfigScreen` por capacidades y rol, sin ocultar unsupported como si fuera ausencia de función. | `DeviceConfigViewModelTest` para owner/shared y todos los estados; `NavigationTest` de siete destinos; `CAP-config-owner`, `CAP-config-shared`, `CAP-config-delete-error`. |
| `password_modify` | Cambio de contraseñas. Estados: `device_mode`, `admin_mode`, `unlock_mode`, `init_mode`, `mismatch`, `empty`, `success`. Navega a `config`. | Cuatro modos y validación local sin guardar secretos. | `PasswordModifyScreen` con operación real por modo, limpieza de campos y error de credencial. | `PasswordViewModelTest` para modos/errores; `SecretStateTest`; `CAP-password-modify-mismatch`. |
| `unlock_setting` | Quick unlock y biometría. Estados: `quick_on`, `quick_off`, `bio_on`, `bio_off`, `bio_unavailable`, `warning`, `saved`. Navega a `config`. | Quick y biometría persistentes; la biometría se resuelve con la credencial del sistema y sin ella la acción pasa directa. | `UnlockSettingScreen` con credencial del sistema antes de abrir o disparar F1, y advertencia de seguridad, sin confundir autorización local con ack. | `BiometricTest` para disponible/no disponible/fallback; `CAP-unlock-setting-warning`, `CAP-unlock-setting-bio-unavailable`. |
| `device_info` | Información y renombrado. Estados: `read_only`, `editing`, `validation_error`, `success`, `failure`. UID no editable. Navega a `config`. | Edición, duplicado y guardado local. | `DeviceInfoScreen` con UID estable, guardado local/remoto y estado stale. | `DeviceInfoViewModelTest` para cinco estados; `CAP-device-info-validation`. |
| `about` | Versión, privacidad y acceso debug. Estados: `version`, `privacy`, `debug_trigger`. Recurso `logo.png`. Navega a `debug`. | Versión local, privacidad local y gesto. | `AboutScreen` con versión del paquete, proveedor y privacidad accesible; debug restringido. | `AboutTest` para versión/privacidad/gesto; `CAP-about`, `CAP-about-privacy`. |
| `image_pager` | Visor de imágenes. Estados: `white`, `black`, `page`, `share`, `delete_confirm`, `save_result`. Navega a `video_player`, `tab_gallery`. | Marcadores locales, modos y borrado; compartir/exportar no disponible. | `ImagePagerScreen` con media real, zoom accesible, URI grants mínimos y operaciones reales. | `MediaPagerTest` con imágenes de fixture; `FileProviderSecurityTest`; `CAP-image-pager-white`, `CAP-image-pager-black`, `CAP-image-pager-delete`. |
| `video_player` | Reproductor local. Estados: `playing`, `paused`, `stopped`, `muted`, `unmuted`, `seeking`, `tools_visible`, `tools_hidden`, `landscape`. Navega a `image_pager`. | Controles simulados sobre panel negro. | `VideoPlayerScreen` con player real, posición restaurada, audio y controles accesibles. | `VideoPlayerTest` para todos los estados y process death; `CAP-video-player-playing`, `CAP-video-player-seeking`, `CAP-video-player-landscape`. |
| `push_call` | Llamada entrante. Estados: `ringing`, `vibration`, `accept`, `refuse`, `auto_refuse`, `answered_elsewhere`. Recursos fondo y botones originales. Navega a `preview`, `main_tab`. | Llamada local con vibración, aceptar/rechazar y cierre a 30 s. | `IncomingCallScreen` con `callId`, acciones una sola vez, permisos y evento 20. | `CallViewModelTest` con reloj; `CallNavigationTest` anti duplicado; `CAP-push-call-ringing`, `CAP-push-call-answered-elsewhere`. Prueba FCM/hardware posterior. |
| `debug` | Diagnóstico. Estados: `debug_toggle`, `forwarding`, `connection_type`, `advanced`, `service_switch`, `send_log`, `token`. Navega a `loading`. `hidden_gesture`. | Escenarios fake, reset y ausencia de token. | `DebugScreen` solo en builds permitidas, diagnóstico por proveedor y exportación saneada. Nunca token visible. | `DebugAccessTest`, `LogRedactionTest`, confirmación para cambio/reset; `CAP-debug-redacted`. |
| `upgrade` | Firmware. Estados: `query`, `up_to_date`, `available`, `progress`, `success`, `failure`. Navega a `config`. | Consulta local up-to-date y upgrade deshabilitado. | `FirmwareScreen` con consulta y apply separados. Apply solo con evidencia, autenticidad y autorización de hardware. | `FirmwareViewModelTest` para seis estados sin flashear; `CAP-upgrade-available`, `CAP-upgrade-failure`. Prueba física fuera de tests normales. |
| `guide` | Guía paginada. Estados: `page_locks`, `page_cameras`, `page_playback`, `start`. Recursos `guide_display1/2/3`. Navega a `main_tab`. `legacy_no_inbound`. | Omitida porque loading siempre abre main. | `legacy_documented`: conservar las tres páginas y botón sin presentarla como onboarding actual. | `LegacyCoverageTest`. Si se reactiva: pager accesible y `CAP-guide-locks`, `CAP-guide-cameras`, `CAP-guide-playback`. |
| `add_method_legacy` | Selector AP/audio legacy. Estados: `ap`, `voice`, `switch_success`, `switch_failure`. Navega a `router`, `handle_wifi`. `legacy_no_inbound`. | Omitida como legacy. | `legacy_documented`: no duplicar el selector moderno sin revisar estas transiciones. | `LegacyCoverageTest`. Si se reactiva: `PairingNavigationTest` y `CAP-add-method-legacy`. |
| `router` | Router y contraseña. Estados: `loading`, `selected`, `password_hidden`, `password_visible`, `save_password`, `validation`, `new_mode`, `modify_mode`. Recursos niveles `wifi_1` a `wifi_4`. Navega a `wifi_progress`, `voice`, `config`. | Lista y formulario demo; guardado preferido sin secretos persistidos. | `RouterScreen` con redes reales, guardado opt in, secreto efímero y dos modos. | `RouterViewModelTest` para ocho estados; `WifiPermissionTest`; `CAP-router-new`, `CAP-router-modify`, `CAP-router-validation`. |
| `voice` | Alta acústica. Estados: `sending`, `waiting`, `volume_low`, `failure`, `success`. Recurso `device_add_sound_waver`. Navega a `device_bind`, `main_tab`. | Máquina local sin emitir audio. | `VoiceProvisioningScreen` con emisión real cancelable, volumen y progreso del adaptador. | `AudioProvisioningTest` para cinco estados; `CAP-voice-waiting`, `CAP-voice-volume-low`; prueba hardware posterior. |
| `wifi_progress` | Progreso AP. Estados: `connecting`, `sending`, `waiting`, `network_error`, `retry`, `failure`, `success`. Recurso `device_add_wifi_set`. Navega a `pairing_fail`, `device_bind`, `main_tab`, `handle_wifi`. | Etapas locales, fallo manual y navegación. | `WifiProvisioningScreen` con progreso real, 150 s si el perfil lo confirma, cancelación y `outcomeUnknown`. | `ProvisioningViewModelTest` para todos los estados/cancelación; `CAP-wifi-progress-waiting`, `CAP-wifi-progress-network-error`, `CAP-wifi-progress-unknown`. |
| `share_device` | Nueva invitación. Estados: `qr`, `link`, `expiration`, `copied`, `save_result`, `share_sheet`. QR 120 dp. Navega a `share_manage`. | Enlace local y QR/exportación no disponibles; no muestra QR falso. | `ShareDeviceScreen` con QR real, caducidad, copia y chooser; contenido sensible fuera de logs. | `InvitationViewModelTest`, decodificar QR generado, compartir/guardar real; `CAP-share-device-qr`, `CAP-share-device-expiration`. |
| `pairing_fail` | Error de alta. Estados: `error`, `main_only`. Back físico ignorado y salida a `main_tab`. | Error simulado y regreso a main. | `PairingFailScreen` con causa tipada, retry seguro y salida previsible sin dejar tarea activa. | `PairingFailureNavigationTest` para back y reintento; `CAP-pairing-fail`. |
| `share_manage` | Usuarios e invitaciones. Estados: `loading`, `empty`, `list`, `create`, `edit`, `delete`, `limit`, `error`. Navega a `share_device`, `config`. | Lista local, crear/borrar, límite y errores. | `ShareManageScreen` con CRUD real, permisos, rol owner y límite 100 cuando aplique. | `SharingViewModelTest` para ocho estados y permisos; `CAP-share-manage-empty`, `CAP-share-manage-list`, `CAP-share-manage-limit`. |
| `share_bind` | Aceptar invitación. Estados: `form`, `validation`, `accept`, `success`, `invalid`. UID/código bloqueados. Navega a `main_tab`. | Invitación local, duplicados y alta compartida. | `ShareBindScreen` con aceptación real, reaceptación y errores de caducidad/uso. | `InvitationAcceptTest` para cinco estados y reaceptación; `CAP-share-bind-form`, `CAP-share-bind-invalid`. |
| `tab_devices` | Lista y acciones. Estados: `empty`, `list`, `refresh`, `offline`, `password`, `biometric`, `action_result`. Recursos portada y estados online/offline. Navega a `add_type`, `preview`, `config`. | Loading/empty/list/offline y acciones demo deshabilitadas según permiso. | `DevicesScreen` con datos reales/stale, acciones por capacidad y ack diferenciado. | `DevicesViewModelTest` para siete estados; `ActionOutcomeTest`; `CAP-devices-empty`, `CAP-devices-list`, `CAP-devices-offline`, `CAP-devices-biometric`. |
| `tab_events` | Contenedor de eventos. Estados: `loading`, `empty`, `list`, `filters_hidden`, `warning`, `edit`, `delete`, `image`. Navega a `tab_event_list`. | Lista local, warning, edición y borrado; filtros ocultos documentados. | `EventsScreen` con una lista All por defecto y filtros solo si se habilitan; fotos reales y gaps visibles. | `EventsViewModelTest` para ocho estados; `CAP-events-empty`, `CAP-events-list`, `CAP-events-edit`, `CAP-events-image`. |
| `tab_event_list` | Lista de actividad. Estados: `empty_all`, `empty_call`, `empty_motion`, `refresh`, `load_more`, `edit`. Navega a `tab_events`. | Filas, refresh manual, páginas de 15 y edición local. | `EventList` con cursor real, tres vacíos y selección, sin transformar error en lista vacía. | `EventPaginationTest` para páginas/errores/gaps; `CAP-event-list-empty-motion`, `CAP-event-list-load-more`. |
| `tab_gallery` | Galería local. Estados: `empty`, `grid`, `edit`, `selected`, `select_all`, `share`, `save`, `delete`. Tres columnas y fechas. Navega a `image_pager`. | Grid local con marcadores y operaciones sobre metadata. | `GalleryScreen` con archivos reales, selección y acciones de URI seguras. | `GalleryViewModelTest` para ocho estados; `FileProviderSecurityTest`; `CAP-gallery-empty`, `CAP-gallery-grid`, `CAP-gallery-selected`. |
| `tab_settings` | Ajustes. Estados: `sound_on`, `sound_off`, `vibration_on`, `vibration_off`, `about`, `exit_confirm`, `hidden_options`. Navega a `about`. Auto rotate, canales y preview time ocultos en original. | Sonido/vibración persistentes, about, escenarios, reset y salida demo. | `SettingsScreen` con preferencias reales, proveedor y modo conexión. Opciones ocultas documentadas, debug/reset fuera de producción. | `SettingsViewModelTest` para siete estados; `CAP-settings`, `CAP-settings-exit-confirm`, `CAP-settings-hidden-options-review` solo documental. |

## 5. Modelo de estados de UI

### 5.1 Carga, vacío, error y offline

| Estado | Regla de presentación | Regla de datos |
|---|---|---|
| `loading` | Skeleton o indicador junto al área afectada, sin bloquear toda la app si hay contenido previo | Solo indica operación en curso, no éxito |
| `empty` | Mensaje y acción específica: añadir dispositivo, sin eventos, sin media o sin usuarios | Solo después de respuesta correcta que demuestre cero elementos |
| `error` | Causa accionable y retry permitido. Mantiene contenido previo si existe | Nunca convertir fallo en vacío ni sustituir por demo |
| `offline` | Distinguir teléfono sin red, proveedor desconectado y dispositivo offline | Conservar datos stale con hora de última actualización |
| `unsupported` | Función conocida pero no ofrecida por proveedor/dispositivo/rol | No reintentar ni presentar timeout |
| `unknown` | Función no negociada o sin evidencia | Permitir revalidar; no afirmar que el hardware carece de ella |
| `permission` | Explicar permiso Android o rol owner/shared y ofrecer acción válida | No confundir denegación de plataforma con unsupported cloud |
| `action-pending` | Mostrar acción y dispositivo, deshabilitar duplicados y permitir cancelar solo si es segura | Conservar `commandId` y estado tras rotación |
| `ack` | Texto según nivel: local, proveedor, dispositivo u observación física | Nunca convertir ack de transporte en efecto físico |
| `failure` | Error tipado, posible resultado desconocido y siguiente paso | No repetir automáticamente acciones físicas ambiguas |

`LoadState` debe poder llevar datos anteriores, `isStale`, `lastUpdated`, `error` y `refreshing`. Esto evita elegir entre una lista antigua y un indicador sin contexto.

### 5.2 Demo frente a conexión real

- Todas las superficies muestran `DEMO LOCAL` o el proveedor real y su estado, no solo las cuatro pestañas.
- El banner identifica si los datos son sintéticos, cache local o respuesta real. Un dato cacheado de stock no lleva etiqueta demo.
- El proveedor demo no se puede activar como recuperación de un error de stock. Cambiar a demo requiere una acción explícita y reinicia el ámbito de datos.
- Una captura para documentación debe incluir el banner cuando corresponda. No se recorta para ocultar el carácter simulado.
- Los ack demo usan texto específico, por ejemplo "reconocimiento local, sin acción física". Nunca se reutiliza "apertura correcta" para ese resultado.

### 5.3 Acciones físicas

1. Antes de enviar: comprobar capacidad, rol, estado de sesión y protección quick/biometría.
2. Durante: mostrar `action-pending` y evitar una segunda orden. No usar un spinner como sustituto del estado.
3. Con ack: mostrar el nivel exacto y su significado. `PROVIDER_ACCEPTED` no es "puerta abierta".
4. Con timeout: mostrar resultado desconocido y opciones de refrescar/revisar. No repetir por defecto.
5. Con evento posterior: correlacionar con `commandId` o llamada si existe y actualizar el mismo resultado, sin crear otro comando.
6. Con unsupported o unknown: conservar la función en el inventario y explicar el bloqueo. No borrar unlock, F1, llamada o preview del diseño.

## 6. Accesibilidad, tamaños, tema y navegación

### 6.1 Accesibilidad

- Objetivos táctiles mínimos de 48 dp, incluso iconos de preview, tabs, pager y selección.
- Texto normal con contraste mínimo 4.5:1 y texto grande/iconos esenciales 3:1. Medir los pares reales, no inferirlos de la paleta.
- No depender solo de rojo/verde o de los iconos online/offline. Añadir texto, forma y descripción para TalkBack.
- `contentDescription` contextual: "Abrir portal, dispositivo online", no "botón". Los iconos decorativos se excluyen del árbol de accesibilidad.
- `stateDescription` para switches, selección, grabación, habla, escucha y paneles. Acciones personalizadas para controles de video sin gestos obligatorios.
- Foco visible, orden lógico y anuncios de error/progreso sin lectura repetitiva por recomposición.
- Escalas de fuente 1.0, 1.3 y 2.0 sin recortes ni elipsis en mensajes críticos. Las filas crecen y las acciones pueden apilarse.
- Soporte ES/EN y RTL para formularios, chevrons, texto y navegación. Los códigos/UID mantienen dirección LTR cuando proceda.
- Respetar reducción de animación. Los progresos físicos no usan animación como única señal.
- Permisos con explicación previa y alternativa cuando sea posible. No pedir micrófono, cámara o ubicación antes de entrar en la función que los necesita.

### 6.2 Tamaños y orientación

Matriz mínima de validación:

| Clase | Configuración | Comprobación |
|---|---|---|
| Teléfono estrecho | 320 dp de ancho, font 1.0 y 2.0 | Formularios, errores, acciones y bottom sheets sin recorte |
| Teléfono habitual | 360 dp, font 1.0 y 1.3 | Todas las pantallas base y navegación |
| Pantalla media | 600 dp | Rail o layout adaptativo, preview y galería |
| Pantalla ancha | 840 dp | Paneles sin estirar tarjetas ni perder jerarquía |
| Landscape | Preview, video y pairing crítico | Insets, controles, teclado y back stack |
| Android con gestos y con botones | Insets y back | Sin acciones ocultas tras barras del sistema |

### 6.3 Tema claro y oscuro

El tema claro es la primera referencia funcional. El oscuro solo se habilita tras medir todos los pares de color, estados disabled, error, banners, mapas de selección y controles media. Preview y reproductor pueden permanecer oscuros en ambos temas. No se adopta un oscuro automático que reduzca contraste o haga invisibles iconos originales.

## 7. Pruebas que debe producir la implementación

### 7.1 Unitarias y de contrato

1. ViewModels con todos los estados de cada fila y relojes/dispatchers deterministas.
2. Reductores de estado para loading, empty, error, offline, unsupported, unknown, permission, pending, ack y failure.
3. Navegación y back stack para las 33 representaciones no legacy, las 71 aristas de referencia y deep links.
4. Cancelación de pairing, preview, llamada y eventos al salir, rotar o cambiar de cuenta.
5. Acciones duplicadas por doble clic, recomposición y process death. Una acción física ambigua genera cero retries.
6. Niveles de ack y mapeo de textos, incluidos fake y errores.
7. Permisos Android, permisos de invitación y diferencias owner/shared.
8. Páginas de 15, duplicados, gaps, selección y borrado.
9. Parser QR/invitación, OEM, caducidad y secretos ausentes de estado persistente y logs.
10. Persistencia y restauración de tab, scroll, pager, selección y posición de video.

### 7.2 Compose e instrumentación

- Tests semánticos por pantalla para acciones, estados, descripciones y deshabilitado.
- Tests de navegación real en emulador, incluidos process death, rotación, back gestual y llamada entrante.
- Escenarios demo y fake separados de integración stock. El nombre del test declara la fuente.
- Pruebas de accesibilidad con font 1.3 y 2.0, TalkBack manual en flujos críticos y contraste automatizado cuando sea fiable.
- Pruebas de cámara, galería, micrófono, biometría y notificaciones solo con permisos y fixtures controlados.
- Pruebas reales de archivos para captura, grabación, galería, compartir y guardar. Un registro `DemoMedia` no cuenta como archivo.
- Suites stock y hardware solo cuando exista autorización, sin incluir secretos ni acciones físicas en el test ordinario.

### 7.3 Capturas requeridas, todavía no producidas

La implementación posterior debe generar capturas reales de la app en ejecución. No se aceptan composiciones, recursos `start.png` o `guide_display*.png` como si fueran capturas.

| Lote | Contenido mínimo | Condición de aceptación |
|---|---|---|
| Base | Una captura de cada una de las 32 superficies visuales no legacy | 360 dp, font 1.0, tema claro, banner demo/proveedor visible |
| Estados críticos | Loading/error/empty/offline/unsupported/permission/pending/ack/failure en las pantallas donde aplican | Estado identificable y datos coherentes con su fuente |
| Preview | Uno/cuatro paneles, portrait/landscape, offline, unsupported y ack | Controles legibles y sin fotograma sintético presentado como real |
| Pairing | Permisos, QR, LAN vacío/resultados, AP/audio/progreso/fallo y bind | Cada etapa con origen real o demo explícito |
| Llamada | Ringing, pending, accepted/rejected y answered elsewhere | `callId` y resultado coherentes |
| Formularios | Validación, password visible/oculta sin secreto real, owner/shared | Sin datos personales ni credenciales |
| Accesibilidad | 320 dp font 2.0, 600/840 dp, landscape y RTL en flujos seleccionados | Sin solapes, recortes ni pérdida de acciones |
| Tema | Claro y, solo si se habilita, oscuro | Contraste documentado y controles visibles |

Cada captura tendrá nombre estable `CAP-<pantalla>-<estado>-<configuracion>.png` y una entrada de manifiesto con ruta, SHA-256, fecha, serial de emulador no personal, API Android, tamaño, font scale, idioma, tema, modo demo/real y commit. `cache` se valida con test, no con imagen. Las cinco legacy solo se capturan si se reactivan.

Comando de referencia para la fase posterior, con serial explícito y sin usar un teléfono físico:

```bash
adb -s <emulador> exec-out screencap -p > <ruta-de-captura>.png
```

## 8. Puerta de revisión funcional y APK final

La revisión posterior debe recorrer esta tabla y `analysis/architecture/provider-matrix.json` punto por punto. Para cada pantalla registrará navegación, estados, acciones, recursos, pruebas, captura y limitaciones. Para cada capacidad registrará API/wire, comportamiento, seguridad y hardware por separado.

Antes de entregar el APK se ejecutarán, como mínimo y según el entorno disponible:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
python3 analysis/architecture/verify_provider_architecture.py
ANDROID_SERIAL=<emulador> ./gradlew :app:connectedDebugAndroidTest
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Después se instalará ese APK en el emulador elegido y se repetirá un recorrido de humo: arranque, cuatro pestañas, alta, preview o unsupported, llamada, galería, configuración y navegación atrás. Se conservarán hash, ruta del APK, comandos, recuentos de tests y capturas reales. Compilar e instalar no certifica compatibilidad cloud ni hardware. Esta hoja no ha ejecutado esos comandos ni ha producido un APK.
