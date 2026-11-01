# Arquitectura de proveedores para Wibox

## 1. Alcance, prioridad y nivel de certeza

Este documento es una propuesta de arquitectura, no una implementación ni una certificación de compatibilidad. La prioridad es entender y conservar el comportamiento de QVCloud/Quvii tal como aparece en Wibox Fermax v2.2, versionCode 18. La UI puede modernizarse, pero no debe borrar funciones para que quepan en un denominador común con MQTT o con una nube propia.

Fuentes primarias revisadas: `analysis/integration-contract.json`, `analysis/apk/inventory.json`, `analysis/apk/ui-inventory.json`, evidencias E001-E075, ejecución REA descrita en `docs/rea-analysis.md`, `docs/protocol.md` y la API pública actual de `core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud`. La evidencia de cable disponible es estática y tiene pruebas offline para una parte del cliente. No se ha usado hardware, una cuenta real ni tráfico nuevo en esta hoja.

Regla de lectura:

- **HECHO**: se observa en el APK, en una evidencia citada o en el código actual.
- **PROPUESTA**: diseño que todavía debe implementarse y probarse.
- **DESCONOCIDO**: falta contrato, ejecución autorizada o prueba con hardware.
- Un fake, una suscripción HTTP aceptada o un publish MQTT no demuestra una acción física ni compatibilidad con stock.
- No se afirma compatibilidad total ni un porcentaje global. Cada capacidad tiene cuatro puertas independientes: API/wire, comportamiento, seguridad y hardware.

La matriz normativa de capacidades es `analysis/architecture/provider-matrix.json`. Contiene 64 capacidades y sus estados actuales. Los proveedores alternativos son diseño futuro, no implementaciones existentes.

## 2. Diagnóstico de acoplamientos actuales

### 2.1 En la API `core:cloud`

| Acoplamiento observado | Consecuencia | Corrección propuesta |
|---|---|---|
| `WiboxCloudConfig` contiene `baseUrl`, `clientId`, `regionId`, `clientType`, `oemId`, `appId` y `clientFlag` | La configuración pública tiene forma de QVCloud y no representa otros perfiles | Separar configuración legacy de `ProviderConfig`, con un `ProviderProfile` cerrado y secretos fuera de la configuración pública |
| `CloudCredentials` y `CloudSession` exponen usuario, password, access token y refresh token | La sesión no es opaca para los consumidores y solo cubre un esquema OAuth | Entregar a dominio/UI un `SessionHandle` opaco. Los tokens quedan dentro del adaptador y de su almacén de secretos |
| `TokenStore` tiene una única sesión sin namespace | No distingue proveedor, cuenta, entorno ni instalación | Mantenerlo para legacy y añadir `SecretStore` con clave compuesta y escritura atómica |
| `CloudCapabilities` es un `Set<CloudCapability>` global | No expresa negociación, ámbito por dispositivo, soporte desconocido ni evidencia | Sustituirlo en el puerto nuevo por `CapabilitySnapshot` con soporte, ámbito, procedencia, evidencia y revalidación |
| `CloudDevice` solo tiene `id` | No conserva nombre, modelo, rol, canales, permisos, presencia ni extensiones | Introducir `DeviceSnapshot` provider-neutral y capacidades por dispositivo |
| `discoverServices(): Map<CloudService, String>` | Si la UI consumiera el resultado conocería endpoints y servicios concretos | Resolver servicios dentro de `stock-qvcloud`. La UI recibe solo estados de preparación y errores de dominio |
| `CloudEventType` solo distingue movimiento, llamada entrante y llamada atendida | Faltan huecos, estado de conexión, eventos desconocidos, deduplicación y cursors | Usar `EventEnvelope` con identidad, cursor, gap, procedencia, tiempo crudo y payload desconocido conservado |
| `CloudActionAcknowledgement(providerCode, data)` | No distingue aceptación local, aceptación cloud, ack del dispositivo y efecto físico | Introducir `ActionOutcome` con correlación, nivel de ack, ambigüedad y observación posterior |
| No existen operaciones de pairing, invitaciones, configuración, firmware, preview o llamada | La interfaz actual no cubre gran parte del APK | Añadir subpuertos opcionales sin convertir esas funciones en métodos vacíos obligatorios |
| `RealWiboxCloud` implementa bien el aislamiento Ktor interno, refresh single-flight, margen de cinco minutos, TLS estricto y no retry | Es una base útil, pero solo para OAuth, Shadow y parser offline | Reutilizar esos componentes dentro del adaptador stock, sin reescribirlos ni anunciar más cobertura de la demostrada |

### 2.2 En la app Android

