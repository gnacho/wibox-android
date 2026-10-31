# Inventario visual y de navegación de Wibox Fermax v2.2

Fuente: análisis estático de `com.fermax.wibox` v2.2, versionCode 18, SHA-256 `08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce`.

## 1. Alcance y ausencia de capturas

No hay capturas reales de la app en ejecución. No se ha instalado la app ni se ha usado un móvil o emulador. `start.png` es una pantalla de marca incluida como recurso y `guide_display1/2/3.png` son ilustraciones de guía. No se presentan como capturas de estados reales.

La especificación procede de layouts XML, drawables, valores, clases de navegación y recursos decodificados por JADX. Cada pantalla tiene evidencia en `analysis/apk/evidence/ui-code-evidence.md` y recursos con hash en `analysis/apk/resources/manifest.json`.

## 2. Tecnología y estructura

- Android Views y XML, no Jetpack Compose.
- AppCompat `Light.NoActionBar`, activities portrait salvo cambios manuales de preview/visor.
- ViewBinding y MVP. Fragments para las cuatro pestañas principales.
- `CommonTitleBar` para cabeceras y `MyGLSurfaceView` para vídeo.
- `values-night/colors.xml` está vacío. No hay tema oscuro propio. Preview y visor negro son estados puntuales.
- `FontHelper.Init(false, ...)` desactiva la fuente empaquetada. Se usa la familia sans-serif del sistema.

## 3. Sistema visual

### 3.1 Colores

Los valores se conservan en ARGB cuando el recurso los define así. Los alias se resuelven en notas cuando son evidentes.

| Recurso | Valor | Uso/nota |
|---|---|---|
| `appTitleColor` | `#78c9e7` | Marca |
| `app_main_tab_bg` | `@color/appTitleColor` | Recurso original |
| `bg_red` | `#e73e04` | Error |
| `bg_red_pressed` | `#cc330a` | Recurso original |
| `black_bg` | `#ff000000` | Recurso original |
| `black_transparent` | `#50000000` | Recurso original |
| `btn_upgrade_color_bg` | `#7e9aaf` | Recurso original |
| `clr_normal` | `#ffffff` | Recurso original |
| `clr_pressed` | `#c9c9c9` | Recurso original |
| `colorAccent` | `@android:color/darker_gray` | Recurso original |
| `colorPrimary` | `#78c9e7` | Marca y barras |
| `colorPrimaryDark` | `#78c9e7` | Recurso original |
| `color_bottom_tab_text` | `@color/white` | Tab activo |
| `color_bottom_tab_text_normal` | `#929292` | Tab inactivo |
| `common_sub_text` | `#ff888888` | Recurso original |
| `conditions_agree` | `@color/colorPrimary` | Recurso original |
| `conditions_disagree` | `@android:color/darker_gray` | Recurso original |
| `cut_line` | `@android:color/darker_gray` | Recurso original |
| `device_list_pressed` | `#e8e8e8` | Recurso original |
| `disable_color_bg` | `#dfddde` | Recurso original |
| `fm_fragment_text_normal` | `#7d7d7d` | Recurso original |
| `fm_fragment_text_selected` | `#00a3dd` | Evento seleccionado |
| `grey` | `#ff888888` | Recurso original |
| `guide_blue_tv` | `#00a3dd` | Recurso original |
| `icon_black` | `#88000000` | Recurso original |
| `main_ui_color_white` | `#f4f4f4` | Fondo general |
| `new_line_color` | `#ffcecece` | Recurso original |
| `preview_item_bg` | `@android:color/black` | Recurso original |
| `preview_item_select` | `@android:color/transparent` | Recurso original |
| `progree_bar_bg` | `#ff6700` | Recurso original |
| `progressBarBg` | `@android:color/darker_gray` | Recurso original |
| `snackBarBackground` | `#af78c9e7` | Recurso original |
| `snackBarText` | `@color/titleText` | Recurso original |
| `textColor` | `#ff4d4d4d` | Texto normal |
| `textColorGray` | `@android:color/darker_gray` | Recurso original |
| `titleText` | `#fff` | Texto de cabecera |
| `transparent` | `#00000000` | Recurso original |
| `white` | `#fff` | Recurso original |
| `window_selected` | `@android:color/transparent` | Recurso original |

