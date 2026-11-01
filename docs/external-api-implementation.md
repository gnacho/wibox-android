# Implementación de la API local para otras apps

Este documento describe la implementación real del servicio Binder/AIDL expuesto por Wibox a otras apps. El diseño de referencia es `docs/external-api.md` y el contrato congelado es `analysis/external-api/api-contract.json`. La verificación automática vive en `analysis/external-api-runtime/verify_external_api_runtime.py`.

## Qué se implementó

- Servicio `dev.nacho.wibox.externalapi.WiboxExternalApiService` en el módulo `app`, que devuelve una instancia de `IWiboxExternalService.Stub` por conexión. Registrado en `app/src/main/AndroidManifest.xml` como servicio `exported="true"` con la acción `dev.nacho.wibox.externalapi.BIND` y sin atributo `android:permission`: la autorización se decide por llamada, no por un permiso declarativo.
- Motor de autorización puro en el módulo `external-api`, paquete `dev.nacho.wibox.externalapi.server`: resolución de identidad, allowlist, revocación y límites de solicitudes pendientes. Es Kotlin sin Android, cubierto por tests unitarios.
- Persistencia de la allowlist en la app con `SharedPreferencesAuthorizationStore`, esquema versionado (v1) y escritura atómica con `commit()`. No guarda secretos: uid, package, digest del conjunto de firmantes, scopes, dispositivos, generación, nombre visible y estado.
- Runtime del servicio (`ExternalApiRuntime`) con límites de ráfaga por uid (`TokenBucket`), presupuesto de operaciones one-shot por app y globales, y un journal acotado de acciones para deduplicación, cancelación y consulta de resultados.
- Mappers propios en la app de `core.cloud` a los parcelables Wire (`ExternalApiMappers`), con etiquetado honesto: origen y `simulated=true` cuando el provider es demo.
- SDK Kotlin ya existente en `external-api/src/main/kotlin/.../sdk/` sin cambios de contrato.
- Tests: 24 unitarios en `external-api` (autorización completa y hash de firmantes) y 3 instrumentados en `app/src/androidTest` que ejercitan el servicio Binder real.

## Cómo autoriza el servicio

En cada transacción Binder el servicio deriva la identidad del llamante, nunca la acepta del cliente:

1. Obtiene el uid real con `Binder.getCallingUid()` dentro de la transacción. Las corrutinas en segundo plano reciben esa identidad capturada, no vuelven a preguntar.
2. Rechaza uids por debajo de `FIRST_APPLICATION_UID` y el rango de uids aislados (99000..99999).
3. Resuelve los packages del uid con `PackageManager.getPackagesForUid(uid)`. Más de un package termina en `SHARED_UID_UNSUPPORTED`.
4. El package reclamado en `ClientHello` se contrasta con el derivado del uid. Desajuste termina en `PACKAGE_UID_MISMATCH`.
5. Lee los certificados firmantes actuales con `PackageInfo.signingInfo` y `GET_SIGNING_CERTIFICATES` (`apkContentsSigners`), calcula SHA-256 en hex minúsculo sin separadores de cada certificado, ordena el conjunto y calcula el digest final del conjunto ordenado. Dos conjuntos de firmantes distintos producen siempre dos digests distintos.
6. Busca una entrada activa con la tupla exacta (uid, package, digest de firmantes) en la allowlist. La ausencia de entrada deniega: denegación por defecto.
7. Comprueba el scope pedido y, si la operación va ligada a un dispositivo, que ese dispositivo esté en la lista concedida. Una lista vacía significa acceso a cero dispositivos.

La primera operación válida de cada conexión es `handshake`; el resto devuelve `HANDSHAKE_REQUIRED`. El handshake negocia `minor = min(cliente, servidor)` con major 1 y devuelve el estado de autorización actual sin conceder nada. Por debajo de Android 9 el handshake devuelve `UNSUPPORTED`, porque `SigningInfo` no existe antes de la API 28.

## Cómo se revoca

La revocación, la aprobación y el deniego son decisiones del usuario desde Ajustes de Wibox y se materializan en el motor: `approve`, `deny` y `revoke` sobre la tupla (uid, package, digest). `revoke` incrementa la generación del grant y marca el estado `REVOKED`. Como el servicio revalida la tupla en cada llamada, la revocación aplica a la siguiente llamada sin necesidad de notificaciones. `unsubscribeEvents` y la cancelación de operaciones propias quedan disponibles para limpieza. El historial de firmas no autoriza por sí solo: un cambio de firma produce `SIGNATURE_CHANGED` y exige reaprobación, igual que un cambio de uid produce `UID_CHANGED`.

Una app no autorizada puede llamar a `requestAuthorization` y queda `PENDING` con sus scopes y dispositivos solicitados. Ningún camino de la API concede acceso por sí mismo: la aprobación solo existe en el motor, invocada desde Ajustes. Los límites de solicitudes pendientes son 3 por package y 50 globales; el exceso responde `RATE_LIMITED`.