| Acoplamiento observado | Evidencia actual | Riesgo y corrección |
|---|---|---|
| Repositorio fijado al fake | `WiboxRepository` declara `val cloud: FakeWiboxCloud` y su única fábrica es `local()` | No se puede seleccionar stock sin cambiar el repositorio. Introducir `WiboxRepository` sobre `WiboxProvider` y una fábrica explícita por perfil |
| Modelos demo en estado y ViewModels | `AppUiState`, `AddDeviceViewModel` y `PreviewViewModel` consumen `DemoSnapshot`, `DemoDevice`, `DemoSettings` y `DemoScenario` | La UI no puede distinguir dominio real de fixture. Crear modelos de presentación neutros y mantener `Demo*` solo en el adaptador demo |
| Acceso directo al fake desde el ViewModel | `AppViewModel` llama a `repository.cloud.observeEvents()` | El evento debe venir del repositorio neutral, con error y gap modelados |
| Éxito de acción ligado al fake | Cualquier ack termina en `LOCAL_ACTION_ACKNOWLEDGED`; el fake devuelve `local-demo-ack-no-physical-action` | El texto actual es honesto para demo, pero no sirve para stock. El resultado debe conservar el nivel de ack y nunca traducirlo genéricamente a puerta abierta |
| Pairing y preview simulados por temporizadores | `startProvisioning()` y `load()` completan estados locales; `PLAYING_SIMULATED` sí está etiquetado | Mantener esos estados solo bajo un provider demo. En producción, progreso y reproducción deben proceder de eventos reales |
| Datos de alta incompletos | `CloudDevice` solo aporta ID y el ViewModel deduce el modelo si el ID contiene `9440` | El adaptador debe devolver metadatos tipados. La UI no debe inferir modelo, endpoint o capacidad desde un identificador |
| Tres ViewModels compartidos por todo el `NavHost` | `WiboxApp` crea `AppViewModel`, `AddDeviceViewModel` y `PreviewViewModel` para todas las rutas | Hay estado de larga duración mezclado entre flujos. Separar sesión, lista, alta, dispositivo, media y ajustes con ViewModels por grafo o pantalla |
| Carga inicial artificial | `LoadingScreen` espera 450 ms y navega | En real debe representar restauración, preparación y decisión de sesión. En demo puede conservar una portada breve identificada |
| Persistencia demo tolerante a corrupción | `LocalDemoStore` convierte fallo de parseo en `null` y el repositorio vuelve a fixtures | No reutilizar ese patrón para datos reales. Un snapshot dañado debe producir error recuperable y copia, no pérdida silenciosa ni sustitución por demo |
| Secretos en estado de formulario | `authCode` y `wifiPassword` viven en `AddDeviceUiState` | Son necesarios durante el alta, pero no deben persistirse, registrarse ni aparecer en snapshots de UI. Usar estado efímero acotado y limpieza al terminar |

La app sí acierta en dos límites que deben conservarse: no importa Ktor ni DTOs internos y el modo demo está etiquetado. La modernización no debe eliminar esas protecciones.

## 3. Frontera provider-neutral sin recortar QVCloud

### 3.1 Módulos conceptuales

| Pieza | Responsabilidad | Lo que no puede contener |
|---|---|---|
| `core:provider-api` | Modelos, capacidades, sesiones opacas, eventos, acciones, errores y subpuertos | URLs, topics, headers, Ktor, Paho, DTOs, clases Android de media |
| `provider:stock-qvcloud` | Registro opaco, OAuth, discovery regional, user auth, Shadow, push, P2P, CGI y pairing stock | Modelos `Demo*`, reglas de Compose ni fallback a otro proveedor |
| `provider:mqtt` futuro | Perfil MQTT versionado para firmware o broker que se defina después | Topics QVCloud inventados, promesas de media ni reutilización del MQTT duhow como stock |
| `provider:self-hosted` futuro | API de una nube propia versionada, cuentas, dispositivos, eventos y acciones | Suplantación de QVCloud ni afirmaciones de paridad sin suite propia |
| `provider:demo` | Fixtures deterministas para desarrollo y tests | Selección en producción, credenciales reales o evidencia de compatibilidad |
| `app` y `core:presentation` | Casos de uso, estado de UI, navegación y render por capacidades | Endpoints, topics, tokens, URLs `quii`, parsers wire o elección implícita de transporte |

El proveedor demo es necesario para pruebas, pero no es uno de los tres proveedores de despliegue descritos en el contrato. Debe vivir en un source set o módulo claramente separado y llevar `connectionKind = DEMO` en todos sus snapshots.

### 3.2 Puerto principal y subpuertos

`WiboxProvider` es la entrada nueva. Su contrato obligatorio se limita a identidad, capacidades, lifecycle, sesión, errores y cierre. El resto se obtiene mediante subpuertos opcionales. Una función ausente devuelve `Unsupported` o `Unknown`, nunca un resultado vacío que parezca éxito.

| Subpuerto | Operaciones de dominio | Preserva en stock |
|---|---|---|
| `SessionPort` | Preparar, restaurar, autenticar con material efímero, refrescar si aplica, cerrar sesión local, revocar si está demostrado | Registro opaco, login posterior, OAuth password/refresh y credenciales dinámicas sin inventar login visible |
| `DevicePort` | Observar y refrescar dispositivos, leer detalle, presencia y Shadow, consultar campos concretos | Lista local sincronizada con `getDevList`, credenciales dinámicas, estados desconocidos y cuatro operaciones Shadow |
| `DiscoveryPort` | Descubrir candidatos LAN o preparar discovery regional interno | `QvLocationManager`/P2P para servicios y búsqueda LAN para alta, sin exponer endpoints |
| `PairingPort` | Parsear QR, provisionar por AP/audio/LAN/manual, observar progreso, cancelar, enlazar | Campos QR, 150 s, polling, reintentos limitados por etapa y errores sin repetir físicamente a ciegas |
| `EventPort` | Historial, páginas, fotos, borrado, eventos en vivo, push y gaps | Alarmas 2/19/20, páginas de 15, FCM, MQTT shadow desactivado por defecto y foto asociada |
| `ActionPort` | Ejecutar unlock, F1, guardia y acciones de llamada con correlación | Permisos, biometría local opcional, timeout, ack y resultado ambiguo |
| `MediaCallPort` | Abrir/cerrar preview, paneles, canal, calidad, escucha, habla, captura, grabación, aceptar/rechazar llamada | P2P real, uno/cuatro paneles, modos, 90 s, reconexión y llamada entrante sin sustituirlo por un panel simulado |
| `InvitationPort` | Listar, crear, editar, revocar y aceptar invitaciones | QR/enlace, caducidad, seis permisos y límite de 100 |
| `DeviceConfigPort` | Renombrar, borrar, contraseñas, alarmas, WiFi, hora, F1, FPS, modo intercom y protección de apertura | Diferencias owner/shared y configuración específica que un puerto mínimo eliminaría |
| `MaintenancePort` | Consultar firmware, aplicar actualización, consultar actualización de app y diagnóstico permitido | Progreso y fallos sin convertir una consulta en actualización ejecutada |