Reglas principales:

- Cabecera y bottom tabs: `colorPrimary`/`appTitleColor` `#78c9e7`.
- Fondo general: `main_ui_color_white` `#f4f4f4`.
- Texto normal: `textColor` `#ff4d4d4d`; títulos de cabecera: `titleText` blanco.
- Bottom tab inactivo: `#929292`; activo: blanco.
- Eventos seleccionados: `fm_fragment_text_selected` `#00a3dd`.
- Error: `bg_red` `#e73e04`.
- Preview: negro, con iconos blancos y overlays.

### 3.2 Tipografía

| Uso | Especificación |
|---|---|
| Familia | Sans-serif del sistema. `fonts/alger.ttf` existe, pero no se aplica por defecto |
| Botón estándar | `text_normal_size`, 18sp, estilo normal, `textAllCaps=false` |
| Texto secundario | `text_small_size`, 15sp |
| Título | `title_text_size`, 20sp |
| Bottom tabs | `bottom_text_size`, 12sp |
| Guía | 30sp, bold |
| Nombre en About | 25sp |
| Auxiliares | 10sp, 13sp, 14sp, 15sp, 16sp y 18sp según layout |
| Estilos destacados | `item_alarm_header` y `file_grid_header` usan 16sp bold |

No se sustituye por Lucide en la descripción del original: los iconos reales son PNG y selectores XML.

### 3.3 Dimensiones

| Recurso | Valor |
|---|---|
| `activity_device_config_wifi_textsize` | `18sp` |
| `bottom_menu_height` | `130dp` |
| `bottom_text_size` | `12sp` |
| `btn_height` | `50dp` |
| `btn_height_large` | `60dp` |
| `btn_height_small` | `25dp` |
| `btn_small_width` | `120dp` |
| `btn_width` | `300dp` |
| `default_gap` | `8dp` |
| `devlist_height` | `55dp` |
| `fm_fragment_dial_info_line` | `3dp` |
| `fullscreen_img_size` | `55dp` |
| `logo_text_size` | `25sp` |
| `main_bottom_height` | `55dp` |
| `margin_large` | `20dp` |
| `margin_normal` | `10dp` |
| `margin_small` | `5dp` |
| `padding_large` | `30dp` |
| `padding_mini` | `3dp` |
| `padding_normal` | `15dp` |
| `padding_small` | `5dp` |
| `padding_small_normal` | `10dp` |
| `preview_item_icon_small` | `50dp` |
| `standard_width` | `45dp` |
| `talk_record_text_size` | `12sp` |
| `text_large_size` | `22sp` |
| `text_mini_size` | `10sp` |
| `text_nomal_size` | `16sp` |
| `text_normal_size` | `18sp` |
| `text_small_size` | `15sp` |
| `title_height` | `60dp` |
| `title_text_size` | `20sp` |

Medidas destacadas para la réplica:

- Botón `option_button`: match_parent de ancho, 50dp alto y márgenes laterales de 30dp.
- Bottom navigation: 55dp alto, cuatro elementos con peso 1 e iconos de 60x60 px en xhdpi.
- Portada de dispositivo: 200dp de alto, `centerCrop`.
- QR de invitación: 120x120 dp.
- Guía: imágenes de 640x960 px en xhdpi.
- `standard_width`: 45dp de margen lateral en textos explicativos.

### 3.4 Recursos por densidad

La tabla solo cuenta las imágenes representativas extraídas, no todo el APK. El manifiesto original tiene muchos más recursos de librerías y variantes.

