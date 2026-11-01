# Protocolo cloud de Wibox

## 1. Alcance y nivel de certeza

Este documento describe el contrato implementado en `core:cloud` a partir del APK Wibox Fermax v2.2, paquete `com.fermax.wibox`, versión 18 y SHA-256 registrado en `analysis/apk/inventory.json`. La evidencia estática se complementa con la captura autorizada attempt3 de `analysis/dynamic/inventory.json`. La implementación y sus tests no han contactado con servicios y no se afirma compatibilidad cloud completa.

La API pública está en `core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt`. Los consumidores usan `WiboxCloudFactory.create`, modelos del package `dev.nacho.wibox.core.cloud`, `TokenStore` y `CloudClock`. Ningún tipo público pertenece a Ktor. Transporte, DTO y parsers están en `dev.nacho.wibox.core.cloud.internal`.

Las pruebas usan `MockEngine`, reloj y almacenamiento inyectados, fixtures ficticios y el host reservado `unit.test`. No realizan red.

## 2. Matriz de evidencia, implementación y límites

| ID | Evidencia | Operación | Estado implementado | Prueba principal | Limitación |
|---|---|---|---|---|---|
| P-OAUTH-PASSWORD | E029, E033, `OAuthApi.java`, `GetTokenResp.java` | `authenticate` | Implementado con GET y password grant | `OAuthContractTest.passwordGrantUsesExactWireContractAndPersistsSession` | Requiere credenciales opacas ya obtenidas. No crea una cuenta ni una pantalla de login |
| P-OAUTH-REFRESH | E033 a E036, `QvOAuthManager.java` | `refreshSession` y refresh previo a Shadow | Implementado con refresh grant y margen de cinco minutos | `OAuthContractTest.refreshGrantUsesOnlyRefreshQueryAndAccessTokenHeader`, `SessionBehaviorTest.concurrentRefreshUsesSingleFlightAndOnePersistenceWrite` | Sin credenciales retenidas no hay fallback implícito a password |
| P-SESSION-RESTORE | E034, E036 | `restoreSession`, caché y persistencia | Implementado mediante `TokenStore` | `SessionBehaviorTest.restoresOnceAndUsesFreshCachedAccessToken` | El cifrado y la atomicidad persistente dependen de la implementación de `TokenStore` |
| P-SHADOW-STATUS | E041, `QvShadowApi.java`, `QvShadowHttpManager.java`, entidades Shadow | `getDeviceShadow` | Implementado | `ShadowContractTest.statusUsesPostTokenHeaderAndTypedBody` | Los enteros de estado se conservan sin traducir semántica no probada |
| P-SHADOW-MULTI | E041, `QvShadowApi.java`, entidades Shadow | `getDeviceShadows` | Implementado | `ShadowContractTest.multiStatusUsesArrayBodyAndPreservesMissingState` | No deduce dispositivos ausentes ni paginación |
| P-SHADOW-SUB | E041, `QvShadowApi.java`, `QvShadowSubscribeRequest.java` | `subscribeDeviceShadows` | Implementado solo para `Device_state` | `ShadowContractTest.subscribeUsesDeviceStateSubscriptionAndReturnsAck` | El ack HTTP no abre MQTT ni prueba entrega posterior |
| P-SHADOW-UNSUB | E041, `QvShadowApi.java`, `QvShadowUnsubscribeRequest.java` | `unsubscribeDeviceShadows` | Implementado con motivos 1 y 2 | `ShadowContractTest.unsubscribeUsesReasonAndEmptyFieldsAndReturnsAck` | No revoca la sesión ni desvincula físicamente el dispositivo |
| P-ALARM-FCM | E056, E057, `AlarmMessageInfo.java` | `parseAlarmEvent` | Parser offline para movimiento, llamada y llamada atendida | `EventAndUnsupportedTest.parsesDocumentedAlarmTypesAndPreservesUnknownFields` | No registra FCM ni interpreta el formato de fecha |
| P-APP-UPGRADE | E050 y `DYN3-HTTP-UPGRADE-001` en attempt3 | `checkAppUpgrade` | Implementado solo para GET y HTTP 200 XML de éxito observados | `UpgradeContractTest.requestMatchesAttempt3AndParsesEveryObservedField` | Sin decisión update-available, contrato de error ni compatibilidad cloud total |
| P-ERROR-HTTP | E035 y comportamiento HTTP observado estáticamente | Errores 401, 403 y resto | Errores tipados y sin reintento automático | `ErrorAndSafetyTest.mapsHttpErrorsWithoutRetryOrSecretLeakage` | No se inventa un envelope de error del proveedor |
| P-ERROR-WIRE | DTO de OAuth y Shadow | Red, timeout, parseo y respuesta incompleta | Errores tipados sin falso estado | `ErrorAndSafetyTest` | No hay reintentos ni recuperación ambigua |
| U-SERVICE-DISCOVERY | E034, E040 y `QvLocationManager` | `discoverServices` | `Unsupported` | `EventAndUnsupportedTest.capabilitiesAndUnsupportedOperationsNeverClaimRuntimeSupport` | El descubrimiento dinámico no está resuelto |
| U-DEVICE-LIST | E004, E010, E020 | `listDevices` | `Unsupported` | `EventAndUnsupportedTest.capabilitiesAndUnsupportedOperationsNeverClaimRuntimeSupport` | Falta el contrato HTTP/XML completo de `getDevList` |
| U-REALTIME | E037 a E041, E063, E073 | `observeEvents` | `Unsupported` | El mismo test de capacidades | MQTT está presente pero desactivado por defecto y sin prueba dinámica |
| U-ACTIONS | E019, E023, E025 | `performAction` | `Unsupported` | El mismo test de capacidades | No hay wire cloud completo ni ack físico verificado |
| U-REGISTER | E004, E010, smali `com.quvii.qvweb.userauth` | Registro opaco y `userLogin` | Contrato cerrado por smali, sin operación expuesta | Sección 14 y documentación | Respuesta real del gateway pendiente de captura, el app de referencia nunca llegó a enviar el POST por el bloqueo TLS |
| U-MEDIA | E011, E021, E023, E042 | Audio y vídeo | No implementado | Revisión de capacidades | P2P nativo no reimplementado y binarios originales prohibidos |

