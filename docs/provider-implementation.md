# Implementación del puerto de proveedores Wibox

## 1. Alcance y certeza

Esta hoja implementa F0/F1: un puerto provider-neutral y un adaptador `stock-qvcloud` sobre el `WiboxCloud` existente. No sustituye la API legacy, no añade protocolo nuevo y no certifica compatibilidad cloud completa. Toda la evidencia procede de los contratos offline ya registrados y de attempt3 para app upgrade. No se ha usado red, hardware ni credenciales.

La matriz F0 sigue siendo `analysis/architecture/provider-matrix.json`. El catálogo de `ProviderCapabilityCatalog.stockQvCloud` conserva sus 64 IDs, soporte, estado, referencias y limitaciones. La operación disponible se evalúa aparte para evitar que la presencia de un método se convierta en una afirmación de compatibilidad.

## 2. API pública real

Todo el API pública vive en `dev.nacho.wibox.core.cloud` y carece de Ktor, DTOs wire y tipos Android.

| Clase o contrato | Responsabilidad |
|---|---|
| `WiboxProvider` | Descriptor, catálogo, subpuertos, preparación y cierre |
| `WiboxProviderFactory` | Creación explícita del adaptador stock. No ofrece factorías MQTT o self-hosted |
| `ProviderDescriptor` | `ProviderId`, versión de perfil, conexión y extensiones declarativas QVCloud |
| `ProviderCapabilityCatalog` | Catálogo inmutable de las 64 capacidades |
| `CapabilitySnapshot` | Soporte F0, ámbito, dispositivo/canal opcional, revalidación, origen, evidencia declarada, operación y evidencia de operación |
| `SessionPort` | Autenticar, restaurar y refrescar. `logout` sigue `Unsupported` |
| `SessionHandle` | Identidad opaca sin tokens ni password. `toString` redacta identificadores |
| `CredentialMaterial` | Material efímero para autenticación. `toString` redacta identidad y secreto |
| `DevicePort` | Shadow individual, múltiple, suscripción y desuscripción |
| `DeviceSnapshot` | Dispositivo con rol, permisos, canales, presencia, origen y frescura. No se usa como lista vacía de éxito |
| `ShadowSnapshot` | Campos solicitados y devueltos, estado, timestamp, frescura y desconocidos conservados |
| `EventPort` | Parser offline FCM y frontera realtime |
| `EventEnvelope` | Evento con origen, tiempo crudo, campos desconocidos, cursor y duplicados |
| `EventStreamItem` | Eventos, conexión, reconexión, resync y gaps para futuros canales probados |
| `ActionPort` | Frontera de acciones. Actualmente devuelve `Unsupported` antes de ejecutar |
| `ActionOutcome` | Correlación y niveles LOCAL_ACCEPTED, PROVIDER_ACCEPTED, DEVICE_ACKNOWLEDGED, PHYSICAL_OBSERVED, REJECTED y UNKNOWN_OUTCOME |
| `MaintenancePort` | Consulta de actualización de app |
| `ProviderError` | Errores tipados con operación, capacidad, retry, código y causa saneada |

`ProviderId.MQTT`, `ProviderId.SELF_HOSTED` y `ProviderConnectionKind` solo identifican diseños futuros. No existen adaptadores para esos IDs ni capacidades implementadas asociadas. `ProviderId.DEMO` no activa ningún fallback.

## 3. Tres ejes que no se confunden

Cada `CapabilitySnapshot` separa:

1. `support`: obligación declarativa F0, con required, optional, unsupported o unknown.
2. `declaredEvidence` y `declaredOrigin`: estado y origen registrados en la matriz F0.
3. `operation`, `operationEvidence` y `operationOrigin`: operación realmente expuesta y evidencia que la sustenta.