| Densidad | Imágenes extraídas | Ejemplos |
|---|---:|---|
| `drawable-ldrtl-xhdpi` | 1 | icon_arrow_list_right.png 44x44 |
| `drawable-xhdpi` | 52 | backgroud_calling.png 720x1280, btn_accept.png 120x120, btn_preview_mode_normal.png 50x50, btn_refuse.png 120x120, device_add_sound_waver.png 564x180, device_add_wifi_set.png 564x180, device_cover_default.png 600x450, device_status_offline.png 48x48 |

## 4. Navegación global

- Entrada: `loading` -> `main_tab`.
- Pestañas: `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings`.
- Alta: `add_type` separa nueva y online; QR conduce a router/AP/audio o bind.
- Dispositivo: `tab_devices` -> `preview` o `config`.
- Ajustes: `config` enlaza información, contraseñas, desbloqueo, WiFi, firmware y usuarios.
- Galería: `tab_gallery` -> `image_pager` -> `video_player`.
- Push: evento 19 -> `push_call` -> `preview`; evento 20 cierra la llamada.
- Legacy: `guide`, `product_choose`, `add_method_legacy` y `wifi_list_legacy` no tienen entrada estática. `wifi_set_legacy` solo cuelga de la lista legacy.

El JSON contiene 71 aristas. La tabla siguiente no inventa destinos cuando no existe referencia.

## 5. Pantallas, estados y recursos

Convenciones:

- `layout` indica el archivo extraído. `cache` no tiene layout.
- Estados incluyen visibilidad, error, carga y variantes detectadas en código.
- `legacy_no_inbound` significa ausencia de referencias de entrada en la versión actual, no imposibilidad demostrada de abrir por intents externos no documentados.