`CloudCapabilities` solo anuncia OAuth password, OAuth refresh, Shadow HTTP, parser offline de alarmas y app upgrade check. Las demás capacidades quedan fuera del conjunto soportado.

## 3. Configuración y descubrimiento de servicio

`WiboxCloudConfig.baseUrl` es obligatorio y no tiene valor por defecto. Debe ser HTTPS, tener host, y no incluir userinfo, query ni fragmento. La fábrica no contiene un endpoint de producción ni uno de pruebas reales. El llamador debe aportar una base ya resuelta para OAuth y Shadow.

`discoverServices` devuelve `CloudError.Unsupported`. Las URLs regionales dependen de `QvLocationManager` y P2P según E034 y E040. El análisis no ha resuelto la respuesta dinámica que asigna los servicios 4 y 8, por lo que no se sustituye por el host bootstrap ni por una IP histórica.

Los timeouts de request, conexión y socket son configurables y positivos. Las redirecciones y el retry de conexión del motor están desactivados. TLS usa la validación normal de certificado y hostname de la plataforma, sin trust store propio ni excepciones.

## 4. OAuth

### 4.1 Password grant

Request:

```text
GET /qvoauthv2/token
token: <access token previo o cadena vacía>
```

Query en este orden:

```text
grant_type=password
client_id=<config>
client_type=<config>
oemid=<config>
appid=<config>
usr=<cuenta opaca>
pwd=<secreto de esa cuenta>
region_id=<config>
client_flag=<config>
```

Los valores se codifican como query URL. No se usa `Authorization` ni el esquema Bearer. El header `token` se envía siempre. En una autenticación inicial es una cadena vacía, y si existe sesión restaurada contiene su access token previo. Esta regla local evita ausencia del header exigido por el contrato de integración sin convertir el password grant en una autorización Bearer.

Response soportada:

```json
{
  "access_token": "<JWT>",
  "refresh_token": "<JWT>"
}
```

Se toleran campos JSON adicionales. Los dos tokens son obligatorios. Cada uno debe tener tres segmentos, payload Base64URL JSON y `exp` numérico positivo. Solo se decodifica la caducidad para gestionar la sesión. La validación de identidad y autorización corresponde al servidor y al canal TLS, no a una verificación local inventada de firma.

### 4.2 Refresh grant