Los subpuertos pueden tener implementaciones parciales. Por ejemplo, `stock-qvcloud` puede ofrecer `DevicePort` con Shadow y todavía no ofrecer lista de dispositivos. La capacidad individual, no la presencia de la interfaz, decide si una operación está disponible.

## 4. Capacidades negociadas

### 4.1 Dos ejes que no se deben mezclar

**Soporte de diseño**:

- `required`: obligatorio para el contrato común o para la paridad stock declarada.
- `optional`: el proveedor puede ofrecerlo y la UI se adapta.
- `unsupported`: se sabe que ese proveedor no lo ofrece. Debe mostrarse como no disponible, no como error de red.
- `unknown`: todavía no se ha negociado o no hay evidencia. Debe mostrarse como no comprobado y permite revalidación.

**Evidencia de implementación**:

- `implemented_contract`: existe implementación y prueba offline de contrato.
- `implemented_offline`: existe lógica local o parser con prueba, sin llamada real.
- `local_only`: solo demo o almacenamiento local, sin compatibilidad provider.
- `static_evidence_only`: el APK demuestra una construcción o flujo, pero el clon no lo implementa.
- `unsupported`: el cliente real lo devuelve explícitamente como no soportado.
- `unknown`: no hay base suficiente ni para implementar ni para negar la función.
- `design_required`: es obligación de la arquitectura futura y aún no está terminada.
- `not_implemented`: el proveedor alternativo es solo diseño futuro.

### 4.2 Contenido de `CapabilitySnapshot`

Cada entrada debe incluir:

1. `CapabilityKey` estable y versionado, con extensiones namespaced cuando no exista equivalente común.
2. Soporte `required`, `optional`, `unsupported` o `unknown` y evidencia actual separada.
3. Ámbito `provider`, `account`, `device`, `channel` o `session`.
4. `DeviceRef` y canal cuando aplique. Una capacidad de una cerradura no se hereda a todos los dispositivos.
5. Origen: declaración local, negociación de cuenta, Shadow, permiso de invitación, observación runtime o prueba de contrato.
6. `evidenceLevel`, versión del perfil y momento de revalidación. Un dato estático no se transforma en runtime por pasar el tiempo.
7. `limitations`: ausencia de ack físico, falta de media, permiso denegado o semántica desconocida.
8. Extensiones tipadas de dominio. No se transporta un DTO genérico a la UI.

La negociación se realiza al preparar sesión, al refrescar dispositivos, al cambiar permisos, tras ciertos errores de protocolo y al reconectar. `unknown` no se guarda como `unsupported`. La UI puede deshabilitar una acción mientras se comprueba, pero debe distinguir ambos textos.

## 5. Modelos y semántica del puerto

### 5.1 Sesión opaca y credenciales

`SessionHandle` contiene proveedor, instalación, alias de cuenta, entorno, estado y un identificador opaco. No contiene tokens, password, `dataEncodeKey`, Verify Code, URLs con userinfo ni secretos de dispositivo.

`CredentialMaterial` es efímero, de un solo propósito y nunca forma parte de `StateFlow`, `SavedState`, logs, analytics, fixtures o snapshots. La autenticación password actual queda dentro de stock. El almacén nuevo usa la clave `(installationId, providerId, accountId, environment)` y separa:

- Secretos OAuth y de cuenta.
- Credenciales dinámicas de dispositivo y claves de media.
- WiFi solo si el usuario opta explícitamente por guardarla.
- Configuración no secreta y caché, en otro almacén.

La escritura es atómica y cifrada con una clave protegida por Android Keystore. El borrado de sesión local no implica revocación remota. `logout` permanece desconocido hasta conocer el contrato de revocación y los efectos sobre la cuenta opaca.

### 5.2 Dispositivos

`DeviceSnapshot` incluye `DeviceRef`, nombre, modelo declarado, rol owner/shared, permisos, canales, presencia `online/offline/unknown`, última observación, firmware conocido, capacidades y metadatos no secretos. No se infiere el modelo desde el ID.

La lista debe conservar el origen `cache`, `cloud`, `local` o `merged`, y la frescura de cada fuente. Un dispositivo ausente en Shadow no se marca offline. Un entero `Device_state` se conserva sin traducir hasta que su semántica esté probada.

### 5.3 Shadow

`ShadowSnapshot` conserva campos solicitados, campos devueltos, enteros originales, `path`, timestamp, secuencia si existe, frescura y campos desconocidos. `get`, `multi`, `subscribe` y `unsubscribe` son capacidades distintas:

- `get` y `multi` demuestran consulta HTTP, no presencia en tiempo real.
- El ack de `subscribe` no demuestra apertura de MQTT ni entrega posterior.
- `unsubscribe` con `ACCOUNT_LOGOUT` o `DEVICE_UNBIND` no equivale a revocar OAuth ni desvincular físicamente.

Los campos desconocidos se conservan en un sobre de dominio seguro, no como DTO de red. Las extensiones conocidas reciben nombres tipados y las desconocidas no se descartan.

### 5.4 Eventos y tiempo real

`EventEnvelope` contiene ID de proveedor, tipo, dispositivo, canal, tiempo crudo y parseado opcional, recepción local, adjuntos por referencia, campos desconocidos, cursor, procedencia FCM/MQTT/HTTP/local y marca de duplicado.

El stream emite además `Connected`, `Reconnecting`, `ResyncRequired` y `Gap`. Debe existir deduplicación por `(provider, account, eventId)` y política de cursor persistida. QoS 1 puede duplicar y retained puede ser antiguo. Ninguno garantiza exactamente una vez ni orden total.

El parser offline de FCM es una capacidad separada del registro de token, recepción en background, historial y eventos en vivo. `AlarmTime` permanece crudo hasta fijar formato y zona.

### 5.5 Pairing y provisioning