| ID / pantalla | Layout | Estados | Navegación |
|---|---|---|---|
| `loading`<br>WI-BOX<br>Reach: `entry` | `activity_loading.xml` | `splash`: Fondo start a pantalla completa<br>`storage_permission`: Diálogo de almacenamiento y ajustes<br>`phone_permission`: Resultado READ_PHONE_STATE no bloquea el arranque<br>`startup_wait`: Espera de login o timeout de 10 s<br>`push_redirect`: Redirige evento 19 a llamada o preview | `main_tab`, `push_call`, `preview` |
| `cache`<br>Relay push<br><small>Actividad sin layout, excluida de recientes</small><br>Reach: `linked` | sin layout | `relay`: Copia extras a LoadingActivity<br>`finish`: Finaliza sin UI propia | `loading` |
| `main_tab`<br>Pestañas principales<br>Reach: `root` | `activity_main_tab.xml` | `devices_selected`: Pestaña Dispositivo activa por defecto<br>`events_selected`: Pestaña Eventos<br>`gallery_selected`: Pestaña Galería<br>`settings_selected`: Pestaña Ajustes<br>`terms`: Diálogo de términos no cancelable<br>`android_hint`: Aviso Android 10 con no mostrar más<br>`edit_mode`: Barra inferior oculta en edición | `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings` |
| `add_type`<br>Añadir Dispositivo<br>Reach: `linked` | `activity_da_type_choose.xml` | `new_device`: Opción nuevo dispositivo<br>`online_device`: Opción añadir por red | `product_intro`, `online_type` |
| `preview`<br>Autoencendido<br>Reach: `linked` | `activity_preview.xml` | `empty`: Hueco para añadir dispositivo<br>`connecting`: Conectando o buffering<br>`playing`: Reproduciendo con controles<br>`stopped`: Detenido<br>`refresh`: Error y botón reintentar<br>`password_error`: Credencial rechazada<br>`offline`: Dispositivo o canal offline<br>`one_window`: Un panel<br>`four_windows`: Cuatro paneles<br>`portrait`: Controles verticales<br>`landscape`: Controles horizontales<br>`listening`: Audio entrante activo<br>`talking`: Micrófono activo<br>`recording`: Grabación y tiempo visible<br>`snapshot`: Confirmación de captura<br>`unlock_progress`: Abriendo<br>`unlock_success`: Apertura correcta<br>`unlock_failure`: Fallo o timeout de apertura<br>`f1`: Estados F1 enviando/éxito/fallo<br>`timeout`: Fin del tiempo de preview<br>`no_permission`: Función no permitida | `config`, `password_modify` |
| `online_type`<br>Añadir por Red<br>Reach: `linked` | `activity_da_online_type_choose.xml` | `scan_online`: Buscar dispositivos online<br>`manual`: Alta manual | `online_devices`, `device_bind` |
| `product_choose`<br>Setup Wizard<br><small>No se encontró referencia de entrada en el código actual</small><br>Reach: `legacy_no_inbound` | `activity_da_new_produce_choose.xml` | `doorbell`: Wi-Fi Doorbell tipo 7410<br>`monitor`: Wi-Fi Monitor tipo 9440 | `product_intro` |
| `product_intro`<br>Ajustes Wi-Fi<br>Reach: `linked` | `activity_da_new_produce_introduce.xml` | `reset_instructions`: Instrucciones e imagen de reset<br>`location_permission`: Localización desactivada o denegada<br>`camera_permission`: Solicitud de cámara<br>`scan_error`: QR inválido<br>`wifi_switch_error`: Paso manual al AP | `scan`, `router`, `handle_wifi` |
| `handle_wifi`<br>Ajustes Wi-Fi<br>Reach: `linked` | `activity_da_new_handle_wifi.xml` | `instructions`: SSID y contraseña visibles con copiar<br>`copied`: Confirmación de copiado<br>`ap_mismatch`: Snackbar para abrir ajustes WiFi<br>`ap_connected`: Continúa a router o progreso | `router`, `wifi_progress` |
| `wifi_list_legacy`<br>Ajustes Wi-Fi<br><small>No se encontró referencia de entrada en el código actual</small><br>Reach: `legacy_no_inbound` | `activity_dm_wifi_list.xml` | `loading`: Refresco de redes<br>`list`: Lista de SSID<br>`error`: Aviso sin lista<br>`password`: Diálogo de contraseña<br>`connect_failure`: Error de conexión y ajustes<br>`success`: Finaliza al conectar | `wifi_set_legacy` |
| `wifi_set_legacy`<br>Ajustes Wi-Fi<br><small>Solo enlazada desde wifi_list_legacy, que tampoco tiene entrada</small><br>Reach: `legacy_no_inbound` | `activity_dm_wifi_set.xml` | `password_hidden`: Contraseña oculta<br>`password_visible`: Contraseña visible<br>`countdown`: Espera con contador<br>`connect_error`: Diálogo para abrir WiFi<br>`modify_success`: Vuelve a ajustes<br>`set_failure`: Error y posible reinicio de alta | `config`, `product_intro` |
| `device_bind`<br>Añadir Dispositivo<br>Reach: `linked` | `activity_da_device_bind.xml` | `qr_locked`: UID bloqueado por QR<br>`manual`: UID editable y escáner<br>`password_hidden`: Contraseña oculta<br>`password_visible`: Contraseña visible<br>`share_link`: Enlace compartido desactiva campos incompatibles<br>`validation_error`: Nombre/UID duplicado o vacío<br>`binding`: Progreso no cancelable<br>`success`: Configuración correcta<br>`invitation_error`: Invitación inválida o usada | `main_tab`, `scan` |
| `online_devices`<br>Añadir por Red<br>Reach: `linked` | `activity_da_online_device_list.xml` | `refreshing`: Búsqueda LAN<br>`results`: Lista de dispositivos<br>`empty`: Mensaje no device found<br>`error`: Finaliza refresco sin resultados | `device_bind` |
| `scan`<br>Escanee el código QR<br>Reach: `linked` | `activity_da_capture.xml` | `scanning`: Visor y marco QR<br>`pick_image`: Escaneo desde galería con progreso<br>`invalid`: Diálogo QR inválido y reintento<br>`result`: Devuelve resultado al llamador<br>`new_device`: Continúa alta nueva<br>`share`: Abre invitación | `product_intro`, `device_bind`, `share_bind` |
| `config`<br>Ajustes<br>Reach: `linked` | `activity_dm_config.xml` | `owner`: Todas las opciones del propietario<br>`shared`: Oculta contraseña, WiFi, alarmas, usuarios y firmware<br>`loading`: Consulta de info y alarmas<br>`alarm_on`: Llamada/movimiento activado<br>`alarm_off`: Llamada/movimiento desactivado<br>`upgrade_available`: Icono de nueva versión<br>`delete_confirm`: Confirmación de borrado<br>`delete_error`: Error de borrado<br>`bind_required`: Diálogo de contraseña | `device_info`, `password_modify`, `unlock_setting`, `router`, `upgrade`, `share_manage`, `main_tab` |
| `password_modify`<br>Modificar contraseña<br>Reach: `linked` | `activity_dm_password_modify.xml` | `device_mode`: Cambia contraseña de dispositivo<br>`admin_mode`: Cambia admin y desbloqueo<br>`unlock_mode`: Solo contraseña de apertura<br>`init_mode`: Inicialización sin campo antiguo<br>`mismatch`: Etiquetas en rojo<br>`empty`: Error de campos vacíos<br>`success`: Contraseña modificada | `config` |
| `unlock_setting`<br>Configuración de desbloqueo<br>Reach: `linked` | `activity_dm_unlock_setting.xml` | `quick_on`: Apertura rápida activa<br>`quick_off`: Apertura rápida inactiva<br>`bio_on`: Confirmación biométrica activa<br>`bio_off`: Confirmación biométrica inactiva<br>`bio_unavailable`: Fila biométrica oculta<br>`warning`: Diálogo explicativo con no mostrar más<br>`saved`: Guarda y finaliza | `config` |
| `device_info`<br>Información del dispositivo<br>Reach: `linked` | `activity_dm_device_info_edit.xml` | `read_only`: Nombre y UID no editables<br>`editing`: Nombre editable y botón guardar<br>`validation_error`: Nombre vacío o duplicado<br>`success`: Guardado local/remoto<br>`failure`: Error de modificación | `config` |
| `about`<br>Info<br>Reach: `linked` | `activity_me_about.xml` | `version`: Logo, WI-BOX y versión<br>`privacy`: Diálogo con HTML local<br>`debug_trigger`: Secuencia oculta abre TestInfo | `debug` |
| `image_pager`<br>Visor de galería<br>Reach: `linked` | `activity_fm_image_pager.xml` | `white`: Fondo blanco con título y acciones<br>`black`: Fondo negro sin título ni acciones<br>`page`: Indicador posición/total<br>`share`: Compartir elemento<br>`delete_confirm`: Confirmación de borrado<br>`save_result`: SnackBar de guardado | `video_player`, `tab_gallery` |
| `video_player`<br>Reproductor de vídeo<br>Reach: `linked` | `activity_fm_local_video_play.xml` | `playing`: Reproducción y botón pausa<br>`paused`: Overlay y botón play<br>`stopped`: Overlay visible<br>`muted`: Icono mute<br>`unmuted`: Icono sonido<br>`seeking`: Pausa temporal y seek<br>`tools_visible`: Controles visibles<br>`tools_hidden`: Oculta controles tras 5 s<br>`landscape`: Vídeo a pantalla completa | `image_pager` |
| `push_call`<br>llamada de placa de calle<br>Reach: `linked` | `activity_push_call.xml` | `ringing`: Nombre, aceptar y rechazar con tono<br>`vibration`: Vibración opcional<br>`accept`: Abre preview y registra aceptación<br>`refuse`: Cierra o envía a segundo plano<br>`auto_refuse`: Auto-rechazo a 30 s<br>`answered_elsewhere`: Mensaje y cierre por evento 20 | `preview`, `main_tab` |
| `debug`<br>Debug<br><small>Acceso por secuencia en About y código para opciones avanzadas</small><br>Reach: `hidden_gesture` | `activity_test_info.xml` | `debug_toggle`: Modo debug y aviso de reinicio<br>`forwarding`: Fuerza forwarding<br>`connection_type`: Muestra tipo de conexión<br>`advanced`: Opciones tras código<br>`service_switch`: Cambio de servidor y borrado local<br>`send_log`: Compartir log<br>`token`: Token disponible o null | `loading` |
| `upgrade`<br>Versión Firmware<br>Reach: `linked` | `activity_dm_upgrade.xml` | `query`: Consulta versión actual y disponible<br>`up_to_date`: Firmware actualizado<br>`available`: Diálogo de confirmación<br>`progress`: Progreso no cancelable<br>`success`: Diálogo de éxito y reinicio posterior<br>`failure`: Diálogo de fallo | `config` |
| `guide`<br>Guía de inicio<br><small>Declarada pero LoadingPresenter siempre abre MainTabActivity</small><br>Reach: `legacy_no_inbound` | `activity_start_guide.xml` | `page_locks`: Guía More locks<br>`page_cameras`: Guía More Cameras<br>`page_playback`: Guía Quick Playback<br>`start`: Botón Start Now | `main_tab` |
| `add_method_legacy`<br>Añadir Dispositivo<br><small>No se encontró referencia de entrada en el código actual</small><br>Reach: `legacy_no_inbound` | `activity_da_add_type.xml` | `ap`: AP add<br>`voice`: Sound waves to add<br>`switch_success`: Continúa al router<br>`switch_failure`: Paso manual WiFi | `router`, `handle_wifi` |
| `router`<br>Ajustes Wi-Fi<br>Reach: `linked` | `activity_da_router_info.xml` | `loading`: Refresco de redes<br>`selected`: SSID elegido con nivel wifi_1 a wifi_4<br>`password_hidden`: Contraseña oculta<br>`password_visible`: Contraseña visible<br>`save_password`: Guardar contraseña activo por defecto<br>`validation`: Contraseña corta<br>`new_mode`: Continúa AP o audio<br>`modify_mode`: Textos cambiar y notas adicionales | `wifi_progress`, `voice`, `config` |
| `voice`<br>Sound waves to add<br>Reach: `linked` | `activity_da_voice_set.xml` | `sending`: Envío acústico<br>`waiting`: Espera de dispositivo online<br>`volume_low`: Aviso de volumen insuficiente<br>`failure`: Fallo y botón retry<br>`success`: Continúa bind o main | `device_bind`, `main_tab` |
| `wifi_progress`<br>Ajustes Wi-Fi<br>Reach: `linked` | `activity_da_wifi_set.xml` | `connecting`: Conecta dispositivo<br>`sending`: Envía datos<br>`waiting`: Espera online con cuenta atrás<br>`network_error`: Aviso de red y restauración manual<br>`retry`: Botón retry<br>`failure`: Pantalla de pairing error<br>`success`: Continúa bind o main | `pairing_fail`, `device_bind`, `main_tab`, `handle_wifi` |
| `share_device`<br>Nuevo usuario<br>Reach: `linked` | `activity_share_device.xml` | `qr`: QR de 120 dp<br>`link`: Enlace textual<br>`expiration`: Disponible antes de fecha<br>`copied`: Enlace copiado<br>`save_result`: Resultado al guardar QR<br>`share_sheet`: Chooser Android | `share_manage` |
| `pairing_fail`<br>Error<br>Reach: `linked` | `activity_da_device_config_fail.xml` | `error`: Explicación de pairing fallido<br>`main_only`: Solo botón siguiente, back físico ignorado | `main_tab` |
| `share_manage`<br>Usuarios<br>Reach: `linked` | `activity_share_manage.xml` | `loading`: Refresco de invitaciones<br>`empty`: No Users<br>`list`: Usuarios con permisos<br>`create`: Nueva invitación con permisos por defecto<br>`edit`: Editar nombre/permisos<br>`delete`: Confirmación de borrado<br>`limit`: Botón añadir oculto a 100<br>`error`: No autorizado o fallo | `share_device`, `config` |
| `share_bind`<br>Añadir Dispositivo<br>Reach: `linked` | `activity_share_bind.xml` | `form`: UID/código bloqueados y nombre editable<br>`validation`: Nombre vacío o duplicado<br>`accept`: Aceptación con progreso<br>`success`: Vuelve a main<br>`invalid`: Invitación inválida o usada | `main_tab` |
| `tab_devices`<br>Dispositivo<br>Reach: `main_tab` | `fragment_device_list.xml` | `empty`: Botón central añadir<br>`list`: Tarjetas con portada y acciones<br>`refresh`: SwipeRefresh y estado online<br>`offline`: Estado offline<br>`password`: Diálogo de apertura/F1<br>`biometric`: Verificación biométrica<br>`action_result`: Toast éxito/fallo | `add_type`, `preview`, `config` |
| `tab_events`<br>Eventos<br>Reach: `main_tab` | `fragment_message_list.xml` | `loading`: Carga inicial y refresh<br>`empty`: Texto de ausencia de eventos<br>`list`: Una sola lista All ACTIVITY<br>`filters_hidden`: RINGS/MOTION ocultos<br>`warning`: Aviso sobre fotos y firmware<br>`edit`: Selección y barra inferior<br>`delete`: Confirmación de borrado<br>`image`: Diálogo de foto | `tab_event_list` |
| `tab_event_list`<br>Lista de actividad<br>Reach: `nested_fragment` | `fragment_main_message_type.xml` | `empty_all`: Mensaje de llamadas del hogar<br>`empty_call`: Mensaje vacío de llamadas<br>`empty_motion`: Mensaje e icono de movimiento<br>`refresh`: Tirar para refrescar<br>`load_more`: Carga más a partir de 15<br>`edit`: Selección múltiple | `tab_events` |
| `tab_gallery`<br>Galería<br>Reach: `main_tab` | `fragment_flie.xml` | `empty`: Texto sin fotos ni vídeos<br>`grid`: Tres columnas agrupadas por fecha<br>`edit`: Barra de selección y pestañas oculta<br>`selected`: Contador en título<br>`select_all`: Seleccionar todo<br>`share`: Compartir selección<br>`save`: Guardar selección y resultado<br>`delete`: Confirmación de borrado | `image_pager` |
| `tab_settings`<br>Ajustes<br>Reach: `main_tab` | `fragment_me.xml` | `sound_on`: Sonido activado<br>`sound_off`: Sonido desactivado<br>`vibration_on`: Vibración activada<br>`vibration_off`: Vibración desactivada<br>`about`: Abre información<br>`exit_confirm`: Diálogo de salida<br>`hidden_options`: Auto rotate, canales y preview time ocultos | `about` |