```text
GET /qvoauthv2/token?grant_type=refresh_token&refresh_token=<token>
token: <access token actual>
```

La respuesta exige access y refresh token completos y sustituye ambos de forma atómica respecto a `TokenStore`. Una respuesta válida sin refresh token es `CloudError.Parse` y no pisa la sesión anterior.

### 4.3 Margen, single-flight y persistencia

Un token es utilizable solo si `exp - reloj > 300000 ms`. En el límite exacto de cinco minutos se renueva. Si el access token está dentro del margen pero el refresh token es utilizable, una operación Shadow comparte una única renovación en vuelo. Los llamadores concurrentes reciben el mismo resultado y hay un solo request y un solo `save`.

`refreshSession` fuerza refresh, mientras que las operaciones Shadow reutilizan un access token fresco. No se guardan usuario ni password para una segunda autenticación oculta. Si ambos tokens dejan de ser utilizables se produce `CloudError.MissingToken` antes de red.

`TokenStore` define `load`, `save` y `clear` suspending. La app debe proporcionar almacenamiento cifrado y escritura atómica. La librería no ofrece un almacén persistente inseguro. `CloudSession.toString` y `CloudCredentials.toString` redactan secretos, y los errores no incorporan cuerpos, URLs ni mensajes de transporte potencialmente sensibles.

## 5. Shadow HTTP

Las cuatro operaciones usan POST, JSON, header `token` con el access token y ningún parámetro query. Una respuesta de negocio solo se acepta con `code=0`. Un código distinto produce `CloudError.Provider` con el código numérico, sin copiar mensajes del servidor.

### 5.1 Estado individual

Ruta `/openapi-tdk/device/status`.

```json
{
  "thingId": "fixture-device-001",
  "fieldNames": ["Device_state"]
}
```

La respuesta exige `data.thingId` y `data.fields`. `Device_state` puede ser null. Los campos tipados son `F1_state`, `Switch_state`, `Private_mode_state`, `Fisheye_chn_state`, `Lock_state`, `Alarm_zone_mode` y `Smart_relay`. Los modelos públicos usan nombres Kotlin y conservan los enteros originales. Los campos desconocidos de estado, switches, locks, relés y `fields` se conservan como JSON en `unknownFields`. Los desconocidos del envelope se conservan en `responseUnknownFields`.

### 5.2 Estado múltiple

Ruta `/openapi-tdk/device/status/multi`. El body es un array de objetos `thingId` y `fieldNames`. La respuesta `data` es un array en el mismo modelo. No se rellenan estados ausentes ni se convierte `Device_state=null` en online, offline o éxito de acción.

### 5.3 Suscripción

Ruta `/openapi-tdk/device/status/sub`.

```json
{
  "appId": "2021",
  "clientId": "fixture-client",
  "subData": [
    {"thingId": "fixture-device-001", "fieldNames": ["Device_state"]}
  ]
}
```

Solo se solicita `Device_state`, el campo visto en el productor. El ack exige `code=0`, `path` y `timestamp`, conserva `data` opcional y campos desconocidos del envelope. No implica que exista un canal activo de eventos.

### 5.4 Desuscripción

Ruta `/openapi-tdk/device/status/unsub`. Usa el mismo envelope con `subData`. Cada entrada lleva `fieldNames=[]` y `doType`: 1 para `ACCOUNT_LOGOUT`, 2 para `DEVICE_UNBIND`. Son los valores presentes en las entidades del APK. No se les añade semántica de revocación OAuth o desvinculación física.

## 6. Eventos y alarmas

`parseAlarmEvent` procesa un `Map<String, String>` ya recibido por el llamador. Reconoce `AlarmEvent=2` como movimiento, 19 como llamada entrante y 20 como llamada atendida. Modela `AlarmId`, `DevUmid`, `SensorChNo`, `CameraName`, `NotReadCount`, `AlarmEvent` y `AlarmTime` según E056 y `AlarmMessageInfo`.

`AlarmEvent` y `NotReadCount` deben ser enteros. Un `SensorChNo` ausente o no numérico se representa como -1, igual que el parser estático. La fecha se conserva como texto porque no se ha fijado zona ni formato completo. Otros campos se conservan. Un código de alarma desconocido produce `Unsupported`, y un entero obligatorio inválido produce `Parse`.

`observeEvents` no es un flujo vacío ni un éxito simulado: al recolectarlo produce `CloudError.Unsupported`.

