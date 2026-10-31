# API local Binder/AIDL para terceros

## 1. Estado y factibilidad

Es factible exponer una parte de Wibox a otras apps Android mediante un servicio local Binder/AIDL con binding explícito. Es la vía preferida frente a abrir HTTP en loopback o en la red, porque Android aporta el UID real del llamante y permite aplicar autorización en cada transacción.

Este documento es un diseño listo para una fase posterior de implementación. El servicio, la allowlist, los AIDL, el SDK y sus pruebas no se han implementado ni ejecutado. Ninguna app puede consumir hoy esta API. La presencia de métodos en el contrato no cambia el estado de compatibilidad de QVCloud.

La API se apoyará en el futuro `WiboxProvider` y en sus subpuertos opcionales. No se conectará directamente a Ktor, DTOs de red, `FakeWiboxCloud` ni a los fixtures demo. `WiboxCloud` y `RealWiboxCloud` podrán quedar encapsulados dentro del adaptador stock durante la migración, pero no serán la frontera pública para terceros.

Objetivos:

1. Permitir integraciones firmadas de forma distinta a Wibox sin debilitar la seguridad.
2. Denegar por defecto y exigir aprobación y revocación visibles para el usuario.
3. Exponer estado y capacidades sin transportar secretos ni prometer efectos físicos.
4. Mantener extensiones específicas de QVCloud y de futuros proveedores.
5. Ofrecer un SDK Kotlin main-safe con corrutinas, Flow y compatibilidad explícita.

## 2. Base contrastada y límites

Fuentes locales revisadas:

- `docs/provider-architecture.md`, arquitectura propuesta y matriz de 64 capacidades.
- `analysis/architecture/provider-matrix.json`, fuente normativa de soporte y evidencia.
- `core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt`, superficie legacy actual.
- `docs/protocol.md`, contratos offline y operaciones `Unsupported`.
- `docs/dynamic-analysis.md` y `analysis/dynamic/inventory.json`, límites de ejecución controlada.
- `app/src/main/AndroidManifest.xml`, que actualmente no declara ningún servicio externo.

Hechos relevantes:

- OAuth password, OAuth refresh, restauración y las cuatro operaciones Shadow tienen contrato implementado offline. No son una validación runtime completa.
- `devices.list`, `events.realtime`, las acciones físicas y media P2P están `unsupported` para stock en la matriz actual.
- El parser de alarmas es offline y no demuestra recepción push ni eventos en vivo.
- La consulta de actualización tiene una request y una response HTTP 200 XML observadas en el análisis dinámico. Siguen abiertos los errores, la decisión completa y el comportamiento TLS de producción. La matriz conserva `app.update_check` como `static_evidence_only` y este diseño no eleva ese nivel.
- El repositorio está acoplado actualmente al fake en partes de la app. Ese fake no se presentará como QVCloud.

Por tanto, la infraestructura local de handshake, autorización y descriptores puede implementarse antes que QVCloud completo. Las lecturas Shadow quedan condicionadas a una sesión y a disponibilidad real del provider. Lista, realtime y acciones stock deben devolver `UNSUPPORTED` mientras su capacidad no esté disponible. En demo se devolverá simulación explícita y nunca evidencia stock.

## 3. Modelo de amenazas

| Amenaza | Mitigación obligatoria | Residual asumido |
|---|---|---|
| Una app declara el package name de otra | El nombre se contrasta con los paquetes del UID real y con la firma actual registrada | Un dispositivo comprometido queda fuera del modelo |
| Package spoofing mediante Intent o campos Parcelable | Binding explícito y derivación de identidad en el servicio, nunca desde extras del llamante | El llamante puede seguir intentándolo, pero recibe `PACKAGE_UID_MISMATCH` |
| Una app con firma distinta obtiene acceso | SHA-256 del conjunto de certificados actuales y aprobación por el usuario | La clave firmante debe permanecer protegida por su propietario |
| Uso de un permiso Android `normal` | No se usa como frontera de seguridad, porque puede concederse sin una decisión específica de Wibox | Puede añadirse solo como filtro declarativo, nunca como autorización |
| Uso incorrecto de un permiso `signature` | No se exige a terceros, porque solo lo satisfacen apps firmadas con la misma clave que Wibox | Integraciones con otra firma dependen de la allowlist |
| UID compartido | Se deniega en v1 si el UID contiene más de un package name | Una versión futura necesitaría una regla específica y pruebas adicionales |
| Actualización o rotación de firma | Misma firma conserva acceso. Una firma nueva suspende el grant y exige reaprobación | No se acepta automáticamente el historial de firma |
| Reinstalación con otro UID | El grant contiene UID, paquete y firma. Un UID distinto requiere reaprobación | Es una decisión conservadora y documentada |
| Revocación mientras hay callbacks o acciones | Se comprueba el grant en cada llamada, se invalida por generación y se eliminan suscripciones | Una acción ya enviada puede quedar `UNKNOWN_OUTCOME` |
| Binder transaction flood, Parcelables enormes o callbacks muertos | Límites, rate limiting, operaciones asíncronas, colas acotadas y `linkToDeath` | Un caller autorizado puede consumir su propia cuota |
| Confused deputy entre apps o dispositivos | Cada petición queda ligada al binder de conexión, UID, grant, scopes y dispositivos permitidos | El usuario debe revisar los permisos que concede |
| Fuga de tokens, cookies, credenciales o IDs opacos de cliente | Esos campos no existen en AIDL, modelos SDK, logs ni errores | IDs de dispositivo necesarios se minimizan y redactan en logs |
| Downgrade o desajuste de versiones | Handshake de major/minor, catálogo de métodos y `VERSION_UNSUPPORTED` | Un cliente debe respetar la versión negociada |
| Enum desconocido tras una actualización | Se transmiten enteros AIDL y el SDK conserva un valor `UNKNOWN` | La app no debe tomar decisiones destructivas con valores desconocidos |
| Falsa apertura por ack insuficiente | Niveles de ack, simulación visible y ausencia de retries físicos ambiguos | La observación física solo se afirma con evidencia independiente validada |