La regla actual solo cambia la evidencia de operación de `app.update_check`: la matriz conserva `static_evidence_only`, mientras el adaptador presenta `IMPLEMENTED_CONTRACT` con `DYNAMIC_CAPTURE` por `DYN3-HTTP-UPGRADE-001`. No se eleva OAuth, Shadow, pairing, lista, realtime, media ni acciones a comportamiento runtime.

`limitations` conserva las cuatro dimensiones actuales de cada entrada. No hay una propiedad `hardware_verified`, ni una conversión automática de tests offline en prueba física.

## 4. Operaciones implementadas y evidencia

| Operación neutral | Delegación legacy | Evidencia y alcance | Prueba de comportamiento |
|---|---|---|---|
| `prepare` | `restoreSession` | Restauración offline. READY solo con sesión y provider abierto | Preparación con/sin sesión y cierre concurrente |
| `session.authenticate` | `authenticate` | E029, E033, contrato offline OAuth password. Sin registro opaco inventado | Sesión opaca y secretos redactados |
| `session.restoreSession` | `restoreSession` | E034, E036, restauración offline | Restauración y preparación |
| `session.refreshSession` | `refreshSession` | E033 a E036, single-flight legacy | Estado REFRESHED y mismo handle opaco |
| `devices.getShadow` | `getDeviceShadow` | E041, contrato offline | Snapshot y campos desconocidos |
| `devices.getShadows` | `getDeviceShadows` | E041, contrato offline | Conserva lista sin rellenar estado |
| `devices.subscribeShadow` | `subscribeDeviceShadows` | E041, solo ack HTTP. No prueba MQTT posterior | Ack tipado, sin realtime prometido |
| `devices.unsubscribeShadow` | `unsubscribeDeviceShadows` | E041, motivos 1 y 2 sin logout/unbind físico implícito | Ack y timestamp conservados |
| `events.parseAlarm` | `parseAlarmEvent` | E056, E057, parser offline 2/19/20 | Envelope FCM y desconocidos |
| `maintenance.checkAppUpgrade` | `cloud.checkAppUpgrade(request)` | E050 y attempt3, GET + HTTP 200 XML. No recalcula HTTP ni parsea XML en el adaptador | Una sola delegación, resultado idéntico y errores mapeados |
| `close` | `close` condicionado por ownership | Lifecycle offline, cierre idempotente | Un solo cierre propiedad del adapter y operación posterior rechazada |

`ProviderErrorMapper` conserva autenticación, autorización, HTTP, red, timeout, parseo, persistencia, entrada inválida, Unsupported, Closed y códigos de negocio sin copiar URLs, cuerpos o tokens. `CancellationException` se propaga como cancelación, no como `Network`.

## 5. Matriz completa F0/F1

La columna `operación` describe la evidencia de la operación expuesta, no compatibilidad total ni hardware.