`PairingRequest` identifica método QR, AP, audio, LAN, manual o invitación, datos no secretos y material efímero. El QR de dispositivo conserva los cuatro campos observados `apName`, `uid`, `authCode` y `model`, sin reemplazar `authCode` por una credencial de UI. `PairingProgress` usa etapas `connecting`, `sending`, `waiting`, `binding`, `ready`, `failed`, `cancelled` y `permissionRequired`, con reintento manual acotado por etapa.

La cancelación detiene trabajos del adaptador y cierra recursos, pero no afirma deshacer una escritura ya enviada. El adaptador informa `outcomeUnknown` cuando el dispositivo pudo recibir la configuración. El reintento de bind solo se automatiza para los códigos y modos demostrados. Nunca se reutiliza el retry genérico de HTTP para provisioning físico.

### 5.6 Acciones y acknowledgements

`ActionCommand` incluye ID de cliente, dispositivo, canal, tipo, parámetros y política de reintento. El ID sirve para correlación y deduplicación solo si el proveedor lo admite; no se presenta como idempotencia garantizada.

Niveles de `ActionOutcome`:

| Nivel | Significado permitido | Lo que no permite afirmar |
|---|---|---|
| `LOCAL_ACCEPTED` | La app o fake aceptó la petición | Que salió del teléfono o llegó al dispositivo |
| `PROVIDER_ACCEPTED` | El cloud aceptó el comando | Que el dispositivo lo ejecutó |
| `DEVICE_ACKNOWLEDGED` | El dispositivo confirmó recepción o ejecución según contrato | Que la puerta se abrió físicamente, salvo semántica explícita |
| `PHYSICAL_OBSERVED` | Un evento independiente y validado confirma el efecto | Otras acciones o estados no observados |
| `REJECTED` | Existe rechazo tipado | Una causa física no comunicada |
| `UNKNOWN_OUTCOME` | Timeout, desconexión o respuesta ambigua | Éxito o fracaso definitivo |

La UI solo dice "puerta abierta" con `PHYSICAL_OBSERVED` y semántica verificada. Un ack de apertura puede mostrar "comando de apertura confirmado por dispositivo" si ese es su significado probado. Las acciones ambiguas no se repiten automáticamente. Se ofrece refrescar estado o revisar el dispositivo antes de una nueva orden manual.

### 5.7 Llamada, preview y P2P

`MediaSessionHandle` es opaco y su ciclo incluye `preparing`, `connecting`, `playing`, `buffering`, `stopped`, `offline`, `unsupported`, `failed` y `closed`. Los controles se negocian por dispositivo y canal: uno/cuatro paneles, calidad, escucha, habla, captura y grabación.

El puerto no expone `quii://`, puertos mapeados, passwords, JNI ni `dataEncodeKey`. El adaptador media administra túneles y secretos. La UI consume estados y frames mediante una abstracción Android de render, no un DTO cloud.

Aceptar una llamada y abrir preview son operaciones relacionadas pero distintas. Rechazar, auto-rechazo a 30 s y evento 20 de llamada atendida en otro terminal necesitan correlación de llamada. Un preview local nunca cuenta como llamada stock atendida.

### 5.8 Invitaciones

El modelo conserva QR, enlace, caducidad, owner, dispositivo, permisos `preview`, `playback`, `alarm`, `unlock`, `F1` y `callGuard`, más el límite de 100 cuando aplique. El contrato de enlace incluye `InviteCode`, `OwnerId`, `OemId`, `AppVersion`, `DeviceId`, `From` y `AppId`. Crear, editar, revocar, aceptar y volver a aceptar son operaciones separadas.

La compatibilidad OEM no se resuelve copiando la comparación permisiva del APK. Se fija una regla exacta tras prueba con invitaciones válidas, inválidas, usadas, caducadas y de otro OEM. Los enlaces y códigos se tratan como secretos y no se registran.

### 5.8.1 Cuentas y miembros: lo que el proveedor no ofrece

Las rutas capturadas del proveedor son `/auth/user`, `/auth/user/deli`, `/auth/nologin` en modo duplex, `/device/status`, `/device/status/multi`, `/device/status/sub`, `/device/status/unsub` y `/app/version_check.jsp`. Ninguna gestiona cuentas, miembros, roles ni listas de usuarios de una cuenta. Lo único que toca la cuenta es el login y el borrado de la propia sesión.

En el APK stock, las pantallas que parecen de usuarios (`activity_share_manage.xml`, titulada "Users", y `activity_share_device.xml`, titulada "New user") son en realidad la lista de personas con las que se comparte un dispositivo y la pantalla que genera la invitación: QR de 120 dp, enlace, caducidad del tipo "disponible antes de", copiar y hoja de compartir del sistema. No hay alta de cuentas, ni papeles distintos de propietario e invitado, ni invitaciones que existan sin un dispositivo detrás.

La app ya cubre esos dos destinos (`share_manage` y `share_device`, con `share_bind` para aceptar) sobre invitaciones locales. Falta el proveedor real, no el modelo.

Conclusión para el producto: no hay operaciones de cuenta que implementar contra este proveedor. Cualquier pantalla de "usuarios" es la lista de invitaciones de un dispositivo, y debe presentarse así en lugar de prometer una gestión de cuentas que el protocolo no tiene.

### 5.9 Errores y reintentos

`ProviderError` conserva categoría, operación, capacidad, `retryPolicy`, `outcome`, `providerCode` saneado y `causeSafe`. No copia cuerpos, URLs completas ni tokens.

Categorías mínimas: autenticación, autorización, permiso de plataforma, no encontrado, conflicto, límite, red, timeout, parseo, persistencia, entrada inválida, unsupported, unknown, sesión cerrada, gap de eventos y resultado físico desconocido.

Políticas:

- `SAFE_READ_RETRY`: solo lecturas idempotentes, con backoff y jitter acotados.
- `MANUAL_RETRY`: requiere decisión del usuario y nueva correlación.
- `DO_NOT_RETRY`: entrada inválida, unsupported o acción física ambigua.
- `REAUTHENTICATE`: credenciales o sesión ya no válidas, sin reintentar password oculto.
- `RENEGOTIATE`: capacidad o endpoint regional cambió.
- `RESYNC`: hay hueco o cursor inválido, sin fingir continuidad.