## 6. Componentes y estados transversales

| Componente | Recursos/estados |
|---|---|
| Cabecera | `publico_include_title.xml`, izquierda/centro/derecha, sin línea inferior, modo light |
| Bottom tabs | `main_btn_device`, `main_btn_call_log`, `main_btn_file`, `main_btn_setting` y variantes `_select` |
| Tarjeta dispositivo | `item_device.xml`: estado 48x48, nombre, portada 600x450, settings, conserjería, F1 y lock |
| Tarjeta compartida | `item_device_share.xml`: CardView, permisos preview/playback/alarm/unlock/F1, editar/cerrar/abrir |
| Preview | `preview_item_play.xml`: añadir, play, refresh, progreso, velocidad y texto de estado |
| Controles portrait | `activity_preview.xml`: play, sonido, grabación, captura, unlock/F1, canal, modo y habla |
| Controles landscape | `multi_full_screen_operate_bar.xml`: equivalentes horizontales y unlock/F1 |
| Teclado apertura | `cust_keyboard.xml` y `cust_keyboard_full_screen.xml`: dígitos, borrar, mostrar, lock1/2/F1 |
| Diálogos | `custom_dialog`, `custom_dialog_with_never_show`, `custom_conditions_dialog`, `device_version_dialog`, `new_app_version_dialog` |
| Progreso | `publico_custom_progress`, `publico_custom_progress_dlg`, `custom_horizontal_progressbar` |
| Listas | `item_message`, `item_new_device`, `item_lan_search_device`, `item_wifi_info` |
| Refresh | `xrefreshview_header/footer`, textos arrastrar/soltar/refrescando/más |
| Notificación llamada | `custom_notification.xml` y `custom_notification_big.xml`, aceptar/rechazar |
| Estados selector | `btn_switch_on/off`, `selector_btn_unlock*`, `selector_f1*`, `main_selector_*`, `publico_selector_*` |