Segunda pasada realizada sobre este diseño: se revisaron suplantación de paquete, UID compartido, rotación, revocación, secuestro de Intent, callbacks, overflow, cancelación, repetición de acciones, downgrade, compatibilidad de Parcelable y filtrado de secretos. Las mitigaciones quedan expresadas como requisitos de implementación, no como defensas ya probadas.

## 4. Servicio y binding explícito

Nombres propuestos, todavía no presentes en el manifiesto:

- Paquete de la app: `dev.nacho.wibox`.
- Servicio: `dev.nacho.wibox.externalapi.WiboxExternalApiService`.
- Acción: `dev.nacho.wibox.externalapi.BIND`.
- Interfaz: `dev.nacho.wibox.externalapi.aidl.IWiboxExternalService`.

Fragmento conceptual, no aplicado:

```xml
<service
    android:name=".externalapi.WiboxExternalApiService"
    android:exported="true"
    android:process="">
    <intent-filter>
        <action android:name="dev.nacho.wibox.externalapi.BIND" />
    </intent-filter>
</service>
```

No se propone `android:permission` como control de acceso. `android:process` vacío representa el proceso principal, no un atributo que deba copiarse literalmente. La fase de implementación debe omitir ese atributo salvo que se justifique otro proceso.

Reglas:

1. Solo se acepta binding por componente explícito. El SDK fija `ComponentName`, nunca depende de resolver una acción implícita.
2. `onBind` devuelve una instancia Binder ligera y distinta por conexión. No inicializa providers ni muestra UI.
3. La primera operación válida es `handshake`. El resto devuelve `HANDSHAKE_REQUIRED`.
4. Cada método vuelve a validar `Binder.getCallingUid()` contra el UID asociado a esa conexión.
5. El servicio no abre sockets HTTP para la integración local y el consumidor no necesita permiso `INTERNET` para usar Binder.
6. En Android 11 o posterior, la app consumidora puede necesitar `<queries><package android:name="dev.nacho.wibox" /></queries>` para visibilidad de paquete.
7. La API externa v1 requiere Android API 28 o superior para consultar `SigningInfo` de forma consistente. En versiones inferiores el handshake devuelve `UNSUPPORTED` salvo que se diseñe y pruebe un fallback específico.

## 5. Identidad, autorización y allowlist

### 5.1 Identidad derivada en el servidor

En `handshake`, y de nuevo cuando cambie el paquete o se invalide la caché, el servicio hará lo siguiente:

1. Obtener el UID con `Binder.getCallingUid()`.
2. Rechazar UIDs aislados y no pertenecientes a apps normales según la política de plataforma.
3. Resolver `PackageManager.getPackagesForUid(uid)`.
4. Exigir exactamente un paquete en ese UID y que coincida con `ClientHello.packageName`. Más de un paquete implica `SHARED_UID_UNSUPPORTED`.
5. Leer `PackageInfo.signingInfo` con `GET_SIGNING_CERTIFICATES` y calcular SHA-256 de cada certificado firmante actual.
6. Normalizar los hashes como hex minúsculo sin separadores, ordenarlos y comparar el conjunto completo. No basta con que coincida uno si existen varios firmantes.
7. Buscar una entrada activa con la tupla exacta `(uid, packageName, currentSignerSetSha256)`.
8. Autorizar solo los scopes y dispositivos aprobados. La ausencia de entrada deniega.

La identidad y la generación del grant se capturan durante la transacción Binder y se adjuntan al trabajo encolado. Una corrutina background no debe llamar a `Binder.getCallingUid()` para reconstruir al llamante, porque allí ya no está en el contexto de la transacción original.

Una lista de dispositivos concedidos vacía significa acceso a cero dispositivos. No hay comodines ni herencia automática a dispositivos nuevos. El usuario debe ampliar el grant de forma explícita.

El certificado no lo envía el cliente. El servicio lo obtiene de `PackageManager`. El package name de `ClientHello` es una afirmación a contrastar, no una prueba.

### 5.2 Alta y aprobación

`requestAuthorization` permite registrar una solicitud con nombre visible, scopes y dispositivos deseados. No concede acceso. La identidad usada es la derivada del UID y la firma, no el nombre visible aportado por la app.

La aprobación se realiza dentro de Ajustes de Wibox. Una app externa no puede aprobar su propia solicitud, simular el diálogo ni elevar scopes. Wibox mostrará paquete, hashes SHA-256, scopes, dispositivos y estado. Se limitarán solicitudes pendientes para evitar spam.

Estados:

- `NOT_REQUESTED`
- `PENDING`
- `AUTHORIZED`
- `DENIED`
- `REVOKED`
- `SIGNATURE_CHANGED`
- `UID_CHANGED`
- `SHARED_UID_UNSUPPORTED`

Una app revocada puede crear una nueva solicitud, pero queda `PENDING` y sin acceso hasta una decisión nueva del usuario.

### 5.3 Scopes mínimos

- `provider.read`
- `capabilities.read`
- `devices.read`
- `shadow.read`
- `events.read`
- `actions.unlock`
- `actions.f1`
- `actions.call_guard`
- `actions.call_control`
- `app.upgrade_check`

La autorización de un scope no implica que el provider soporte la operación. Se comprueban siempre identidad, grant, scope, dispositivo, capability y estado de sesión. `required` en la matriz describe diseño, no disponibilidad runtime.

### 5.4 Actualización, rotación y revocación

- Actualización con mismo UID, paquete y conjunto firmante: conserva grant.
- Cambio de firma: `SIGNATURE_CHANGED`, callbacks cerrados y reaprobación obligatoria. El historial de certificados no autoriza por sí solo.
- Cambio de UID: `UID_CHANGED` y reaprobación.
- Revocación: se incrementa la generación del grant, las llamadas posteriores devuelven `AUTHORIZATION_REVOKED` y se eliminan callbacks. La notificación es best effort y nunca sustituye la comprobación por llamada. Como excepción sin exposición de datos, se permiten `unsubscribeEvents` y la cancelación de operaciones pertenecientes a esa misma conexión para garantizar limpieza.
- Una acción revocada antes de enviarse se cancela. Si ya pudo salir hacia el provider, su resultado es `UNKNOWN_OUTCOME` hasta reconciliar, nunca cancelación física garantizada.

La allowlist no necesita contener secretos. Se persistirá con esquema versionado y escritura atómica. No se guardan tokens OAuth, cookies, contraseñas, `clientId`, `dataEncodeKey`, códigos QR ni claves media.

## 6. Versionado y compatibilidad

Hay dos versiones relacionadas pero distintas:

1. Wire AIDL `major.minor`, inicialmente `1.0`.
2. SemVer del SDK, inicialmente `1.0.0` cuando se publique una primera versión real.

Reglas wire:

- Major distinto: `VERSION_UNSUPPORTED`.
- Mismo major: se negocia `minor = min(clientMinor, serverMinor)`.
- Un minor nuevo solo añade métodos, capabilities, nuevos tipos Parcelable o valores enum.
- Los métodos AIDL existentes no cambian de firma, orden ni semántica.
- No se añaden ni reordenan campos en un Parcelable existente dentro de la misma major. Un campo estructural nuevo usa un tipo `V2` y, cuando cambia una firma, un método nuevo. Así se evita romper lectores V1 incluso sin depender de extensiones de Stable AIDL.
- Un cambio incompatible usa nuevos tipos `V2` y una nueva interfaz major. No se reinterpreta un Parcelable V1.
- El servidor conserva lectores V1 durante la ventana de migración documentada.
- Un cliente con minor mayor solo llama a métodos presentes en la versión negociada. El servidor también valida y devuelve `UNSUPPORTED` o `VERSION_UNSUPPORTED`, sin depender del SDK.
- Las capacidades se consultan en runtime. Su sola presencia en el contrato no indica disponibilidad.

El SDK usará SemVer: major para ruptura de API Kotlin, minor para adición compatible y patch para correcciones. Las deprecaciones incluirán sustituto y versión de retirada prevista. `WiboxClient` no será una interfaz destinada a implementación por consumidores. Añadir un método abstracto que rompa implementadores exige major, mientras que una minor solo podrá usar una adición binaria compatible. Se añadirá una comprobación de compatibilidad binaria del SDK. No se publicará ningún artefacto como parte de esta hoja.

## 7. Contrato AIDL conceptual

El código siguiente define la forma propuesta. Cada Parcelable viviría en su propio archivo AIDL y tendría validación en ambos extremos. Los callbacks one-shot son `oneway` para que el servicio no quede bloqueado por el consumidor. La entrega one-way sigue sujeta a colas y presupuestos acotados.

```aidl
package dev.nacho.wibox.externalapi.aidl;

interface IWiboxExternalService {
    HandshakeResult handshake(in ClientHello hello);
    AuthorizationResult requestAuthorization(in AuthorizationRequest request);
    AuthorizationResult getAuthorizationState();

    ProviderSnapshotResult getProviderSnapshot();
    CapabilityPageResult getCapabilities(in CapabilityQuery query);

    void listDevices(in PageRequest request, in IDevicePageCallback callback);
    void getDevice(in DeviceRequest request, in IDeviceCallback callback);
    void getDeviceShadow(in ShadowQuery request, in IShadowCallback callback);
    void getDeviceShadows(in ShadowBatchQuery request, in IShadowBatchCallback callback);

    EventSubscriptionResult subscribeEvents(
        in EventSubscriptionRequest request,
        in IWiboxEventCallback callback
    );
    void unsubscribeEvents(in IWiboxEventCallback callback);

    void performAction(in ActionRequest request, in IActionCallback callback);
    CancellationResult cancelOperation(String requestId);
    ActionOutcomeResult getActionOutcome(String requestId);

    void checkAppUpgrade(
        in UpgradeCheckRequest request,
        in IUpgradeCallback callback
    );
}
```

Callbacks:

```aidl
oneway interface IDevicePageCallback {
    void onSuccess(in DevicePage result);
    void onError(in ApiError error);
}

oneway interface IDeviceCallback {
    void onSuccess(in DeviceSnapshot result);
    void onError(in ApiError error);
}

oneway interface IShadowCallback {
    void onSuccess(in ShadowSnapshot result);
    void onError(in ApiError error);
}

oneway interface IShadowBatchCallback {
    void onSuccess(in ShadowBatch result);
    void onError(in ApiError error);
}

oneway interface IActionCallback {
    void onOutcome(in ActionOutcome result);
    void onError(in ApiError error);
}

oneway interface IUpgradeCallback {
    void onSuccess(in UpgradeInfo result);
    void onError(in ApiError error);
}

oneway interface IWiboxEventCallback {
    void onEvents(in EventBatch batch);
    void onConnectionChanged(in EventConnectionState state);
    void onGap(in EventGap gap);
    void onAuthorizationChanged(in AuthorizationSnapshot state);
}
```

Las operaciones de red y las acciones son asíncronas. Los métodos síncronos solo leen estado local ya materializado y nunca esperan red, P2P o trabajo físico. Cada callback one-shot se elimina en un `finally` tras completar, fallar, cancelar o morir el Binder.

## 8. Modelos públicos y ausencia de secretos

Los nombres siguientes son parte del contrato JSON. Los tipos AIDL transportan datos inmutables y primitivos, listas acotadas y otros Parcelables. No se usa `Bundle` genérico, JSON libre ilimitado ni Java serialization.

### 8.1 Conexión, sesión y provider

- `ClientHello`: `packageName`, `clientApiMajor`, `clientApiMinor`, `clientSdkVersion`.
- `HandshakeResult`: versión negociada, estado de autorización, `ApiError` opcional.
- `AuthorizationRequest`: nombre visible no confiable, scopes y device IDs solicitados.
- `AuthorizationSnapshot`: estado, scopes concedidos, dispositivos concedidos y generación del grant.
- `ProviderSnapshot`: `providerId`, `profileVersion`, `connectionKind`, estado de preparación, `SessionSummary`, `simulated` y `observedAt`.
- `SessionSummary`: solo `state`, `sessionGeneration` y `expiresAtEpochMs` opcional. No contiene cuenta opaca, usuario, password, tokens, cookies, endpoints ni `clientId`.
- `ApiError`: código, política de retry, capability, outcome opcional y mensaje seguro. Nunca copia bodies, URLs completas, headers ni excepciones de transporte.

Estados de sesión: `UNAVAILABLE`, `AUTHENTICATION_REQUIRED`, `PREPARING`, `READY`, `DEGRADED`, `CLOSED`, `UNKNOWN`.

La API no ofrece login, password grant, refresh, logout ni registro a terceros. La sesión la administra Wibox y el usuario. `REAUTHENTICATE` significa abrir Wibox y resolverla allí, no pedir credenciales a la app consumidora.

### 8.2 Capacidades y dispositivos

- `CapabilityQuery`: página, filtro opcional de dispositivo y namespace.
- `CapabilitySnapshot`: clave estable, scope, soporte, disponibilidad, evidencia, origen, perfil, `observedAt`, `revalidateAfter`, limitaciones y extensiones tipadas.
- `DeviceRef`: provider, device ID y canal opcional.
- `DeviceSnapshot`: referencia, nombre, modelo declarado, rol, presencia, última observación, origen, frescura, `simulated` y capabilities del dispositivo.
- `DevicePage`: elementos, cursor siguiente opcional, origen y frescura.

Presencia: `ONLINE`, `OFFLINE`, `UNKNOWN`. Un Shadow ausente nunca se convierte en `OFFLINE`.

Disponibilidad externa: `AVAILABLE`, `UNSUPPORTED`, `UNKNOWN`, `DEMO_ONLY`. La evidencia conserva los valores de la matriz y se transmite por separado. `AVAILABLE` exige un provider runtime preparado, no solo una evidencia estática.

### 8.3 Shadow y extensiones QVCloud

- `ShadowQuery`: request ID, `DeviceRef`, campos solicitados acotados y modo de frescura.
- `ShadowBatchQuery`: request ID y entre 1 y 20 consultas individuales.
- `ShadowSnapshot`: device ID, campos tipados, timestamp, path lógico, secuencia opcional, frescura, origen y descriptores de campos desconocidos.
- `StockShadowFields`: valores enteros nullables para `F1_state`, `Switch_state`, `Private_mode_state`, `Fisheye_chn_state`, `Lock_state`, `Alarm_zone_mode`, `Smart_relay` y `Device_state`.
- `UnknownFieldDescriptor`: nombre y tipo observado, sin valor arbitrario en v1.

No se expone un mapa JSON arbitrario. Los campos desconocidos conservan su existencia y tipo, pero su valor solo se añade tras una revisión de semántica, tamaño y secretos. Las extensiones futuras usan claves namespaced, por ejemplo `stock.qvcloud.shadow.f1_state`, y valores `ProviderExtensionValue` de tipo booleano, entero, decimal o cadena corta validada.

Esto evita dos errores: reducir stock a un mínimo común y filtrar contenido sensible mediante un DTO genérico.

### 8.4 Eventos

