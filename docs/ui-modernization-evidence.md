# Evidencia de modernización de UI

Complemento de `docs/ui-modernization.md`: evidencia verificable de navegación, capturas, accesibilidad y límites. La auditoría completa queda en `analysis/ui-modernization/` (manifest, route-coverage y capturas reales del emulador).

## Superficies y navegación

- Inventario de referencia: 38 entradas y 71 aristas (`analysis/apk/ui-inventory.json`).
- Implementadas: 32 superficies visuales + relay `cache` (sin UI, cubierto por tests instrumentados). 5 pantallas legacy sin navegación de entrada quedan omitidas y documentadas: `guide`, `product_choose`, `add_method_legacy`, `wifi_list_legacy`, `wifi_set_legacy`.
- Las 71 aristas declaradas en `NavigationEdges.kt` coinciden en pares `from/to` con las del inventario; el grafo de sesión es `session(loading, cache)`, con `main` (4 pestañas + lista de eventos), `pairing` (12 pantallas), `device` (6), `sharing` (2), `media` (2) y `system` (3).
- Estados cubiertos por `LoadState` (loading con datos previos, error con datos obsoletos, stale, refreshing) y escenarios demo (`LOADING`, `EMPTY`, `ERROR`, `OFFLINE`, `UNSUPPORTED`), verificados por `StateAndInvitationInstrumentedTest` y `ViewModelsTest`.

## Capturas reales

- 32 capturas tomadas de la app en ejecución en el emulador (`emulator-5554`, Android API 37, 1080x2400, font scale 1.0, tema claro, modo demo), mediante el test instrumentado `CaptureSurfacesTest` (12/12 PASS) y `UiDevice.takeScreenshot`. Sin imágenes fabricadas.
- Manifiesto con SHA-256, tamaño, fecha, serial, API, idioma, tema y modo: `analysis/ui-modernization/manifest.json`. Cobertura de rutas: `analysis/ui-modernization/route-coverage.json` (33 destinos: 32 visuales + cache).
- El banner de proveedor (`LOCAL DEMO`) es visible en las capturas y nunca se recorta.

## Contraste medido (WCAG)

Umbral: 4.5:1 texto normal, 3.0:1 texto grande o estado. Paleta clara de `Theme.kt`.

| Par | Ratio | Umbral |
|---|---|---|
| text (#4D4D4D) sobre surface (#FFFFFF) | 8.45 | >= 4.5 |
| text sobre background (#F4F4F4) | 7.69 | >= 4.5 |
| secondaryText (#666666) sobre surface | 5.74 | >= 4.5 |
| onPrimary (#FFFFFF) sobre primary (#006F8E) | 5.73 | >= 4.5 |
| bannerText (#4D4D4D) sobre banner (#DFF3FA) | 7.38 | >= 4.5 |
| selected (#007A9E) sobre surface | 4.91 | >= 3.0 |
| error (#A92F04) sobre surface | 6.78 | >= 3.0 |
| onPrimary sobre selected (#007A9E) | 4.91 | >= 3.0 |

Todos los pares medidos superan su umbral. Objetivos táctiles de 48dp en botones y filas de opción (`WiboxButton`, `WiboxOptionRow`, `WiboxSwitchRow`, `SmallIconButton`).

## Matriz de tamaños

| Configuración | Evidencia |
|---|---|
| 360dp aprox, font 1.0, tema claro, retrato (1080x2400) | Lote base: 32 capturas |
| 600/840dp y landscape | Fuera del lote base; layouts con rail y paneles existen y están cubiertos por tests, sin captura dedicada |
| 320dp, font 2.0, RTL | Fuera del lote base; documentado como límite, no como verificado |

## Límites documentados

- Landscape y accesibilidad avanzada (320dp, font 2.0, RTL, tema oscuro) no cuentan con evidencia medida en este lote: la rotación recrea la actividad y el repositorio demo, por lo que se deja fuera del lote base de forma honesta en lugar de simular capturas.
- El tema oscuro comparte pares equivalentes de paleta pero no se ha medido en esta evidencia.
- Las 5 pantallas legacy omitidas no tienen captura porque no tienen navegación de entrada; su reactivación exigiría decisión funcional y pruebas propias.