## 7. Cadenas ES/EN

- Inglés base: 282 cadenas propias (`key_*`, `app_name`, `co_consent_info`).
- Español: 281. Falta `key_is_phone_calling`, por lo que se usa el fallback inglés.
- 59 claves tienen exactamente el mismo texto en ambos recursos. Entre ellas hay cadenas sin traducir y también marcas o abreviaturas, como `Motion Alerts`, `RINGS`, `All ACTIVITY`, `Sound waves to add`, `Setup Wizard`, `WI-BOX` y `UID`.
- `values-en` solo añade cadenas de librerías. Las cadenas propias inglesas están en `values/strings.xml`.

| Clave | Inglés | Español | Observación |
|---|---|---|---|
| `app_name` | WI-BOX | WI-BOX | Marca |
| `key_device` | Device | Dispositivo | Tab y título |
| `key_device_add` | Add Device | Añadir Dispositivo | Alta |
| `key_call_logs` | Events | Eventos | Tab |
| `key_album` | Gallery | Galería | Tab |
| `key_setting` | Settings | Ajustes | Tab |
| `key_unlock` | Unlock | Abrir | Acción |
| `key_alarm_title_all` | All ACTIVITY | All ACTIVITY | Sin traducir |
| `key_alarm_title_call` | RINGS | RINGS | Sin traducir |
| `key_alarm_title_motion` | MOTION | MOTION | Sin traducir |
| `key_is_phone_calling` | During the call, please try again later! | Falta | Fallback inglés |