## 6. Discovery real sin filtrar endpoints ni DTOs

### 6.1 Servicios regionales

`stock-qvcloud` encapsula bootstrap, `QvLocationManager`, P2P, región, caché y selección de servicios. Su salida hacia dominio es `PreparationState`, no `Map<CloudService, String>`:

1. `idle`: todavía no hay configuración suficiente.
2. `discovering`: resolviendo servicios sin mostrar hosts.
3. `ready`: hay sesión y capacidades utilizables.
4. `degraded`: alguna capacidad falló, con detalle por capacidad.
5. `authenticationRequired`: hace falta material opaco o una nueva cuenta.
6. `failed`: error tipado y política de reintento.

El bootstrap `fermax.qvcloud.net` es evidencia estática, no fallback universal. No se usan IPs históricas de duhow ni se fija el host de actualizaciones como endpoint OAuth. La UI nunca elige región por URL ni escribe paths.

### 6.2 Dispositivos LAN

`DiscoveryPort` devuelve `DiscoveredDevice` con ID estable si está disponible, nombre, modelo declarado, capacidades anunciadas, origen LAN y validez temporal. No devuelve usuario, password, URL CGI ni paquetes crudos. La selección pasa al flujo de pairing por ID de candidato.

El descubrimiento real necesita datagramas/callbacks completos, permisos Android, comportamiento multicast, duplicados y timeout. Mientras tanto `devices.local_discovery` permanece sin compatibilidad demostrada. El listado simulado no se presenta como escaneo real.

## 7. Adaptadores

### 7.1 `stock-qvcloud`, prioritario

Debe cubrir progresivamente todo lo conocido del APK, no solo OAuth y Shadow.

| Componente interno | Responsabilidad | Puerta de activación |
|---|---|---|
| Sesión opaca y OAuth | Registro, login, password/refresh, restauración y single-flight | Contrato request/response, errores y ejecución autorizada |
| Service discovery | Región y URLs OAuth, Shadow, user, alarm y MQTT | Esquema dinámico y correlación con servicios reales |
| User/device API | Lista, credenciales dinámicas, bind e invitaciones | XML/JSON completo, paginación, duplicados y permisos |
| Shadow | Cuatro rutas actuales y campos desconocidos | Mantener golden tests y probar semántica por versión |
| Push/eventos | Registro FCM, recepción, historial, realtime y resync | Evidencia runtime, background, orden, huecos y deduplicación |
| Pairing | QR, AP, audio, LAN, manual y recuperación | Permisos, secuencias y prueba con hardware autorizado |
| Media/acciones | P2P, CGI, llamadas, unlock, F1, guardia y ack | Alternativa FOSS verificada y pruebas de dispositivo, sin copiar binarios propietarios |

Si una ejecución autorizada demuestra que el shadow MQTT está activo, el adaptador stock deberá conservar sus topics `tdk/client_api/users/get/`, `tdk/client_api/sys/get/` y `tdk/client_api/sys/update/`, QoS 1, heartbeat de 300 segundos, access token como password y espera de reconexión acotada de 0 a 10 segundos según E037-E041. Hoy está desactivado por defecto y su runtime no está demostrado. Ese eventual canal interno de stock no es el futuro proveedor `mqtt`.

El transporte media de referencia es P2P nativo con puertos mapeados. No se sustituye por SIP, WebSocket o WebRTC por analogía: `libWebrtcAudio.so` no demuestra `PeerConnection`. Una implementación FOSS debe demostrar interoperabilidad con ese contrato o declararse un perfil distinto sin compatibilidad stock certificada.

No se copian las debilidades E002-E003, E027-E030, E049 ni E072: logging BODY, secretos en logs, trust-all, hostname verification desactivada o claves embebidas. La compatibilidad de wire no exige reproducir esas decisiones inseguras. Digest MD5 o esquemas legacy solo se estudian si son imprescindibles y quedan confinados, documentados y probados. Nunca se relaja TLS para que una prueba pase.

### 7.2 `mqtt`, futuro y no implementado

Su objetivo es ofrecer otro proveedor después de definir un perfil concreto. No se implementa en esta fase.

Contrato previo obligatorio:

- Perfil y versión de firmware/broker, namespace de topics, payload y esquema de capacidades.
- Bootstrap, TLS, autenticación, autorización por dispositivo y rotación.
- QoS, retained, LWT, heartbeat, reconexión, deduplicación, replay y resync.
- Topics separados para comando, ack de dispositivo y observación física.
- Correlación, idempotencia real si existe, expiración de comandos y política de reintento.
- Descubrimiento de dispositivos y negociación por dispositivo.
- Media fuera de MQTT salvo evidencia de otro canal; MQTT por sí solo no aporta preview P2P.

El MQTT del APK usa OAuth y está desactivado por defecto. El MQTT plano de duhow y el de cmos486 pertenecen a firmware distinto. Ninguno se mezcla con stock ni entre sí. La matriz marca el adaptador completo como `not_implemented`.

### 7.3 `self-hosted`, futuro y no implementado

Requiere una API propia versionada y un modelo de confianza explícito. Debe definir cuentas, enrolamiento, inventario, autorización, invitaciones, eventos, acciones, ack, almacenamiento, recuperación y media. Puede usar HTTPS y un canal realtime, pero la elección no está tomada ni se da por implementada.

El servidor no es un proxy transparente que convierta automáticamente QVCloud en compatible. Si actúa como relay stock, sus límites y credenciales se documentan como un perfil distinto y sigue siendo necesaria la evidencia stock por capacidad. El aislamiento multiusuario, revocación y ausencia de secretos en logs son requisitos previos, no mejoras posteriores.

## 8. Lifecycle, threading, Flow, cancelación y persistencia

### 8.1 Lifecycle común