| Capacidad | Soporte F0 | Evidencia F0 | Operación F1 |
|---|---|---|---|
| `core.lifecycle` | required | implemented_contract | implemented_contract |
| `core.capability_negotiation` | required | local_only | local_only |
| `auth.opaque_registration` | required | static_evidence_only | static_evidence_only |
| `auth.user_login` | required | static_evidence_only | static_evidence_only |
| `auth.oauth_password` | required | implemented_contract | implemented_contract |
| `auth.oauth_refresh` | required | implemented_contract | implemented_contract |
| `auth.session_restore` | required | implemented_contract | implemented_contract |
| `auth.logout` | unknown | unknown | unknown |
| `discovery.regional_services` | required | unsupported | unsupported |
| `devices.list` | required | unsupported | unsupported |
| `devices.dynamic_credentials` | required | static_evidence_only | static_evidence_only |
| `devices.local_discovery` | required | unsupported | unsupported |
| `devices.online_status` | required | static_evidence_only | static_evidence_only |
| `pairing.qr` | required | local_only | local_only |
| `pairing.ap_wifi` | required | local_only | local_only |
| `pairing.audio` | required | local_only | local_only |
| `pairing.bind` | required | local_only | local_only |
| `invitations.accept` | required | local_only | local_only |
| `invitations.manage` | required | local_only | local_only |
| `invitations.issue` | required | local_only | local_only |
| `invitations.permissions` | required | local_only | local_only |
| `shadow.get` | required | implemented_contract | implemented_contract |
| `shadow.multi` | required | implemented_contract | implemented_contract |
| `shadow.subscribe` | required | implemented_contract | implemented_contract |
| `shadow.unsubscribe` | required | implemented_contract | implemented_contract |
| `events.alarm_parser` | required | implemented_offline | implemented_offline |
| `events.push_registration` | required | static_evidence_only | static_evidence_only |
| `events.realtime` | required | unsupported | unsupported |
| `events.history` | required | local_only | local_only |
| `events.photo` | required | local_only | local_only |
| `events.delete` | required | local_only | local_only |
| `events.filters` | optional | static_evidence_only | static_evidence_only |
| `media.preview_single` | required | unsupported | unsupported |
| `media.preview_multi` | required | unsupported | unsupported |
| `media.stream_controls` | required | unsupported | unsupported |
| `media.listen` | required | unsupported | unsupported |
| `media.talk` | required | unsupported | unsupported |
| `media.snapshot` | required | unsupported | unsupported |
| `media.record` | required | unsupported | unsupported |
| `media.local_gallery` | optional | local_only | local_only |
| `media.local_share_save` | optional | local_only | local_only |
| `actions.unlock` | required | unsupported | unsupported |
| `actions.f1` | required | unsupported | unsupported |
| `actions.call_guard` | required | unsupported | unsupported |
| `actions.call_accept` | required | unsupported | unsupported |
| `actions.call_reject` | required | unsupported | unsupported |
| `calls.lifecycle` | required | local_only | local_only |
| `config.rename` | required | local_only | local_only |
| `config.passwords` | required | local_only | local_only |
| `config.unlock_security` | required | local_only | local_only |
| `config.alarms` | required | local_only | local_only |
| `config.wifi` | required | local_only | local_only |
| `config.time_sync` | required | local_only | local_only |
| `config.playback_intercom` | required | local_only | local_only |
| `config.delete_device` | required | local_only | local_only |
| `firmware.check` | required | local_only | local_only |
| `firmware.apply` | required | unsupported | unsupported |
| `app.update_check` | optional | static_evidence_only | implemented_contract con captura dinámica |
| `app.privacy_terms` | required | local_only | local_only |
| `app.deep_link` | optional | static_evidence_only | static_evidence_only |
| `app.diagnostics` | optional | local_only | local_only |
| `app.sound_vibration` | required | local_only | local_only |
| `security.persistence` | required | design_required | design_required |
| `events.notifications` | required | static_evidence_only | static_evidence_only |

Registro opaco, `userLogin`, discovery regional/LAN, lista, credenciales dinámicas, pairing, invitaciones, historial, push, media, acciones, configuración y firmware no tienen una operación nueva por el solo hecho de aparecer en el catálogo.

## 6. Acciones y acknowledgements

`ActionCommand` contiene correlación, dispositivo, canal, tipo, parámetros redactados y política de reintento. `ActionPort.execute` no llama a `performAction` mientras la capacidad sea `unsupported`, por lo que un fake no puede convertir la operación en éxito ni provocar retries.

`ActionOutcome.canClaimPhysicalEffect()` solo es verdadero con `PHYSICAL_OBSERVED`, una observación presente y ausencia de resultado ambiguo. `LOCAL_ACCEPTED`, `PROVIDER_ACCEPTED` y `DEVICE_ACKNOWLEDGED` nunca se traducen por sí solos en puerta abierta. El adaptador no genera los niveles altos porque no existe ack de dispositivo o evidencia física.

`UNKNOWN_OUTCOME` usa `DO_NOT_RETRY`. No se reintenta una acción física ambigua ni se informa éxito por una respuesta de transporte.