Los archivos completos filtrados están en `analysis/apk/resources/values/strings-en.xml` y `strings-es.xml`. No incluyen la configuración sensible de Firebase.

## 8. Recursos y trazabilidad

`analysis/apk/resources` contiene 189 archivos, aproximadamente 1,7 MB:

- 74 layouts propios.
- 53 imágenes representativas con dimensiones y formato medidos por ImageMagick.
- Selectores XML para estados normales, pulsados, seleccionados y on/off.
- Valores filtrados de colores, dimensiones, estilos y cadenas.
- `manifest.json` con SHA-256 y bytes por archivo.

Excluidos deliberadamente: APK completo, claves privadas, BKS, fuentes propietarias, bibliotecas nativas, layouts de terceros y el árbol completo de JADX.

## 9. Prioridades de réplica

1. Mantener la navegación de cuatro pestañas y la jerarquía real, no convertirla en un dashboard nuevo.
2. Reproducir los estados vacío/cargando/error/offline junto a cada pantalla, no solo el camino feliz.
3. Implementar alta AP/audio/LAN/invitación y sus estados de permiso y progreso antes de simplificar el pairing.
4. Conservar preview portrait/landscape, uno/cuatro paneles y controles de audio, grabación, apertura y F1.
5. Respetar la paleta y tipografía del original, pero tratar las cadenas sin traducir como defectos a corregir conscientemente, no como requisito.
6. No replicar debilidades: TLS permisivo, logging de secretos, admin/admin, URLs con contraseña ni almacenamiento de claves en assets.
7. Mantener `legacy_no_inbound` fuera del flujo principal hasta decidir si se recuperan esas pantallas.