- `EventCursor`: generación de stream y secuencia. Es solo posición, no credencial ni bearer token.
- `EventSubscriptionRequest`: cursor opcional, filtro y tamaño máximo de lote.
- `EventFilter`: hasta 20 dispositivos, canales y tipos acotados.
- `EventEnvelope`: event ID del provider, tipo, dispositivo, canal, tiempo crudo, tiempo parseado opcional, recepción local, origen, duplicado, cursor, `simulated`, extensiones revisadas y adjuntos por metadatos sin contenido.
- `EventBatch`: hasta 10 eventos y un presupuesto serializado total de 48 KiB.
- `EventGap`: último cursor válido, cursor de reanudación opcional, causa, detectado, `resyncRequired` y recuperabilidad.
- `EventConnectionState`: `CONNECTED`, `RECONNECTING`, `RESYNC_REQUIRED`, `CLOSED`, `UNSUPPORTED`.

No se transportan fotos, audio, vídeo ni URIs amplias por Binder. Un adjunto futuro necesitará un canal separado con URI grant mínimo, autorización y caducidad. La API v1 solo incluye metadatos no secretos ya revisados.

### 8.5 Acciones

- `ActionRequest`: request ID aportado por el SDK, `DeviceRef`, tipo, parámetros tipados y expiración local.
- `ActionOutcome`: request ID, acción, dispositivo, ack level, origen, `simulated`, timestamp, código de provider saneado y event ID de observación opcional.
- `CancellationResult`: `CANCELLED_BEFORE_DISPATCH`, `CANCELLATION_REQUESTED`, `ALREADY_COMPLETED`, `NOT_FOUND`, `OUTCOME_UNKNOWN`.
- `UpgradeCheckRequest`: request ID e idioma BCP 47 acotado. Wibox aporta internamente su versión y configuración.
- `UpgradeInfo`: estado `UP_TO_DATE`, `AVAILABLE`, `UNKNOWN`, versión actual, versión disponible opcional, prioridad, descripción saneada y `checkedAt`. No incluye cookie, `clientId`, URL de descarga ni instalación.

El request ID correlaciona y deduplica solo dentro de la identidad autorizada. No es un token y no concede acceso. `getActionOutcome` exige que `(uid, packageName, requestId)` coincida con el dueño de la acción.

## 9. Errores tipados y retry

Códigos v1:

- `NOT_AUTHORIZED`, `AUTHORIZATION_PENDING`, `AUTHORIZATION_REVOKED`, `AUTHORIZATION_DENIED`
- `PACKAGE_UID_MISMATCH`, `SIGNATURE_CHANGED`, `UID_CHANGED`, `SHARED_UID_UNSUPPORTED`
- `HANDSHAKE_REQUIRED`, `VERSION_UNSUPPORTED`
- `UNSUPPORTED`, `CAPABILITY_UNKNOWN`, `SESSION_REQUIRED`, `PROVIDER_NOT_READY`
- `NOT_FOUND`, `CONFLICT`, `INVALID_INPUT`, `PAYLOAD_TOO_LARGE`, `RATE_LIMITED`, `BUSY`
- `CANCELLED`, `SERVICE_DIED`, `CALLBACK_DEAD`
- `AUTHENTICATION`, `PROVIDER_AUTHORIZATION`, `NETWORK`, `TIMEOUT`, `PARSE`, `PERSISTENCE`
- `EVENT_GAP`, `OUTCOME_UNKNOWN`, `ACTION_REJECTED`, `INTERNAL`

Políticas:

- `DO_NOT_RETRY`: entrada inválida, autorización denegada, `UNSUPPORTED` o acción ambigua.
- `SAFE_READ_RETRY`: solo lectura idempotente, con backoff, jitter y límite. El SDK no lo activa por defecto.
- `MANUAL_RETRY`: nueva decisión y nuevo request ID.
- `REAUTHENTICATE`: el usuario resuelve la sesión en Wibox.
- `RENEGOTIATE`: repetir handshake o capabilities.
- `RESYNC`: cursor inválido o gap, sin fingir continuidad.

`UNSUPPORTED` y `CAPABILITY_UNKNOWN` no se colapsan. `UNKNOWN_OUTCOME` no es un fallo definitivo ni un éxito.

## 10. Matriz de capacidades de la API

El contrato JSON reproduce las 64 entradas de `provider-matrix.json` con `stock_support` y `stock_status` sin elevarlos. Clasificación externa:

- `contract_local`: infraestructura local diseñada, sin afirmación sobre QVCloud.
- `contract_runtime_gated`: existe método o modelo propuesto, pero solo será usable si el provider real ofrece la capacidad y la sesión está preparada.
- `contract_demo_only`: solo puede producir datos demo etiquetados, sin compatibilidad stock.
- `unsupported_now`: stock devuelve `UNSUPPORTED` en v1. Un método genérico puede existir para otro provider o para demo.
- `not_exposed`: no hay método externo v1, por seguridad, alcance o falta de utilidad pública.

Resumen de la superficie:

| Capacidad o grupo | API propuesta | Situación que debe mostrar el SDK |
|---|---|---|
| `core.lifecycle` | handshake, cierre y cancelación local | Diseño local, pendiente de implementación |
| `core.capability_negotiation` | `getCapabilities` | Snapshot local con evidencia y frescura |
| Sesión actual | `getProviderSnapshot` | Solo resumen no secreto |
| `shadow.get`, `shadow.multi` | `getDeviceShadow`, `getDeviceShadows` | Contrato offline, disponibilidad condicionada a QVCloud y sesión |
| `devices.online_status` | Campo de `DeviceSnapshot` | Semántica y frescura aún condicionadas |
| `devices.list` | `listDevices`, `getDevice` | `UNSUPPORTED` para stock actual, demo solo si está etiquetado |
| `events.realtime` | `subscribeEvents` | `UNSUPPORTED` para stock actual, gaps obligatorios cuando exista |
| Acciones stock | `performAction`, reconciliación | `UNSUPPORTED` actualmente, sin retry físico |
| `app.update_check` | `checkAppUpgrade` | Implementación limitada a la observación disponible, errores y TLS todavía abiertos |
| OAuth, registro, login y logout | No expuestos | Wibox conserva credenciales y sesión |
| Parser de alarmas | No expuesto | No se aceptan payloads arbitrarios de terceros |
| Pairing e invitaciones | No expuestos en v1 | Requieren secretos efímeros y flujos de usuario propios |
| Configuración y firmware | No expuestos en v1 | `firmware.apply` permanece `UNSUPPORTED` |
| Media, P2P, llamadas y galería | Sin transporte Binder | `UNSUPPORTED` para stock, otro canal seguro futuro |

La matriz detallada del JSON es normativa para las 64 capacidades. Si cambia la matriz del repo, el verificador exige actualizar el contrato en vez de aceptar silenciosamente una elevación.

## 11. Eventos, gaps y reanudación

1. El servicio comprueba `events.read`, dispositivos permitidos y `events.realtime` antes de suscribir.
2. Si stock no ofrece realtime, devuelve `EventSubscriptionResult(error=UNSUPPORTED)`. No devuelve un stream vacío como si estuviera conectado.
3. Cada conexión admite como máximo 4 suscripciones y cada lote tiene como máximo 10 eventos.
4. Los cursores contienen generación y secuencia, no secretos. La deduplicación usa provider, cuenta interna y event ID sin exponer la cuenta.
5. QoS 1, retained y reconexiones no garantizan orden total ni exactamente una vez. El modelo conserva duplicados y tiempo crudo.
6. Al desbordarse una cola se descartan los lotes pendientes de esa suscripción y se emite un único `RESYNC_REQUIRED` con `EventGap`. No se oculta el hueco.
7. Si no hay historial para recuperar el hueco, `recoverability=UNAVAILABLE`. No se inventan eventos.
8. La muerte del callback elimina la suscripción. El SDK puede resuscribirse manualmente desde el último cursor, nunca repetir acciones como parte de esa recuperación.
9. En demo, cada evento lleva `simulated=true` y `origin=DEMO`.

## 12. Acciones y niveles de ack

Niveles permitidos:

| Nivel | Significado |
|---|---|
| `LOCAL_ACCEPTED` | Wibox o el provider demo aceptó localmente la petición |
| `PROVIDER_ACCEPTED` | El servicio cloud aceptó el comando |
| `DEVICE_ACKNOWLEDGED` | El dispositivo confirmó recepción o ejecución según un contrato conocido |
| `PHYSICAL_OBSERVED` | Una observación independiente validada confirma el efecto |
| `UNKNOWN_OUTCOME` | Timeout, desconexión, cancelación tardía o respuesta ambigua |
| `REJECTED` | Existe rechazo tipado y seguro |

Reglas:

1. No hay éxito sin un ack real. HTTP 2xx por sí solo no demuestra efecto físico.
2. `LOCAL_ACCEPTED` no implica envío. `PROVIDER_ACCEPTED` no implica recepción por el dispositivo. `DEVICE_ACKNOWLEDGED` no implica puerta abierta salvo semántica explícita.
3. Solo `PHYSICAL_OBSERVED`, con `simulated=false` y origen no demo, permite al SDK exponer un efecto físico confirmado.
4. Ninguna acción física ambigua se reintenta automáticamente. El usuario debe reconciliar y crear una orden nueva manualmente.
5. Un request ID duplicado por la misma identidad devuelve la operación pendiente o el outcome ya registrado. Nunca vuelve a ejecutar la acción. Un request ID reutilizado con otro payload devuelve `CONFLICT`.
6. La cancelación antes de dispatch evita el envío. Después de dispatch devuelve `CANCELLATION_REQUESTED` y el resultado puede ser `UNKNOWN_OUTCOME`.
7. Se serializan acciones por dispositivo cuando una concurrencia pueda duplicar efectos. Se aplican scopes, permisos de invitación y capability en el servicio.
8. En demo se devuelve como máximo `LOCAL_ACCEPTED`, siempre con `simulated=true` y `origin=DEMO`. Nunca se presenta como QVCloud.
9. Si el proceso cliente muere, Wibox conserva un journal acotado para reconciliación. Si Wibox muere tras un posible dispatch, el SDK devuelve `SERVICE_DIED` con `outcome=UNKNOWN_OUTCOME` y `MANUAL_RETRY`.

Media y P2P no son un subproducto de esta API. Aceptar llamada, preview, audio y vídeo siguen `UNSUPPORTED` mientras la matriz lo indique.

## 13. Límites, threading y ciclo de vida

Presupuestos propuestos:

| Recurso | Límite v1 |
|---|---:|
| Request Parcelable | 32 KiB serializados |
| Response o callback Parcelable | 64 KiB serializados |
| Página de dispositivos | 20 |
| Página de capabilities | 50 |
| Batch Shadow | 20 consultas |
| Batch de eventos | 10 eventos y 48 KiB |
| Device ID | 128 caracteres UTF-8 |
| Nombre visible | 128 caracteres |
| Cadena de extensión | 1024 bytes UTF-8 |
| Operaciones one-shot pendientes | 20 por app, 100 globales |
| Suscripciones de eventos | 4 por app |
| Cola por suscripción | 100 eventos o 64 KiB, lo primero que ocurra |
| Lecturas | 30 por minuto y ráfaga de 10 por UID |
| Acciones | 6 por minuto y ráfaga de 2 por UID, más límites por dispositivo |
| Solicitudes de autorización pendientes | 3 por paquete y 50 globales |

Los límites se validan antes de llamar al provider y al serializar. Una lista que supera presupuesto se pagina o devuelve `PAYLOAD_TOO_LARGE`, nunca se trunca silenciosamente. El rate limit devuelve `RATE_LIMITED` con `retryAfterMs` acotado. Los valores son defaults de diseño y deberán probarse.

Threading y recursos:

- El servicio agenda E/S en un scope supervisado con dispatcher IO. Nunca ejecuta red o P2P en el main thread ni mantiene un hilo Binder esperando una acción física.
- El SDK es main-safe. Las consultas síncronas locales se llaman desde `Dispatchers.IO` y las asíncronas usan `suspendCancellableCoroutine`.
- No hay `GlobalScope`. Cerrar el cliente cancela trabajos, desregistra callbacks y desvincula el servicio.
- La cancelación de una lectura cancela su corrutina y callback. La de una acción sigue la semántica de la sección anterior.
- Cada callback usa `linkToDeath`. Los registros se indexan por `callback.asBinder()` y se eliminan al completar, desuscribir, revocar, desvincular o morir. El SDK conserva una referencia fuerte al callback hasta el resultado terminal o el cierre. Una suscripción que falla durante el alta no retiene el callback.
- No hay callbacks estáticos ni referencias a `Activity` o `Context` de UI. Se usa `ApplicationContext`.
- El SDK trata `onServiceDisconnected` y death recipient como cierre anómalo. Los Flow terminan con error tipado o permiten resuscripción explícita de eventos.
- No se pasan frames, buffers, `Surface`, `ParcelFileDescriptor` ni vídeo por esta API.

## 14. SDK Kotlin propuesto

Artefacto futuro:

```text
dev.nacho.wibox:external-api-sdk:1.0.0
```

Módulo opcional de bajo nivel para consumidores que solo necesiten AIDL:

```text
dev.nacho.wibox:external-api-aidl:1.0.0
```

Ninguno se publica ahora. El SDK incluirá modelos Kotlin inmutables, mapeo tolerante a enums desconocidos, wrapper de conexión, corrutinas y Flow. No expondrá Ktor, DTOs, tokens ni clases demo.

API de alto nivel propuesta:

```kotlin
interface WiboxClient : Closeable {
    suspend fun handshake(): Handshake
    suspend fun requestAuthorization(request: AccessRequest): Authorization
    suspend fun authorization(): Authorization
    suspend fun providerSnapshot(): ProviderSnapshot
    suspend fun capabilities(query: CapabilityQuery): CapabilityPage
    suspend fun devices(page: PageRequest): DevicePage
    suspend fun device(request: DeviceRequest): DeviceSnapshot
    suspend fun shadow(query: ShadowQuery): ShadowSnapshot
    suspend fun shadows(query: ShadowBatchQuery): ShadowBatch
    fun events(request: EventSubscriptionRequest): Flow<WiboxEventUpdate>
    suspend fun performAction(request: ActionRequest): ActionOutcome
    suspend fun actionOutcome(requestId: String): ActionOutcome
    suspend fun checkAppUpgrade(request: UpgradeCheckRequest): UpgradeInfo
}
```

Ejemplo mínimo. Los nombres del componente los encapsula el SDK y la autorización se aprueba en Wibox:

```kotlin
val client = WiboxClientFactory.connect(applicationContext)

try {
    val handshake = client.handshake()
    check(handshake.negotiatedMajor == 1)

    val authorization = client.requestAuthorization(
        AccessRequest(
            displayName = "Mi integración",
            scopes = setOf("provider.read", "capabilities.read", "devices.read"),
        )
    )

    if (authorization.state != AuthorizationState.AUTHORIZED) {
        // El usuario debe revisar la solicitud en Ajustes de Wibox.
        return
    }

    val provider = client.providerSnapshot()
    val capabilities = client.capabilities(CapabilityQuery.firstPage())
    val devices = try {
        client.devices(PageRequest.firstPage())
    } catch (error: WiboxApiException) {
        if (error.code == ApiErrorCode.UNSUPPORTED) {
            DevicePage.unavailable(error)
        } else {
            throw error
        }
    }
} finally {
    client.close()
}
```

`DevicePage.unavailable` es una representación de UI del ejemplo, no una lista vacía de éxito. El SDK no reintenta acciones ni oculta `UNSUPPORTED`.

El SDK valida el componente explícito y usa su `packageName` solo como afirmación que Wibox contrasta. No calcula ni envía una firma como prueba. Wibox obtiene la firma real del sistema.

## 15. Plan de implementación y ownership futuro

