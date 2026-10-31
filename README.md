# Wibox Kotlin

Reimplementación Android en Kotlin de los flujos de Wibox (videoportero), construida a partir del análisis de referencias públicas y del APK de fábrica. La arquitectura es provider-neutral: la aplicación no conoce el cloud directamente, solo el puerto `WiboxProvider`, y los límites de cada proveedor se declaran por capacidad en lugar de fingirse soportados.

## Estructura

- `core/cloud`: puerto provider-neutral (`WiboxProvider`), API tipada, modelos de dominio, autenticación OAuth, operaciones Shadow HTTP, `app_upgrade_check` y parsers de eventos con evidencia. No contiene UI.
- `app`: aplicación Android con Jetpack Compose modernizada (32 superficies visuales, 71 aristas de navegación, capturas reales en emulador). Depende de la API pública de `core:cloud` y del módulo `external-api` para el servicio Binder.
- `external-api`: API local Binder/AIDL para que otras apps consulten proveedores Wibox: 15 métodos, 43 modelos, SDK Kotlin main-safe y motor de autorización propio.
- `analysis/rea`, `analysis/apk`, `analysis/dynamic`, `analysis/ui-modernization`, `analysis/final-audit`: inventarios y verificadores con evidencia.
- `docs/protocol.md`, `docs/provider-architecture.md`, `docs/provider-implementation.md`, `docs/external-api.md`, `docs/external-api-implementation.md`, `docs/ui-modernization-evidence.md`: contratos y decisiones con trazabilidad.
- `fastlane`: capturas y metadatos de la aplicación.

## Estado funcional

Adaptador stock QVCloud: solo se implementan operaciones con contrato observado. OAuth (`password`/`refresh_token`), Shadow HTTP (`status`, `multi`, `sub`, `unsub`), alarmas FCM (tipos 2, 19, 20) y `app_upgrade_check` (única operación con request y respuesta dinámicos observados). Descubrimiento regional, listado unificado de dispositivos, suscripción realtime, media P2P y acciones físicas devuelven errores `Unsupported` explícitos: falta evidencia de contrato dinámico y no hay hardware verificado.

Proveedor demo local: implementa los mismos flujos con datos sintéticos etiquetados (`simulated=true`, origen demo) para probar navegación, estados y acciones con ack local honesto.

API externa para otras apps: servicio Binder (`dev.nacho.wibox.externalapi`) con denegación por defecto, autorización por UID + paquete + SHA-256 del conjunto de firmantes con allowlist revocable, handshake obligatorio, límites de tasa y tamaño, y Unsupported honesto heredado de las capacidades del proveedor. Los acks nunca superan lo realmente observado (máximo `LOCAL_ACCEPTED` en demo).

## Requisitos

- JDK 17.
- Android SDK en `~/Android/Sdk` o configurado mediante `ANDROID_HOME`.
- Platform Android 37.0 y Build Tools 37.0.0.
- Para pruebas instrumentadas, un emulador o dispositivo Android visible mediante ADB.

`local.properties`, credenciales y tokens no se versionan.

## Comandos

```bash
./gradlew :core:cloud:test :external-api:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
python3 analysis/final-audit/verify_final_audit.py
python3 analysis/final-audit/verify_release.py
```

Los tests usan respuestas simuladas y no contactan con el cloud real. El selector `ANDROID_SERIAL` es obligatorio en tests instrumentados si hay otros dispositivos conectados.

## Verificación

```bash
python3 scripts/verify_architecture.py
python3 analysis/ui-modernization/verify_ui_modernization.py
python3 analysis/external-api-runtime/verify_external_api_runtime.py
python3 analysis/final-audit/verify_final_audit.py
python3 analysis/final-audit/verify_release.py
python3 scripts/verify_project.py
```

La auditoría final cubre 64 capacidades, 38 pantallas y 71 aristas de navegación con límites documentados. La verificación de release comprueba firma, alineación, instalación, arranque y captura de la APK.

## Límites honestos

- Compatibilidad cloud declarada por capacidad: la única operación stock con contrato dinámico observado es `app_upgrade_check`.
- Sin hardware real verificado: llamadas, preview y acciones físicas no están certificadas.
- Realtime, discovery, media y acciones físicas permanecen `Unsupported` hasta disponer de evidencia dinámica autorizada.
- La API externa no expone secretos ni promete operaciones que el proveedor activo no soporte.