## 7. Errores y reintentos

| Error | Condición |
|---|---|
| `MissingToken` | No hay sesión o access y refresh no superan el margen |
| `Authentication` | HTTP 401 |
| `Authorization` | HTTP 403 |
| `Http` | Otro estado no 2xx |
| `Provider` | Respuesta Shadow con `code` distinto de 0 |
| `Network` | Fallo de transporte no temporal |
| `Timeout` | Timeout de request o socket |
| `Parse` | JSON inválido, JWT inválido o campos obligatorios incompletos |
| `Persistence` | Fallo de `TokenStore.load` o `save` |
| `InvalidInput` | Configuración, credenciales o IDs inválidos |
| `Unsupported` | Operación o evento sin contrato implementado |
| `Closed` | Operación posterior al cierre |

No se instala retry de Ktor y el motor no reintenta fallos de conexión. En particular, 401, 403, JSON inválido y errores de red generan un solo request. Las acciones físicas no llegan a red, por lo que una acción ambigua nunca se repite. No se informa éxito de una acción sin ack, y el ack de suscripción no se presenta como confirmación física.

`close` cancela el ámbito propio y cierra el cliente HTTP. Es idempotente y no borra ni revoca tokens.

## 8. Service discovery dinámico no resuelto

La selección regional y la obtención de servicios OAuth, Shadow y MQTT requieren el resultado dinámico de `QvLocationManager`. Falta el esquema de esa respuesta y su correlación runtime. Por eso no existe un bootstrap oculto ni un fallback a hosts históricos. La base URL inyectada es deliberada y visible en la configuración. Esta separación permite probar las operaciones conocidas sin afirmar que el aprovisionamiento del endpoint está terminado.

## 9. MQTT desactivado

El APK contiene Paho, topics, QoS 1, heartbeat y reconexión, pero `SUPPORT_DEVICE_SHADOW=false` impide la inicialización por defecto según E063 y E073. Las clases Paho no están completas en la salida JADX. No se implementa cliente MQTT, conexión, heartbeat ni replay. La suscripción HTTP no activa MQTT y `observeEvents` devuelve `Unsupported`.

## 10. P2P nativo no reimplementado

El vídeo y audio de stock dependen de túneles P2P nativos, puertos mapeados y URLs con credenciales. No se incluyen bibliotecas originales, URLs `quii`, SIP, WebSocket, WebRTC ni un reproductor falso. `MEDIA_P2P` no está soportado y las acciones físicas devuelven `Unsupported`.

## 11. Diferencias con duhow y cmos486

duhow documenta UART, CGI local y MQTT de firmware parcheado. Sus topics, comandos y respuestas no se mezclan con OAuth o Shadow del APK. El ack CGI o un publish local tampoco demuestra apertura física. Este módulo no adopta credenciales por defecto, canales planos ni secuencias temporizadas de ese firmware.

cmos486 describe otro firmware custom con RTSP, WebRTC y MQTT. Esas funciones no prueban el transporte de stock y no se reutilizan como contrato cloud. Tampoco se copian configuraciones, secretos o componentes nativos. REA se usa solo como herramienta de análisis, nunca como referencia de protocolo.

## 12. Evidencia que requiere captura autorizada

- Respuesta dinámica de service discovery y asociación de servicios regionales.
- Requests y responses runtime de OAuth, incluidos errores del proveedor y rotación real.
- Respuestas Shadow por versión y semántica de cada entero de estado.
- Confirmación de que suscribir por HTTP produce eventos y de qué canal, orden, duplicados y huecos.
- Contrato completo de `userFreeRegister`, `userLogin`, lista de dispositivos y credenciales dinámicas.
- Formato y zona de `AlarmTime`, deduplicación FCM y comportamiento en segundo plano.
- Comandos físicos, correlación y ack que distinga envío aceptado de efecto físico.
- Wire format P2P y alternativa FOSS de media sin cargar binarios originales.
- Respuestas no 200 de upgrade, errores del proveedor, semántica de decisión de actualización y comportamiento TLS estricto de producción.

Hasta disponer de esas capturas, las operaciones correspondientes permanecen no expuestas o devolverán `Unsupported`.

## 13. App upgrade check observado en attempt3