Estados: `closed`, `idle`, `preparing`, `ready`, `degraded`, `authenticationRequired` y `closing`. El proveedor es single-owner por sesión, `close()` es idempotente y no borra secretos. Tras cerrar, cualquier operación devuelve `Closed` excepto consultas inmutables de descriptor.

Preparar una sesión no implica conectar media. Refrescar OAuth no implica reconectar P2P. Suscribir Shadow no implica recibir eventos. Estos recursos tienen ciclos separados para evitar éxitos agregados falsos.

### 8.2 Threading y Flow

- Las funciones públicas son main-safe y la E/S ocurre en dispatchers internos inyectables.
- No se usa un `CoroutineScope` global sin dueño. Cada adaptador tiene ámbito supervisado, cerrado por `close()`.
- `observe*` usa `callbackFlow` o equivalente con `awaitClose`, desregistro de listeners y cierre de conexiones.
- La cancelación del coleccionista cancela suscripción y trabajos hijos. No se ignora `CancellationException` ni se convierte en `Network`.
- Los buffers son acotados y su política explícita. No se descartan eventos de llamada silenciosamente. Overflow genera `Gap` y resync.
- Refresco de sesión es single-flight. Pairing, llamada y acciones tienen mutex o actor por dispositivo cuando la concurrencia pueda duplicar efectos.
- La UI usa `repeatOnLifecycle` y cancela al salir. El repositorio no mantiene colecciones huérfanas por pantalla.

### 8.3 Persistencia

Se separan secretos, catálogo, eventos, cursors y preferencias:

- Secretos cifrados y atómicos, nunca en DataStore JSON común ni en fixtures.
- Catálogo versionado por proveedor/cuenta, con fecha y origen. Los datos obsoletos se etiquetan.
- Cursor y deduplicación persistentes para reanudar eventos. Un cursor inválido fuerza resync visible.
- Journal de acciones con ID, nivel de ack y resultado desconocido. Sirve para reconciliar, no para reintentar automáticamente.
- Migraciones con backup y error visible. Nunca se sustituye un snapshot real dañado por datos demo.
- Galería y archivos locales usan URIs restringidas. No se copian las rutas amplias de FileProvider del APK sin necesidad demostrada.

## 9. Compatibilidad por capacidad

### 9.1 Cómo se afirma una capacidad

Para declarar compatibilidad stock de una capacidad deben cumplirse cuatro dimensiones, salvo `hardware = no aplica` en lógica puramente local debidamente justificada:

1. **API/wire**: request, response, headers, errores y casos límite contrastados con evidencia y tests golden. Una ruta estática sola no basta para runtime.
2. **Comportamiento**: secuencia, permisos, estados, duplicados, reconexión, cancelación y ack comparados en ejecución autorizada.
3. **Seguridad**: TLS estricto, secretos protegidos, autorización y logs revisados. No se copia trust-all ni se filtran credenciales para imitar al original.
4. **Hardware**: prueba autorizada con el dispositivo y firmware objetivo cuando hay pairing, P2P, llamada, acción, notificación o efecto físico. Se registra modelo, firmware, resultado y limitaciones.

La palabra `implemented_contract` solo indica contrato offline. `local_only` nunca suma compatibilidad stock. Los detalles y criterios completos están en la matriz JSON. La tabla resume las 64 capacidades sin ocultar las no demostradas.