## 7. Ownership, concurrencia y cierre

`WiboxProviderFactory.stockQvCloud` recibe un `WiboxCloud` existente y `ProviderSessionContext`. El identificador opaco se genera dentro del adaptador y permanece estable entre autenticación, restauración y refresh. Los tokens legacy no cruzan el puerto.

`closeClientWhenClosed=true` es el valor por defecto: el provider es propietario del cliente y lo cierra exactamente una vez. Con `false`, el llamador conserva la propiedad y puede seguir usando el cliente legacy. En ambos casos el provider queda cerrado para nuevas operaciones. Descriptor y catálogo son inmutables y siguen disponibles.

`prepare` serializa preparaciones con un mutex y vuelve a comprobar el estado tras restaurar. Así no devuelve READY si ocurre un cierre concurrente. No hay un scope global adicional: el cliente propietario conserva sus recursos y su single-flight de refresh.

`observeEvents` no emite un stream vacío de éxito. Una emisión inesperada de un fake se convierte en `Unsupported`, una finalización vacía también, y la cancelación se propaga. Los modelos de gap y cursor existen para futuras implementaciones probadas, no como evidencia de realtime actual.

## 8. Migración de app y UI

La app puede mantener `WiboxCloud`, `WiboxCloudFactory`, `TokenStore`, modelos y fakes actuales. El adaptador envuelve esa API sin modificar sus métodos ni elevar sus capacidades. La migración consiste en inyectar `WiboxProvider` en el repositorio y consumir `SessionHandle`, `ShadowSnapshot`, `EventEnvelope`, `ActionOutcome` y `ProviderError` en vez de tipos QVCloud o fixtures de UI.

Los ViewModels no deben conocer `AppUpgradeRequest.baseUrl`, topics, tokens ni transporte. La composición que tenga configuración secreta o específica seguirá fuera de la UI. La UI decide presentación por `CapabilitySnapshot`, distinguiendo unsupported y unknown. Un error de stock nunca selecciona demo.

`ActionOutcomeText` solo puede anunciar efecto físico cuando `canClaimPhysicalEffect()` sea verdadero. `EventStreamItem.Gap` y `ResyncRequired` se muestran como pérdida de continuidad, no como eventos.

## 9. Consumo futuro por Binder

Una API Binder debe vivir en otra capa y traducir los modelos neutros a contratos Parcelable/AIDL versionados. No debe exportar `WiboxCloud`, `CloudSession`, Ktor, URLs, DTOs o `CredentialMaterial`.

Las llamadas suspend se adaptan a comandos con identificador de correlación. `Flow<EventStreamItem>` se proyecta a callbacks que conservan evento, gap, resync y cursor. El cierre del cliente Binder cancela la colección y, según ownership, cierra el provider una sola vez. Los secretos quedan en el proceso propietario y nunca viajan en snapshots, logs ni callbacks.

## 10. Futuros proveedores y límites

MQTT y self-hosted solo tienen IDs reservados. No hay clases de adaptador, brokers, topics, nube alternativa ni soporte anunciado. El MQTT estático del APK sigue desactivado y no se transforma en el futuro provider MQTT.

Persisten estas limitaciones: registro y login sin request/response completos, discovery sin respuesta regional, lista sin contrato, realtime sin canal probado, media sin alternativa FOSS, acciones sin ack y hardware no probado. OAuth y Shadow siguen siendo contratos offline. App upgrade solo cubre la respuesta de éxito observada, sin decisión de disponibilidad, contrato de error o compatibilidad total.

## 11. Verificación

```bash
python3 core/cloud/verify_provider_port.py
python3 core/cloud/verify_upgrade_contract.py
python3 core/cloud/verify_protocol.py
python3 core/cloud/verify_no_secrets.py
./gradlew :core:cloud:test --rerun-tasks
```

`verification.txt` registra salidas, recuento de tests y límites medidos.
