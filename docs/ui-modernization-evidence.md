# UI modernization evidence

Companion to `docs/ui-modernization.md`: verifiable evidence of navigation, captures, accessibility and limits. The full audit remains in `analysis/ui-modernization/` (manifest, route-coverage and real captures from the emulator).

## Surfaces and navigation

- Reference inventory: 38 entries and 71 edges (`analysis/apk/ui-inventory.json`).
- Implemented: 32 visual surfaces + `cache` relay (no UI, covered by instrumented tests). 5 legacy screens with no inbound navigation are omitted and documented: `guide`, `product_choose`, `add_method_legacy`, `wifi_list_legacy`, `wifi_set_legacy`.
- The 71 edges declared in `NavigationEdges.kt` match the inventory's `from/to` pairs; the session graph is `session(loading, cache)`, with `main` (4 tabs + event list), `pairing` (12 screens), `device` (6), `sharing` (2), `media` (2) and `system` (3).
- States covered by `LoadState` (loading with previous data, error with stale data, stale, refreshing) and demo scenarios (`LOADING`, `EMPTY`, `ERROR`, `OFFLINE`, `UNSUPPORTED`), verified by `StateAndInvitationInstrumentedTest` and `ViewModelsTest`.

## Real captures

- 32 captures taken from the app running on the emulator (`emulator-5558`, AVD `wibox-api37`, Android API 37, 1080x2400, font scale 1.0, light theme, demo mode), through the `CaptureSurfacesTest` instrumented test (12/12 PASS) and `UiDevice.takeScreenshot`. No fabricated images.
- Manifest with SHA-256, size, date, serial, API, language, theme and mode: `analysis/ui-modernization/manifest.json`. Route coverage: `analysis/ui-modernization/route-coverage.json` (33 destinations: 32 visual + cache).
- The provider banner (`LOCAL DEMO`) is visible in the captures and is never cropped.

## Measured contrast (WCAG)

Threshold: 4.5:1 normal text, 3.0:1 large text, essential icon or border with a function. The values are computed from the hex values in `ui/Theme.kt` (`LightScheme` and `DarkScheme`) with the WCAG 2.1 relative luminance formula, not from the palette prior to the redesign.

### Light theme (`LightScheme`)

| Pair | Ratio | Threshold |
|---|---|---|
| `#171C1F` onSurface on `#F5FAFD` surface | 16.34 | 4.5 |
| `#171C1F` onSurface on `#E9EEF2` surfaceContainer | 14.71 | 4.5 |
| `#171C1F` onSurface on `#DEE3E7` surfaceContainerHighest | 13.29 | 4.5 |
| `#40484D` onSurfaceVariant on `#F5FAFD` surface | 8.87 | 4.5 |
| `#40484D` onSurfaceVariant on `#E9EEF2` surfaceContainer | 7.98 | 4.5 |
| `#FFFFFF` onPrimary on `#006F8E` primary | 5.73 | 4.5 |
| `#001F2A` onPrimaryContainer on `#BDEAFB` primaryContainer | 13.28 | 4.5 |
| `#071E26` onSecondaryContainer on `#CFE6F1` secondaryContainer | 13.28 | 4.5 |
| `#006F8E` primary on `#F5FAFD` surface | 5.45 | 3.0 |
| `#BA1A1A` error on `#F5FAFD` surface | 6.15 | 4.5 |
| `#70787D` outline on `#F5FAFD` surface | 4.28 | 3.0 |
| `#ECF1F4` inverseOnSurface on `#2C3134` inverseSurface | 11.56 | 4.5 |

### Dark theme (`DarkScheme`)

| Pair | Ratio | Threshold |
|---|---|---|
| `#DEE3E7` onSurface on `#0F1417` surface | 14.34 | 4.5 |
| `#DEE3E7` onSurface on `#1B2023` surfaceContainer | 12.72 | 4.5 |
| `#DEE3E7` onSurface on `#303639` surfaceContainerHighest | 9.49 | 4.5 |
| `#C0C8CD` onSurfaceVariant on `#0F1417` surface | 10.93 | 4.5 |
| `#C0C8CD` onSurfaceVariant on `#1B2023` surfaceContainer | 9.69 | 4.5 |
| `#00363F` onPrimary on `#86D2E9` primary | 7.76 | 4.5 |
| `#BDEAFB` onPrimaryContainer on `#004E5D` primaryContainer | 7.26 | 4.5 |
| `#CFE6F1` onSecondaryContainer on `#344A53` secondaryContainer | 7.22 | 4.5 |
| `#86D2E9` primary on `#0F1417` surface | 10.95 | 3.0 |
| `#FFB4AB` error on `#0F1417` surface | 10.92 | 4.5 |
| `#8A9297` outline on `#0F1417` surface | 5.86 | 3.0 |
| `#2C3134` inverseOnSurface on `#DEE3E7` inverseSurface | 10.18 | 4.5 |

All 24 pairs pass their threshold. The tightest is `outline` on `surface` in light, at 4.28 against the 3.0 required for a border with a function, and `onPrimary` on `primary`, at 5.73 against 4.5.

The brand blue is kept as `primary` `#006F8E` with white text because that pair measures 5.73, which does pass. The previous palette used white on `#78c9e7`, which did not reach the normal text threshold.

Touch targets of 48dp on buttons and option rows (`WiboxButton`, `WiboxOptionRow`, `WiboxSwitchRow`, `SmallIconButton`).

## How the captures are regenerated

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

Both APKs have to be installed by hand: `connectedDebugAndroidTest` uninstalls the app at the end and with it deletes `files/captures`. The manifest is regenerated by recomputing `sha256`, `bytes`, `size_px`, `captured_at`, `serial` and `android_api` from the downloaded files, with the base path `analysis/ui-modernization` plus the `file` of each entry, which already starts with `captures/`.

`captureDeviceFlow` fails intermittently because the semantics stabilize before the pixels. Relaunching the test solves it and does not indicate a regression. When that test is touched, it is worth adding the frame wait inside the flow itself.

## Size matrix

| Configuration | Evidence |
|---|---|
| 360dp approx, font 1.0, light theme, portrait (1080x2400) | Base batch: 32 captures |
| 600/840dp and landscape | Outside the base batch, layouts with rail and panels exist and are covered by tests, without a dedicated capture |
| 320dp, font 2.0, RTL | Outside the base batch, documented as a limit, not as verified |

## Documented limits

- Landscape and advanced accessibility (320dp, font 2.0, RTL, dark theme) have no measured evidence in this batch: rotation recreates the activity and the demo repository, so it is left out of the base batch honestly instead of simulating captures.
- The dark theme has no captures of its own in the batch, but its pairs are measured on the `DarkScheme` hex values in the table above.
- The 5 omitted legacy screens have no capture because they have no inbound navigation, reactivating them would require a functional decision and their own tests.