| Capacidad | Estado actual | Evidencia necesaria y criterio para afirmarla |
|---|---|---|
| `core.lifecycle` | `implemented_contract` | Tests comunes de cierre, cancelación y recursos. Hardware no aplica al lifecycle puro |
| `core.capability_negotiation` | `local_only` | Negociación real por cuenta/dispositivo y pruebas de required/optional/unsupported/unknown |
| `auth.opaque_registration` | `static_evidence_only` | Registro y respuesta completa, login posterior, persistencia y ejecución autorizada |
| `auth.user_login` | `static_evidence_only` | Request/response, errores, reutilización y revocación observada |
| `auth.oauth_password` | `implemented_contract` | Runtime autorizado, rotación y errores reales, manteniendo TLS y redacción |
| `auth.oauth_refresh` | `implemented_contract` | Refresh concurrente real, caducidad y revocación. Hardware no aplica si no hay dispositivo |
| `auth.session_restore` | `implemented_contract` | Restauración cifrada en app real, migración y borrado. Hardware no aplica |
| `auth.logout` | `unknown` | Contrato de revocación y efectos sobre cuenta, tokens y dispositivos |
| `discovery.regional_services` | `unsupported` | Respuesta regional y asociación de servicios en runtime, sin fallback inventado |
| `devices.list` | `unsupported` | `getDevList` completo, sincronización, permisos y cuenta autorizada |
| `devices.dynamic_credentials` | `static_evidence_only` | Renovación, expiración, aislamiento y uso real de credenciales |
| `devices.local_discovery` | `unsupported` | Datagramas/callbacks, permisos, duplicados y dispositivo LAN real |
| `devices.online_status` | `static_evidence_only` | Semántica P2P/Shadow, frescura y transiciones con dispositivo |
| `pairing.qr` | `local_only` | Cámara e imagen, formatos válidos/inválidos y QR físico autorizado |
| `pairing.ap_wifi` | `local_only` | Secuencia AP, seguridad, timeouts y alta con hardware, sin credenciales por defecto nuevas |
| `pairing.audio` | `local_only` | Emisión acústica, permisos, volumen, timeout y alta real |
| `pairing.bind` | `local_only` | Bind, códigos de retry, duplicados y resultado con dispositivo/cuenta |
| `invitations.accept` | `local_only` | Invitación válida, usada, caducada, reaceptación y permisos reales |
| `invitations.manage` | `local_only` | Lista, edición, borrado, límite y autorización con cuenta real |
| `invitations.issue` | `local_only` | QR/enlace, expiración y escaneo cruzado real sin filtrar secretos |
| `invitations.permissions` | `local_only` | Los seis permisos y su enforcement en cada operación y rol |
| `shadow.get` | `implemented_contract` | Respuesta real, campos desconocidos y semántica por dispositivo/firmware |
| `shadow.multi` | `implemented_contract` | Lotes, ausencias, duplicados y respuesta real |
| `shadow.subscribe` | `implemented_contract` | Ack real y evidencia separada de qué ocurre después |
| `shadow.unsubscribe` | `implemented_contract` | Motivos reales y efectos delimitados, sin llamarlo logout/unbind físico |
| `events.alarm_parser` | `implemented_offline` | Fixtures reales sanitizados y formatos de fecha. Hardware no aplica al parser puro |
| `events.push_registration` | `static_evidence_only` | Registro/rotación FCM, HMAC y backend autorizado sin filtrar tokens |
| `events.realtime` | `unsupported` | Canal activo, QoS, orden, duplicados, gaps, replay y resync |
| `events.history` | `local_only` | API de alarmas, páginas de 15, errores y filtros con datos reales |
| `events.photo` | `local_only` | Adjunto real, caducidad, permisos y asociación evento-foto |
| `events.delete` | `local_only` | Borrado individual/total, autorización y sincronización real |
| `events.filters` | `static_evidence_only` | Filtros All/RINGS/MOTION, incluida su condición de visibilidad |
| `media.preview_single` | `unsupported` | Túnel P2P, render, credenciales, estados y hardware. Sin binarios propietarios |
| `media.preview_multi` | `unsupported` | Cuatro paneles, foco, canales y rendimiento con dispositivo |
| `media.stream_controls` | `unsupported` | Calidad, canal, timeout y reconexión reales |
| `media.listen` | `unsupported` | Audio entrante, permisos, latencia y cierre con hardware |
| `media.talk` | `unsupported` | Audio bidireccional, micrófono, half/full duplex y cierre |
| `media.snapshot` | `unsupported` | Frame real, almacenamiento, permisos y archivo verificable |
| `media.record` | `unsupported` | Vídeo real, duración, interrupción, archivo y galería |
| `media.local_gallery` | `local_only` | Fotos/vídeos reales, pager, controles y agrupación con archivos de prueba |
| `media.local_share_save` | `local_only` | Exportación/compartición real por URI y alcance mínimo de FileProvider |
| `actions.unlock` | `unsupported` | Comando, ack, timeout, permisos y efecto físico con hardware autorizado |
| `actions.f1` | `unsupported` | Pulso, duración, ack y relé real sin reintentos ambiguos |
| `actions.call_guard` | `unsupported` | Llamada a conserjería y estado real |
| `actions.call_accept` | `unsupported` | Aceptación, correlación y apertura de media real |
| `actions.call_reject` | `unsupported` | Rechazo y cierre local/remoto real |
| `calls.lifecycle` | `local_only` | Auto-rechazo 30 s, evento 20, background y duplicados reales |
| `config.rename` | `local_only` | Persistencia local/remota, duplicados y sincronización real |
| `config.passwords` | `local_only` | Cambio de dispositivo/admin/unlock, rotación y recuperación segura |
| `config.unlock_security` | `local_only` | Quick unlock y biometría real local, sin convertirla en ack de apertura |
| `config.alarms` | `local_only` | Llamada/movimiento, owner/shared y persistencia cloud/dispositivo |
| `config.wifi` | `local_only` | Cambio WiFi y reconexión real sin exponer la contraseña |
| `config.time_sync` | `local_only` | Comando y estado de hora real con dispositivo |
| `config.playback_intercom` | `local_only` | FPS, calidad, modo audio, F1 y lock number efectivos |
| `config.delete_device` | `local_only` | Unbind, borrado local, invitaciones y efectos reales |
| `firmware.check` | `local_only` | Consulta real, versión y disponibilidad sin instalar nada |
| `firmware.apply` | `unsupported` | Paquete auténtico, progreso, fallo y recuperación con hardware expresamente autorizado |
| `app.update_check` | `static_evidence_only` | Respuesta real del actualizador con TLS estricto y sin instalar automáticamente |
| `app.privacy_terms` | `local_only` | Contenido local revisado, idioma y accesibilidad. Hardware no aplica |
| `app.deep_link` | `static_evidence_only` | Intent real, parámetros, navegación y ausencia de datos sensibles |
| `app.diagnostics` | `local_only` | Diagnóstico acotado y logs saneados, sin token ni cambio oculto de servidor |
| `app.sound_vibration` | `local_only` | Preferencias y llamada real en dispositivo Android, separado del hardware Wibox |
| `security.persistence` | `design_required` | Keystore, atomicidad, migración, backup y pruebas de no fuga en app real |
| `events.notifications` | `static_evidence_only` | FCM background/foreground, full-screen intent, permisos y llamada real |

## 10. Cómo evitar falsos éxitos y pérdida de funciones

1. **Provider explícito**: la composición selecciona `stock-qvcloud`, `mqtt`, `self-hosted` o demo. Nunca se recurre a demo porque stock falle.
2. **Resultados por capacidad**: una sesión OAuth correcta no marca lista, eventos, media o acciones como disponibles.
3. **Unsupported y unknown visibles**: no se ocultan sistemáticamente las funciones desconocidas. Se explica si falta negociación, permiso o implementación.
4. **Sin booleanos agregados**: no hay un único `connected = true` para cloud, P2P, push y media. Cada subsistema tiene estado propio.
5. **Sin listas vacías como éxito**: un fallo de `getDevList` no se transforma en cero dispositivos. Un parser fallido no genera un evento vacío.
6. **Sin ack ficticio**: `code=0`, HTTP 2xx, publish o ack de suscripción conservan su nivel real. El fake siempre incluye origen demo.
7. **Sin retry físico ambiguo**: timeout o desconexión produce `UNKNOWN_OUTCOME`, reconciliación y opción manual, no repetición automática.
8. **Sin pérdida de extensiones**: campos desconocidos se preservan y cada función stock tiene una capacidad en la matriz, aunque no tenga equivalente en otros proveedores.
9. **Sin inferencias de UI**: la UI no deduce URLs, modelo, capacidades o permisos desde IDs, nombres de topics o DTOs.
10. **Sin mezcla de perfiles**: QVCloud, duhow y cmos486 tienen fixtures, credenciales, topics y evidencias separados.

