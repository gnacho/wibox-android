# Evidencia de modernización de UI

Complemento de `docs/ui-modernization.md`: evidencia verificable de navegación, capturas, accesibilidad y límites. La auditoría completa queda en `analysis/ui-modernization/` (manifest, route-coverage y capturas reales del emulador).

## Superficies y navegación

- Inventario de referencia: 38 entradas y 71 aristas (`analysis/apk/ui-inventory.json`).
- Implementadas: 32 superficies visuales + relay `cache` (sin UI, cubierto por tests instrumentados). 5 pantallas legacy sin navegación de entrada quedan omitidas y documentadas: `guide`, `product_choose`, `add_method_legacy`, `wifi_list_legacy`, `wifi_set_legacy`.
- Las 71 aristas declaradas en `NavigationEdges.kt` coinciden en pares `from/to` con las del inventario; el grafo de sesión es `session(loading, cache)`, con `main` (4 pestañas + lista de eventos), `pairing` (12 pantallas), `device` (6), `sharing` (2), `media` (2) y `system` (3).
- Estados cubiertos por `LoadState` (loading con datos previos, error con datos obsoletos, stale, refreshing) y escenarios demo (`LOADING`, `EMPTY`, `ERROR`, `OFFLINE`, `UNSUPPORTED`), verificados por `StateAndInvitationInstrumentedTest` y `ViewModelsTest`.

## Capturas reales

- 32 capturas tomadas de la app en ejecución en el emulador (`emulator-5558`, AVD `wibox-api37`, Android API 37, 1080x2400, font scale 1.0, tema claro, modo demo), mediante el test instrumentado `CaptureSurfacesTest` (12/12 PASS) y `UiDevice.takeScreenshot`. Sin imágenes fabricadas.
- Manifiesto con SHA-256, tamaño, fecha, serial, API, idioma, tema y modo: `analysis/ui-modernization/manifest.json`. Cobertura de rutas: `analysis/ui-modernization/route-coverage.json` (33 destinos: 32 visuales + cache).
- El banner de proveedor (`LOCAL DEMO`) es visible en las capturas y nunca se recorta.

## Contraste medido (WCAG)

Umbral: 4.5:1 texto normal, 3.0:1 texto grande, icono esencial o borde con función. Los valores se calculan sobre los hex de `ui/Theme.kt` (`LightScheme` y `DarkScheme`) con la fórmula de luminancia relativa de WCAG 2.1, no sobre la paleta anterior al rediseño.

### Tema claro (`LightScheme`)

| Par | Ratio | Umbral |
|---|---|---|
| `#171C1F` onSurface sobre `#F5FAFD` surface | 16.34 | 4.5 |
| `#171C1F` onSurface sobre `#E9EEF2` surfaceContainer | 14.71 | 4.5 |
| `#171C1F` onSurface sobre `#DEE3E7` surfaceContainerHighest | 13.29 | 4.5 |
| `#40484D` onSurfaceVariant sobre `#F5FAFD` surface | 8.87 | 4.5 |
| `#40484D` onSurfaceVariant sobre `#E9EEF2` surfaceContainer | 7.98 | 4.5 |
| `#FFFFFF` onPrimary sobre `#006F8E` primary | 5.73 | 4.5 |
| `#001F2A` onPrimaryContainer sobre `#BDEAFB` primaryContainer | 13.28 | 4.5 |
| `#071E26` onSecondaryContainer sobre `#CFE6F1` secondaryContainer | 13.28 | 4.5 |
| `#006F8E` primary sobre `#F5FAFD` surface | 5.45 | 3.0 |
| `#BA1A1A` error sobre `#F5FAFD` surface | 6.15 | 4.5 |
| `#70787D` outline sobre `#F5FAFD` surface | 4.28 | 3.0 |
| `#ECF1F4` inverseOnSurface sobre `#2C3134` inverseSurface | 11.56 | 4.5 |

### Tema oscuro (`DarkScheme`)