`DYN3-HTTP-UPGRADE-001` aporta request y HTTP 200 XML de éxito. El host y puerto observados permanecen en el inventario de evidencia y no se copian como default del cliente. `AppUpgradeRequest.baseUrl` es obligatorio y pasa la misma validación HTTPS estricta que el resto del cliente. `currentVersion` y `clientId` son explícitos. El appid stock es 2021 y el path es `/webs/version/app/version_check.jsp`.

```text
GET <upgrade-base>/webs/version/app/version_check.jsp
Accept-Language: en-US

os=android&appid=2021&version=<currentVersion>&clientid=<clientId>
```

No se envían `token`, `Authorization`, body ni cookies. Los valores query se codifican por URL. Solo se acepta HTTP 200 y `Content-Type: text/xml;charset=UTF-8`, normalizando espacios y mayúsculas. Otro Content-Type produce `CloudError.Parse` y otro estado produce el error HTTP tipado existente. No se afirma un contrato de error no observado.

`AppUpgradeInfo` contiene `flag`, `result`, `priority`, `version`, `url`, `description`, `configVersion`, `configUrl` y `configPicture`. No contiene una decisión `updateAvailable`. Las URLs y descripciones vacías se devuelven como cadenas vacías. Un `result` distinto de 0 produce `CloudError.Unsupported`, nunca un éxito parcial.

El parser usa DOM del JDK disponible en Android, sin una dependencia nueva. Limita el documento a 64 KiB, rechaza DOCTYPE, declaraciones de entidades y XInclude, desactiva expansión de entidades y acceso externo a DTD y esquemas, y valida todos los elementos requeridos y enteros. Los fallos producen `CloudError.Parse` sin incluir el XML ni estados parciales.

Las cabeceras `Set-Cookie` se ignoran. No se instala almacén de cookies, no se reenvían en llamadas posteriores y esta operación no lee ni escribe `TokenStore`. `AppUpgradeRequest.toString` redacta clientId y base URL, y no existe logging BODY.

La excepción `ssl_insecure` de attempt3 perteneció exclusivamente al proxy MITM de laboratorio por un upstream caducado. No se ha copiado al cliente. TLS conserva validación de certificado y hostname.

Bind, lista de dispositivos, media y acciones físicas siguen sin request y response suficientes y permanecen no expuestos o `Unsupported`. El registro y login disponen ahora del contrato estático completo de la sección 14, sin captura de respuesta real. OAuth y Shadow conservan su alcance estático, sin validación runtime afirmada.

## 14. Registro y login: contrato userauth observado en smali y verificado contra el servidor

La evidencia procede del análisis estático del APK oficial stock (`com.quvii.qvweb.userauth`) y de tráfico real capturado el 2026-11-01. La interceptación TLS de los hosts `r1` se logró parcheando el pinning propio del SDK (ver 14.1); con el canal abierto, un POST sintético obtuvo respuesta real del servidor con el formato exacto que declaraba el smali. El app de referencia sigue sin haber enviado el POST por sí mismo: su wizard de alta exige el timbre físico.

### 14.1 Endpoint y transporte

El envío usa POST contra `{baseUrl}/auth/user;jus_duplex=up` con `Content-Type: application/xml;charset=utf-8` y `User-Agent: okhttp/3.9.0`. El `baseUrl` lo aporta `DownChannelManager` (`https://host:port/`); el gateway servido es `https://r1-8.qvcloud.net:443/`. Dos requisitos verificados con tráfico real:

- **Cookie de sesión obligatoria**: sin `Cookie: jsessionid=...` el gateway responde 404 nginx plano. Con la cookie de la sesión del canal down del app, el mismo POST devuelve 200. El método del SDK se llama `getAuthInstanceWithCookie`, coherente con este requisito.
- **Patrón duplex**: el POST `jus_duplex=up` devuelve 200 con cuerpo vacío; la respuesta llega por el canal de bajada `GET /auth/nologin;jus_duplex=down` de la MISMA sesión, un long-poll que se renueva cada ~30 s. El servidor emite la sesión con `Set-Cookie: jsessionid=...` en el primer 200 del canal down. Otros comandos del canal usan `/auth/nologin;jus_duplex=up` y `/auth/user/deli;jus_duplex=up`.