## 11. Migración incremental y compatibilidad

### 11.1 Compatibilidad binaria y source

- `WiboxCloud`, `WiboxCloudFactory`, `TokenStore` y los modelos actuales permanecen durante la migración. No se añaden métodos abstractos a `WiboxCloud`, porque rompería implementadores como `FakeWiboxCloud`.
- `StockQvCloudProvider` envuelve el cliente actual y traduce a `WiboxProvider`. El adaptador inverso temporal puede seguir ofreciendo `WiboxCloud` a consumidores antiguos solo para las operaciones ya soportadas.
- Las capacidades nuevas usan `CapabilityKey`, no nuevas ramas del enum legacy que puedan romper `when` exhaustivos. `ActionOutcome` no sustituye campos de `CloudActionAcknowledgement`.
- Las deprecaciones empiezan con aviso, nunca con borrado. La retirada binaria requiere una versión mayor y una revisión explícita de consumidores.
- Los DTOs internos no se hacen públicos para facilitar la migración. Las extensiones se añaden como tipos de dominio versionados.

### 11.2 Fases y dependencias

| Fase | Trabajo | Depende de | Salida verificable |
|---|---|---|---|
| F0 | Congelar esta matriz, evidencias y criterios | Inventarios y contrato actual | 64 capacidades y 38 pantallas con estado explícito |
| F1 | Añadir puerto nuevo, adaptador demo y wrapper legacy sin cambiar comportamiento | F0 | Contratos comunes y app compilando con selección explícita |
| F2 | Migrar repositorio y ViewModels a modelos neutros; modernizar UI por grafos | F1 | Mismas 33 pantallas no legacy, estados y tests sin `Demo*` fuera del adaptador demo |
| F3 | Completar discovery, registro, login, lista y credenciales stock | F1 y evidencia dinámica autorizada | Cuenta de prueba y contratos runtime por capacidad |
| F4 | Completar pairing, invitaciones, historial, push y configuración stock | F3 y permisos/hardware según capacidad | Suites específicas y revisión funcional punto por punto |
| F5 | Implementar P2P/media y acciones físicas con ack correcto | F3, alternativa FOSS y hardware autorizado | Evidencia por dispositivo/firmware, sin falso ack |
| F6 | Diseñar e implementar MQTT solo tras fijar un perfil | Puerto estable F2 y perfil MQTT completo | Suite MQTT independiente, sin restar prioridad a stock |
| F7 | Diseñar e implementar self-hosted | Puerto estable F2 y API propia versionada | Suite self-hosted y seguridad multiusuario |
| F8 | Revisión final y empaquetado | Capacidades implementadas que entren en la release | Checklist funcional, compatibilidad, seguridad, errores, navegación, tests, APK e instalación en emulador |

La UI puede avanzar en F2 sin esperar a media real, siempre que muestre unsupported/unknown y no convierta la demo en evidencia. MQTT y self-hosted no bloquean las correcciones de stock, pero tampoco se anuncian antes de F6/F7.

### 11.3 Tests de contrato comunes

Todos los proveedores implementados deben superar:

1. Descriptor, capacidades y distinción required/optional/unsupported/unknown.
2. Restauración, expiración, cierre idempotente y operación tras cierre.
3. Cancelación de cada Flow, desregistro y ausencia de corrutinas huérfanas.
4. Dispositivos con datos ausentes, duplicados, campos desconocidos y capacidades por dispositivo.
5. Eventos duplicados, desordenados, huecos, resync y cursor corrupto.
6. Errores de red, timeout, autenticación, autorización, parseo y persistencia sin secretos.
7. Acciones rechazadas, timeout y `UNKNOWN_OUTCOME`, comprobando que no hay retry físico.
8. Niveles de ack y textos que no convierten aceptación en efecto físico.
9. Persistencia, migración, aislamiento por cuenta y redacción de logs.
10. Selección de provider y prueba de que un fallo real nunca activa demo.

### 11.4 Suites específicas

- **Stock**: golden OAuth/Shadow actuales, fixtures XML/JSON de user/alarm, discovery regional, registro opaco, credenciales dinámicas, pairing AP/audio/LAN, FCM, P2P y CGI. Cada operación runtime nueva exige evidencia autorizada.
- **MQTT futura**: broker embebido o contenedor de prueba sin credenciales reales, TLS, QoS, retained, LWT, duplicados, replay, topics desconocidos y correlación.
- **Self-hosted futura**: servidor simulado con versión de API, migraciones, autorización, multiusuario, revocación, rate limit y recuperación.
- **UI**: ViewModels, navegación, Compose, accesibilidad, tamaños y capturas reales descritos en `docs/ui-modernization.md`.
- **Hardware**: protocolo por modelo/firmware, resultado y límites. No se ejecuta como efecto lateral de los tests unitarios.

## 12. Revisión final y APK

La revisión final debe recorrer la matriz capacidad por capacidad y la UI pantalla por pantalla. Para cada punto registrará: implementado o no, evidencia API/wire, comportamiento, seguridad, hardware, prueba enlazada y limitación restante. Las capacidades no demostradas se presentan como tales en la release.

El empaquetado posterior debe producir un APK real, no una captura ni un informe que lo sustituya. La puerta mínima será build y tests, hash SHA-256 del APK, instalación en emulador con serial explícito y recorrido de humo. La existencia del APK no cambia el estado de compatibilidad de ninguna capacidad. Esta hoja solo produce arquitectura y criterios, no compila ni certifica ese APK.