## Límites reales aplicados

- Request 32 KiB y response 64 KiB, aplicados por el presupuesto de `BoundedParcelable`. El batch de eventos queda acotado a 10 eventos y 48 KiB por su propio parcelable.
- Páginas: dispositivos y capabilities limitados a 20 elementos por petición (el parcelable `PageRequest` limita el `limit` a 20 y las páginas de respuesta validan su máximo). Batch de sombras de 20 consultas. IDs de dispositivo de 128 bytes UTF-8. Sin truncado silencioso.
- Operaciones one-shot pendientes: 20 por app y 100 globales; el exceso responde `BUSY`.
- Suscripciones de eventos: 4 por app, comprobadas en el registro. Hoy es inalcanzable porque realtime no está disponible, pero el camino existe para providers futuros.
- Lecturas: 30 por minuto con ráfaga de 10 por uid. Acciones: 6 por minuto con ráfaga de 2 por uid. El exceso responde `RATE_LIMITED` con `retryAfterMs` acotado.
- Request ID de acción duplicado por la misma identidad devuelve la operación pendiente o el resultado registrado, nunca re-ejecuta. Reutilización con otro payload devuelve `CONFLICT`. Los resultados de acciones quedan en un journal acotado a 256 entradas con limpieza de las terminadas.

## Qué queda Unsupported y por qué

- `subscribeEvents`: devuelve `UNSUPPORTED` (o `CAPABILITY_UNKNOWN` si el provider no tiene fila en el catálogo). Ningún provider del repo ofrece hoy un stream realtime con cursores exactos, y devolver un stream conectado falso rompería la honestidad de gaps y reanudación. El catálogo marca `events.realtime` como `unsupported`.
- `checkAppUpgrade` en demo: el repositorio lanza `ProviderError.Unsupported` porque no existe configuración real de upgrade en la demo local. Se mapea a `UNSUPPORTED` sin inventar versiones.
- `listDevices` y `getDevice` en un provider stock: delegan en el puerto de devices, cuyo comportamiento por defecto lanza `ProviderError.Unsupported` que se mapea a `UNSUPPORTED`. En demo funcionan con etiquetado `simulated=true` y origen `DEMO`, tal como permite el contrato (`unsupported_now` con simulación demo permitida).
- Media, P2P, llamadas por Binder, login, pairing y configuración: no expuestos en v1, sin método externo.

Las sombras (`getDeviceShadow` y `getDeviceShadows`) delegan en el puerto de devices. En demo devuelven el estado local etiquetado como simulado. Los campos desconocidos se describen con nombre y tipo observado, nunca con su valor.

## Cómo probarlo

Comandos de verificación, todos ejecutados contra el emulador `emulator-5558` cuando aplica:

```bash
# Verificador runtime (G1)
python3 analysis/external-api-runtime/verify_external_api_runtime.py

# Tests unitarios, lint y build (G2)
./gradlew :external-api:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Tests instrumentados, incluida la denegación por defecto (G3)
ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest
```

El test instrumentado `ExternalApiDenyByDefaultTest` se vincula al servicio con la firma de la propia app y sin entrada en la allowlist, y comprueba que handshake funciona sin grant, que `requestAuthorization` queda `PENDING` sin acceso, que `getDevice`, `getDeviceShadow` y `performAction` devuelven `NOT_AUTHORIZED`, y que toda llamada sin handshake devuelve `HANDSHAKE_REQUIRED`.

Para una integración externa real: la app consumidora se vincula al componente explícito `dev.nacho.wibox.externalapi.WiboxExternalApiService`, hace handshake, llama a `requestAuthorization` y espera la aprobación en Ajustes de Wibox. La aprobación programática del motor (`approve`/`revoke`) todavía no tiene pantalla propia: es la pieza E7 del plan de implementación del diseño.

## Desviaciones del diseño original

- La app declara `tools:overrideLibrary="dev.nacho.wibox.externalapi"` para mantener su `minSdk 26` junto a la librería con `minSdk 28`. El diseño exige API 28 para `SigningInfo`; el servicio lo cubre devolviendo `UNSUPPORTED` en el handshake por debajo de Android 9.
- `app/build.gradle.kts` añade `implementation(project(":external-api"))`: el servicio necesita las clases Stub del contrato AIDL y los tipos Wire del módulo.
- El motor de autorización vive en `external-api/src/main/kotlin/.../server/` en lugar de un módulo futuro `core:external-auth`: mantiene la pieza testeable sin Android y sin crear un módulo nuevo en esta hoja.

## Estado

Servicio, allowlist revocable, denegación por defecto, límites, journal de acciones y pruebas con dos firmas: implementados y verificados con evidencia medida. La UI de Ajustes para aprobar solicitudes y la suite con dos apps de prueba con firmas distintas reales quedan para las fases E7 y E8 del diseño.