El pinning TLS del SDK está en `OkHttpUtil.initSSL()` (método private static): `KeyStore BKS` con password `123456` cargado desde assets `client.bks` (clave de cliente, mTLS potencial) y `server.bks` (truststore con la CA de Qualvision), consumido por `createBuilder2()` que usa el flujo auth. Ese pinning es la causa de que mitmproxy fuera rechazado con `certificate_unknown` en `r1-*` pese a la CA de usuario instalada y a los parches OpenSSL nativos. La CA raíz embebida es `assets/ca.pem` (QUALVISION TECHNOLOGY CO.,LTD, 2017-2037).

### 14.2 Envelope de request

El cuerpo es XML serializado con simpleframework, raíz `envelope` sin modo estricto, con dos bloques:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<envelope>
  <header>...</header>
  <content>...</content>
</envelope>
```

Cabecera `header`:

| Campo | Registro (`userFreeRegister`) | Login (`userLogin`) |
|---|---|---|
| `flag` | `tdkcloud` | `tdkcloud` |
| `version` | valor servido por version_check, observado `5.1.11`, no va fija | igual |
| `command` | `dev-bind-register` | `login` |
| `seq` | `0` | `0` |
| `session` | vacía | vacía |
| `userData` | `authEncWay` | vacía |
| `client` | `id`, `type`, `oem`, `app` | igual |

El bloque `client` lleva `id` con el push token FCM, `type` con `3`, `oem` con `G0021` y `app` con `2021`. El formato de `clientid` observado en version_check es `003-2021-` seguido de un identificador hexadecimal aleatorio de 16 caracteres.

Contenido `content` de registro (constructor con cinco campos): `deviceId` con el umid, `deviceName`, `password`, `authCode` y `authEncWay` con valor `sha2-256` o `plain`. El flujo real `bindDevRegister` genera la contraseña con un UUID aleatorio, toma `authCode` del QR del timbre físico y fija `authEncWay` en `sha2-256`: es registro con vinculación de dispositivo, no alta de cuenta exenta de hardware.

Contenido `content` de login (constructor con cinco campos): `account`, `password`, `type`, `autchCode` (con este error ortográfico en el wire) e `ipRegionId` con la región IP actual.

### 14.3 Respuesta real observada

Capturada el 2026-11-01 en el canal down del app stock, en respuesta a un POST sintético autenticado con la sesión del canal:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<envelope>
  <header>
    <flag>tdkcloud</flag>
    <version>v1.24</version>
    <command>dev-bind-register</command>
    <seq>0</seq>
    <result>100009</result>
  </header>
  <content>
    <account></account>
    <dynamic-password></dynamic-password>
    <password-expired></password-expired>
    <data-encode-key></data-encode-key>
    <is-default-out-authcode>0</is-default-out-authcode>
    <default-out-auth-code></default-out-auth-code>
  </content>
</envelope>
```

Observaciones del wire: la versión del protocolo que declara el servidor en la respuesta es `v1.24` (distinta de la `5.1.11` del cliente); el servidor entregó la misma respuesta dos veces (duplicado del mensaje en dos ciclos de long-poll); el nombre de los elementos (`dynamic-password`, `data-encode-key`, `is-default-out-authcode`) casa 1:1 con los campos del `UserFreeRegisterResp$Content` del smali salvo normalización de nombres. El `result` distinto de cero se interpreta como error; `100009` no aparece como constante en el cliente (definido solo en el servidor).

Respuesta real de `login` (mismo día, POST con `account`=UDID de la etiqueta del dispositivo y la contraseña del dispositivo; rechazado):

```xml
<envelope>
  <header>
    <flag>tdkcloud</flag><version>v1.24</version>
    <command>login</command><seq>0</seq><result>100009</result>
  </header>
  <content>
    <account-id>0</account-id><nick></nick><expire>0</expire>
    <token></token><is-guest>0</is-guest><mfa-status>0</mfa-status><mfa-ttl>0</mfa-ttl>
  </content>
</envelope>
```

Que `dev-bind-register` y `login` devuelvan el mismo `100009` apunta a un código de error genérico (parámetros o sesión), no a un rechazo de credenciales específico. El UDID de la etiqueta no sirve como `account` de login: la cuenta es la que crea el registro del app, no el dispositivo.

### 14.4 Límites