| Par | Ratio | Umbral |
|---|---|---|
| `#DEE3E7` onSurface sobre `#0F1417` surface | 14.34 | 4.5 |
| `#DEE3E7` onSurface sobre `#1B2023` surfaceContainer | 12.72 | 4.5 |
| `#DEE3E7` onSurface sobre `#303639` surfaceContainerHighest | 9.49 | 4.5 |
| `#C0C8CD` onSurfaceVariant sobre `#0F1417` surface | 10.93 | 4.5 |
| `#C0C8CD` onSurfaceVariant sobre `#1B2023` surfaceContainer | 9.69 | 4.5 |
| `#00363F` onPrimary sobre `#86D2E9` primary | 7.76 | 4.5 |
| `#BDEAFB` onPrimaryContainer sobre `#004E5D` primaryContainer | 7.26 | 4.5 |
| `#CFE6F1` onSecondaryContainer sobre `#344A53` secondaryContainer | 7.22 | 4.5 |
| `#86D2E9` primary sobre `#0F1417` surface | 10.95 | 3.0 |
| `#FFB4AB` error sobre `#0F1417` surface | 10.92 | 4.5 |
| `#8A9297` outline sobre `#0F1417` surface | 5.86 | 3.0 |
| `#2C3134` inverseOnSurface sobre `#DEE3E7` inverseSurface | 10.18 | 4.5 |

Los 24 pares superan su umbral. El más ajustado es `outline` sobre `surface` en claro, con 4.28 frente al 3.0 exigido a un borde con función, y `onPrimary` sobre `primary`, con 5.73 frente a 4.5.

El azul de marca se conserva como `primary` `#006F8E` con texto blanco porque ese par mide 5.73, que sí pasa. La paleta anterior usaba blanco sobre `#78c9e7`, que no llegaba al umbral de texto normal.

Objetivos táctiles de 48dp en botones y filas de opción (`WiboxButton`, `WiboxOptionRow`, `WiboxSwitchRow`, `SmallIconButton`).

## Cómo se regeneran las capturas

```bash
adb -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5558 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w \
  -e class dev.nacho.wibox.CaptureSurfacesTest \
  dev.nacho.wibox.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5558 pull /sdcard/Android/data/dev.nacho.wibox/files/captures/. \
  analysis/ui-modernization/captures/
python3 analysis/ui-modernization/verify_ui_modernization.py
```

Hay que instalar los dos APK a mano: `connectedDebugAndroidTest` desinstala la app al terminar y con ella borra `files/captures`. El manifiesto se regenera recalculando `sha256`, `bytes`, `size_px`, `captured_at`, `serial` y `android_api` desde los ficheros descargados, con la ruta base `analysis/ui-modernization` más el `file` de cada entrada, que ya empieza por `captures/`.

`captureDeviceFlow` falla de forma intermitente porque la semántica se estabiliza antes que los píxeles. Relanzar el test lo resuelve y no indica una regresión. Cuando se toque ese test, conviene añadir la espera de frame dentro del propio flujo.

## Matriz de tamaños

| Configuración | Evidencia |
|---|---|
| 360dp aprox, font 1.0, tema claro, retrato (1080x2400) | Lote base: 32 capturas |
| 600/840dp y landscape | Fuera del lote base; layouts con rail y paneles existen y están cubiertos por tests, sin captura dedicada |
| 320dp, font 2.0, RTL | Fuera del lote base; documentado como límite, no como verificado |

## Límites documentados

- Landscape y accesibilidad avanzada (320dp, font 2.0, RTL, tema oscuro) no cuentan con evidencia medida en este lote: la rotación recrea la actividad y el repositorio demo, por lo que se deja fuera del lote base de forma honesta en lugar de simular capturas.
- El tema oscuro no tiene capturas propias en el lote, pero sus pares están medidos sobre los hex de `DarkScheme` en la tabla de arriba.
- Las 5 pantallas legacy omitidas no tienen captura porque no tienen navegación de entrada; su reactivación exigiría decisión funcional y pruebas propias.