| Fase | Trabajo | Módulos propietarios futuros | Puerta de salida |
|---|---|---|---|
| E0 | Congelar contrato, threat model y matriz | `docs`, `analysis/external-api` | Verificador positivo y negativo, revisión de seguridad |
| E1 | AIDL, Parcelables, errores, límites y handshake | `external-api:aidl` | Unit tests de round trip, límites y versión |
| E2 | Servicio Binder y ciclo de vida | `app/externalapi`, futuro `core:external-api` | Binding explícito, denegación por defecto, cancelación y death |
| E3 | Identidad, allowlist y revocación | `core:external-auth`, almacenamiento Android | Tests de UID, paquete, firma, scopes y rotación |
| E4 | Adaptador sobre `WiboxProvider` | `core:provider-api`, `provider:stock-qvcloud`, `provider:demo` | Sin Ktor ni DTOs en AIDL, capacidades por dispositivo |
| E5 | Lecturas, eventos y acciones asíncronas | `core:external-api`, subpuertos del provider | Gaps, ack, journal y cero retries ambiguos |
| E6 | SDK Kotlin y modelos | `external-api:sdk` | API main-safe, Flow, compatibilidad y sample |
| E7 | Ajustes de apps autorizadas | `app/settings` | Alta, aprobación, revocación, scopes, firma y dispositivos |
| E8 | Suite con dos apps de prueba | `external-api:test-client-a`, `external-api:test-client-b` | Firmas distintas, instrumentación y ausencia de secretos |

La integración no debe saltarse E1-E4 exponiendo directamente el fake. La UI de autorización puede desarrollarse en paralelo con mocks del store, pero no se conecta a producción sin las pruebas de identidad.

## 16. Pruebas obligatorias futuras

Ninguna de estas pruebas se ha ejecutado en esta hoja.

### Unitarias

1. Round trip Parcelable de todos los modelos, nulls, enums desconocidos, UTF-8 y límites exactos.
2. Rechazo de listas, cadenas y transacciones sobredimensionadas sin truncado silencioso.
3. Handshake major/minor, método no disponible, downgrade y migración V1/V2.
4. Autorización por tupla UID/paquete/firma, scopes, dispositivos, grant generation y revocación.
5. Package spoof, wrong cert, firma múltiple, UID compartido, UID aislado, rotación y reinstalación.
6. Rate limit por UID y global, saturación y recuperación.
7. Errores tipados y políticas de retry.
8. Acciones con cada ack, ausencia de ack, timeout, cancelación antes y después de dispatch, request ID duplicado con el mismo y distinto payload y cero retries ambiguos.
9. Demo siempre simulada y sin fallback desde stock.
10. Escaneo de transacciones, errores y logs para tokens, cookies, credenciales, `clientId`, secretos y payloads crudos.

### Instrumentación con dos apps de firmas distintas

1. App A autorizada y app B no autorizada, comprobando aislamiento de datos y scopes.
2. Aprobación, denegación, revocación y reaprobación desde Ajustes.
3. App B suplantando package name de A y prueba con certificado incorrecto.
4. Casos de UID compartido, cambio de UID, actualización con misma firma y rotación.
5. Binding explícito, intent implícito rechazado por el SDK y componente incorrecto sin fuga.
6. Callbacks one-shot, suscripciones, gaps, overflow, desuscripción, Binder death y callback leaks.
7. Cancelación de lecturas y acciones, muerte del cliente y muerte de Wibox con `UNKNOWN_OUTCOME` cuando corresponda.
8. Acciones sin ack y verificación de que no existe reintento físico automático.
9. Compatibilidad entre SDK minor anterior/nuevo y servidor anterior/nuevo.
10. `logcat` y capturas de Parcel en laboratorio sin secretos ni datos de cuentas reales.

Las pruebas de hardware, cuando sean necesarias, serán una fase separada y expresamente autorizada. No son un efecto lateral de esta suite.

## 17. Non-goals de v1

- API HTTP, REST, WebSocket, servidor LAN o acceso remoto.
- Autorizar terceros solo por package name o por un permiso `normal`.
- Usar una custom signature permission como si admitiera apps de otra firma.
- Compartir credenciales, tokens, cookies, `clientId`, secretos de dispositivo o claves media.
- Login, pairing, invitaciones, cambios de contraseña o configuración sensible desde terceros.
- Vídeo, audio, P2P, preview, grabación o llamadas por Binder.
- Garantizar entrega exactamente una vez, orden total o ausencia de gaps.
- Reintentar acciones físicas ambiguas o afirmar éxito sin ack.
- Convertir demo en QVCloud ni `local_only` en compatibilidad stock.
- Publicar el SDK, crear un repositorio remoto o declarar disponible el servicio.

## 18. Criterio de disponibilidad

La respuesta corta es sí, es factible una API local segura con Binder/AIDL. La primera entrega útil debería limitarse a handshake, autorización, provider, capabilities y lecturas que el provider pueda respaldar. Shadow depende de sesión y runtime QVCloud. Lista, realtime y acciones stock permanecen `UNSUPPORTED` mientras la matriz lo indique. Demo puede usarse para desarrollo con simulación visible. Media necesita evidencia y un canal distinto, no un Parcelable grande.

La implementación solo podrá anunciarse cuando exista el servicio, la allowlist, el SDK y la suite de dos firmas, con evidencia medida. Hasta entonces el estado es `design-only`.