El caso de éxito de registro no se ha observado. La secuencia de intentos reales (todos con `result=100009`): deviceId sintético hex8 y hex16 con authCode vacío; login con `account`=UDID de la etiqueta y contraseña del dispositivo; `dev-bind-register` con el UDID real `<UDID>` y `authCode=<AP_PASSWORD>` en `plain`; el mismo con `client.id`=token FCM real extraído de las prefs del app; sesión propia y fresca del canal `/auth/user` (duplex completo controlado por curl, respuesta recibida directamente en el down propio); y `authCode=SHA-256(<AP_PASSWORD>)` con `authEncWay=sha2-256` (el cifrado real es `QvEncrypt.EncodeDevicePassword`: si la cadena tiene 64 o más caracteres se devuelve tal cual, si no, SHA-256). El QR de la etiqueta decodifica a `<AP_SSID> <UDID> <AP_PASSWORD> IDS7938 <MAC>`: no lleva un campo authCode separado, el authCode del flujo es la contraseña del AP del propio QR.

Dado que el servidor devuelve el mismo `100009` para deviceIds inexistentes y para el dispositivo real (que está vinculado a la cuenta del usuario), la hipótesis más plausible es un rechazo genérico de registro, siendo "dispositivo ya vinculado a otra cuenta" la explicación pendiente de confirmar; descartarla requeriría desvincular el timbre de la cuenta real (acción destructiva no autorizada). El app no ofrece pantalla de login ni registro independiente: el alta de cuenta va 100% ligada al alta de un device físico. La operación sigue sin estar expuesta en la API pública de `core:cloud`.

### 14.5 La etiqueta del dispositivo

Todos los datos que pide el alta por red salen de la etiqueta pegada en la parte de atrás del módulo. No están en ninguna pantalla del app de fábrica ni se pueden deducir del resto del protocolo: hay que leerlos del aparato.

![Módulo Wi-Box de Fermax con la etiqueta de fábrica](img/wibox-hardware.webp)

La etiqueta lleva cuatro líneas y un QR que las agrupa:

- `AP Name`: nombre del punto de acceso que levanta el módulo mientras no está emparejado.
- `Password`: contraseña de ese punto de acceso. Es también el `authCode` del registro, como se explica en el apartado anterior.
- `MAC`: MAC del Wi-Fi del módulo.
- `UDID`: identificador del dispositivo.

En la tabla siguiente, los valores de la unidad fotografiada, que es un aparato distinto de la unidad usada en las capturas del apartado 14 (aquella tiene AP `<AP_SSID>` y UDID `<UDID>`):

| Campo | Valor |
|---|---|
| AP Name | `IDS7938pdef` |
| Password | `MTZBLNFS` |
| MAC | `7C:A7:B0:40:E2:79` |
| UDID | `tdks97tcpdef` |

El QR decodifica (comprobado con `zbarimg --raw`) a `IDS7938pdef tdks97tcpdef MTZBLNFS IDS7938 7CA7B040E279`, es decir, cinco campos separados por espacios en el orden `AP Name UDID Password SSID MAC`. Es el mismo orden que se dedujo del QR de la unidad del apartado 14, lo que refuerza la lectura: el `authCode` del registro es la contraseña del AP y el `UDID` va en segundo lugar, no como `account` de login.

Foto de [segator/wibox-media](https://github.com/segator/wibox-media), fichero `docs/img/wibox-hardware.webp` (GPL-3.0), copiada sin recodificar: `sha256 f26bdd36e0357c6e25857621a0326df267c1c30936854d4e63657e7652890dc3`.

## 15. Invitación de compartición: forma real del enlace

El enlace que la app oficial imprime en la pantalla "Nuevo usuario" se capturó de una pantalla real y se decodificó con `zbarimg`. Su forma es esta:

```
https://share.qvcloud.net/webs/invite_nologin/page_<OEM>/index.jsp
  ?InviteCode=<8 caracteres>
  &OwnerId=<numérico>
  &OemId=<OEM>
  &DeviceId=<udid del dispositivo>
  &From=QrInvite
  &AppId=<app>
```

Tres detalles que no se deducen del análisis estático:

- **No lleva `AppVersion`.** El smali lee ese parámetro, pero el enlace que la app genera no lo incluye. Exigirlo rechaza todas las invitaciones reales, que es lo que hacía esta app antes del arreglo del issue #12. El campo se trata como opcional y su ausencia se traduce a cadena vacía.
- `From` vale `QrInvite`, no el valor que aparece en los ejemplos del APK.
- `DeviceId` es el UDID del dispositivo del propietario, no el identificador del AP principal, y `OwnerId` es la cuenta del propietario.

El enlace se puede abrir en la app propia porque el parser acepta esa query completa. Fuera de la app no resuelve: el dominio sirve una página del fabricante y el alta exige un `accept` por el canal userauth que se describe en la sección 14.

## 16. Alta por invitación: contrato del accept y resultado 100004

Sección de evidencia sobre lo que no está resuelto, para que no se repita el trabajo de prueba y error.

### 16.1 La versión del protocolo en la petición

El envelope de petición lleva un elemento `version` que no es el número de versión de la app (5.1.11) sino la versión del protocolo, `v1.24`, la misma que el servidor declara en sus respuestas. Con `5.1.11` todas las variantes de `dev-bind-register` devolvían `100009`. Con `v1.24` el resultado pasa a `100004` en todas las variantes probadas. Hecho medido contra el servidor real, no deducido del smali.

### 16.2 Contrato del accept: el wire usa XStream, no los nombres Java del smali

El comando se llama `accept-nologin-share-invitation`. La cadena del app oficial, de arriba abajo: `DADeviceBindPresenter.bindDevice(name, DeviceShareInfo)` valida el alias y llama a `DeviceHelper.bindShareDevice`, que acaba en `QvUserAuthCore.noLoginShareInvitationAccept(uid, shareCode, name, from, needCreate, password)` y emite `POST /auth/nologin;jus_duplex=up`.

Punto crítico verificado con tráfico real: el smali muestra los nombres Java camelCase (`deviceId`, `code`, `name`, `way`, `needCreate`), pero el serializador del app oficial es **XStream**, que escribe esos campos con **alias kebab-case** distintos. Un request construido con los nombres Java es rechazado por el servidor. Los nombres REALES del wire, capturados en un accept con `result=0`:

| Elemento XML del wire | Valor |
|---|---|
| `share-code` | `InviteCode` de la invitación |
| `device-id` | UID del dispositivo del QR |
| `share-device-memo` | Alias libre que escribe el usuario |
| `need-create` | 1 cuando la app no tiene usuario propio |
| `password` | UUID nuevo, solo en ese caso de cuenta nueva |
| `way-from` | `From` de la invitación, es decir `QrInvite` |

El `content` lleva además el atributo `class` con el bean (`com.quvii.qvweb.userauth.bean.request.NoLoginShareAcceptReqContent`), va ANTES que el `header`, y el `header` lleva `user-data` (kebab) y `version` VACÍO. No hay paso previo de consulta: la app emite el accept directamente.

### 16.3 El 100004 era el formato XML, no el dispositivo

La secuencia medida contra el servidor real: con `version=5.1.11` todos los comandos devolvían `100009` (versión de protocolo incorrecta). Con `version=v1.24` el accept devolvía `100004`. Se descartó que el `100004` fuera caducidad de la invitación (un código fresco también lo daba) o que el dispositivo estuviera ya vinculado.

La causa real, confirmada al capturar el accept correcto del app oficial: el `100004` lo producía el request con los nombres de campo Java (`code`, `deviceId`, `name`, `way`) en lugar de los alias XStream (`share-code`, `device-id`, `share-device-memo`, `way-from`). El servidor no reconocía los campos y rechazaba el alta. Con el formato XStream correcto, el mismo accept devuelve `result=0`.

### 16.4 Accept con éxito capturado

Se capturó un accept real con `result=0` ejecutando el app oficial en el emulador y escaneando una invitación con una webcam real (`-camera-back webcam0`; la cámara virtual `v4l2loopback` no entregó imagen al escáner). El request y la respuesta reales están en la sección 14 junto al resto del contrato duplex. La respuesta de éxito crea la cuenta nologin y devuelve `account`, `id`, `name`, `data-encode-key`, `dynamic-password`, `password-expired`, `transparent-basedata`, `powers` y `model`.

## 17. Verificación

```bash
python3 core/cloud/verify_upgrade_contract.py
python3 core/cloud/verify_protocol.py
python3 core/cloud/verify_no_secrets.py
./gradlew :core:cloud:test
```

Los resultados medidos, el recuento de tests y las limitaciones se registran en `core/cloud/verification.txt`.
