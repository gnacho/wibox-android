# Android implementation and visual correspondence

Implementation and verification date: 2026-10-31. Sheet leaf-1.2.2.

## 1. Scope and functional honesty

The application is a real Android app written in Kotlin with Jetpack Compose. It starts with a brief cover and continues to the four main tabs, with no invented login. Navigation, forms, actions and states run against a local repository. It is not a collection of images nor a static mockup.

The mode is identified as `LOCAL DEMO MODE` on the main screens and is explained in Settings. `FakeWiboxCloud` implements the public `WiboxCloud` interface and returns deterministic data. Its acknowledgements contain `local-demo-ack-no-physical-action`. The UI presents them as local acknowledgements, never as opening, video, connection or compatibility with the original cloud.

No real services are contacted and there are no original credentials. The manifest declares only `CAMERA`, and it is requested at runtime when the user presses the button on the scan screen: if the user denies it, the manual field keeps working. Microphone, location, biometrics and WiFi access are not requested. Physical actions are not retried automatically.

## 2. Architecture

- `MainActivity`: creates the repository and mounts Compose.
- `WiboxApp`: theme, `NavHost`, navigation effects and snackbars.
- `ui/Routes.kt`: typed routes and coverage of the 33 reachable screens or inventory representations. The five legacy screens are left out.
- `ui/AppViewModel.kt`: immutable state of tabs, devices, events, gallery, settings, selection and actions.
- `ui/AddDeviceViewModel.kt`: pairing draft, parsing, simulated local discovery, bind and pairing state machine.
- `ui/PreviewViewModel.kt`: simulated preview, one or four panels, local audio, capture, recording and actions.
- `data/FakeWiboxCloud.kt`: local implementation of the whole public cloud interface. Methods without a local equivalent return `CloudError.Unsupported`.
- `data/WiboxRepository.kt`: boundary between ViewModels, fake and persistence.
- `data/LocalDemoStore.kt`: preferences `DataStore` with a versioned JSON snapshot. `InMemoryDemoStore` isolates the tests.
- `data/DeviceCodeParser.kt`: QR and invitation validation, including UTF-8 encoding and duplicate rejection.

The app only imports the public API of `dev.nacho.wibox.core.cloud`. It does not import Ktor, OkHttp, internal DTOs, APK packages or decompiled code.

## 3. Inventory to implementation matrix

The test column distinguishes automated tests from visual evidence. `RoutesTest` checks route coverage, it does not by itself certify the behaviour of a screen.

| Original inventory | Compose route or representation | Resource or replacement | States covered | Test or capture |
|---|---|---|---|---|
| `loading` | `loading`, `LoadingScreen` | Original `start.png` | 450 ms cover and transition to main | `MainActivityInstrumentedTest` |
| `cache` | Representation without UI inside the `MainActivity` startup | None, same as the original | No screen of its own. There is no push relay in local mode | `MainActivityInstrumentedTest` |
| `main_tab` | `main`, `MainScreen` | Own header and bottom tabs with original icons | Four selections, edit with hidden tabs, rail on wide screens | `MainActivityInstrumentedTest`, captures 01 to 04 |
| `add_type` | `add_type`, `AddTypeScreen` | Own rectangular `option_button` | New and online | `LocalFlowInstrumentedTest`, [06-add-device](../fastlane/06-add-device.png) |
| `preview` | `preview/{deviceId}`, `PreviewScreen` | Black preview, own Material vectors | Connecting, simulated playing, stopped, offline, unsupported, one/four panels, portrait/landscape, listen, talk, recording, capture and local actions | `PreviewViewModelTest`, `StateAndInvitationInstrumentedTest`, [05-preview](../fastlane/05-preview.png) |
| `online_type` | `online_type`, `OnlineTypeScreen` | Own rectangular buttons | Simulated local search and manual | `RoutesTest`, `AddDeviceViewModelTest` |
| `product_choose` | Omitted | `legacy_no_inbound` | No static entry in the original | `omittedLegacyScreens` in `RoutesTest` |
| `product_intro` | `product_intro`, `ProductIntroScreen` | Original `reset_device_img.png` | Instructions and manual step. Permissions are not faked | `LocalFlowInstrumentedTest` |
| `handle_wifi` | `handle_wifi`, `HandleWifiScreen` | Own text and copy icon | Instructions, demo AP, copy and continue | `RoutesTest` |
| `wifi_list_legacy` | Omitted | `legacy_no_inbound` | No static entry | `omittedLegacyScreens` |
| `wifi_set_legacy` | Omitted | `legacy_no_inbound` | Only hangs from the legacy list | `omittedLegacyScreens` |
| `device_bind` | `device_bind`, `DeviceBindScreen(shared=false)` | Own rectangular fields | Manual, QR blocked, validation, duplicate, binding and persistent pairing | `AddDeviceViewModelTest`, `LocalFlowInstrumentedTest` |
| `online_devices` | `online_devices`, `OnlineDevicesScreen` | Own rows and device icon | Loading, results, empty and error | `AddDeviceViewModelTest` |
| `scan` | `scan`, `ScanCodeScreen` | Camera card over tonal surface, framing frame and manual field below | Scanning with CameraX and ZXing without Google Play Services, runtime permission with prior reason, denial that keeps the manual field, and manual paste | `DeviceCodeParserTest`, `QrDecoderTest`, instrumented pairing and invitation flows |
| `config` | `config/{deviceId}`, `DeviceConfigScreen` | 50 dp rows, Material switches and option dialogs with radios | Owner, shared, alarms, editable values, deletion, local sync, the three stock display modes (high, low and photo), Intercom Way (full duplex and hold to talk), F1 time from 1 to 10 s and date and time synchronization with visible result | `AppViewModelTest`, `SecondaryFlowsInstrumentedTest`, [07-config](../fastlane/07-config.png) |
| `password_modify` | `password_modify/{deviceId}/{mode}`, `PasswordModifyScreen` | Own form | Device, admin, unlock, empty fields, mismatch and local validation without storing secrets | `RoutesTest` |
| `unlock_setting` | `unlock_setting/{deviceId}`, `UnlockSettingScreen` | Own switches | Persistent quick on/off and persistent biometric confirmation in `AppPreferences`, requested by the system only when it is active | `AppViewModelTest` |
| `device_info` | `device_info/{deviceId}`, `DeviceInfoScreen` | Own form | Read-only UID, editing, duplicate and save | `AppViewModelTest` |
| `about` | `about`, `AboutScreen` | Original `logo.png` | Local version, local privacy and five-tap gesture | `SecondaryFlowsInstrumentedTest` |
| `image_pager` | `image_pager/{mediaId}`, `ImagePagerScreen` | Honest media placeholders, no fake photos | White, black, page, navigation, share unsupported and deletion | `SecondaryFlowsInstrumentedTest` |
| `video_player` | `video_player/{mediaId}`, `VideoPlayerScreen` | Own controls over a black panel | Playing, paused, muted, seeking and visible/hidden controls, all simulated | `SecondaryFlowsInstrumentedTest` |
| `push_call` | `push_call/{deviceId}`, `PushCallScreen` | Original `backgroud_calling.png`, `btn_accept.png`, `btn_refuse.png`, `ico_logo.png` | Local ringing, vibration, accept, reject and close at 30 s | `SecondaryFlowsInstrumentedTest`, [08-call](../fastlane/08-call.png) |
| `debug` | `debug`, `DebugScreen` | Own rows | Fake type, absence of token, scenarios, reset and loading restart | `SecondaryFlowsInstrumentedTest` |
| `upgrade` | `upgrade/{deviceId}`, `UpgradeScreen` | Own rows and buttons | Local query, up-to-date and upgrade disabled | `RoutesTest` |
| `guide` | Omitted | `legacy_no_inbound` | Original loading always opens main | `omittedLegacyScreens` |
| `add_method_legacy` | Omitted | `legacy_no_inbound` | No static entry | `omittedLegacyScreens` |
| `router` | `router?modify={modify}`, `RouterScreen` | Own WiFi list and form | Selection, visible/hidden password, save preference without persisting secrets, new mode and modification | `LocalFlowInstrumentedTest` |
| `voice` | `voice`, `VoiceProvisioningScreen` | Original `device_add_sound_waver.png` | Sending, waiting and ready. No audio emitted | `AddDeviceViewModelTest` |
| `wifi_progress` | `wifi_progress`, `WifiProvisioningScreen` | Original `device_add_wifi_set.png` | Connecting, sending, waiting, ready and manual failure | `AddDeviceViewModelTest`, `LocalFlowInstrumentedTest` |
| `share_device` | `share_device/{invitationId}`, `ShareDeviceScreen` | Real QR code on white background, link, code, expiry and granted permissions | QR generated with ZXing that the app's own scanner reads, link, Android chooser, expiry and invitation permission list | `SecondaryFlowsInstrumentedTest`, `QrEncoderTest` |
| `pairing_fail` | `pairing_fail`, `PairingFailScreen` | Own text and button | Simulated error and return to main | `AddDeviceViewModelTest` |
| `share_manage` | `share_manage/{deviceId}`, `ShareManageScreen` | Own invitation rows and user dialog | Loading, empty, list, create with name and permissions, edit with name and permissions, delete, limit and error | `SecondaryFlowsInstrumentedTest`, `ViewModelsTest` |
| `share_bind` | `share_bind`, `DeviceBindScreen(shared=true)` | Own form with locked UID/code | Valid invitation, duplicate, acceptance and shared pairing | `StateAndInvitationInstrumentedTest` |
| `tab_devices` | `main` content, `DevicesScreen` | Original `device_cover_default.png`, online/offline states | Loading, empty, list, offline, disabled actions, local result and confirmation with the system credential before opening or triggering F1 when the setting is active | `LocalFlowInstrumentedTest`, [01-devices](../fastlane/01-devices.png) |
| `tab_events` | `main` content, `EventsScreen` | Own Material icons | Loading, empty, list, warning, edit, selection and deletion | `AppViewModelTest`, [02-events](../fastlane/02-events.png) |
| `tab_event_list` | `EventRow` and pagination inside `EventsScreen` | Own 55 dp rows | Empty, manual refresh, load more every 15 and editing | `AppViewModelTest` |
| `tab_gallery` | `main` content, `GalleryScreen` | Three-column grid with local placeholders | Empty, grid, edit, selected, select all, save/share and delete | `AppViewModelTest`, [03-gallery](../fastlane/03-gallery.png) |
| `tab_settings` | `main` content, `SettingsScreen` | Material switches | Persistent sound/vibration, about, scenarios, reset and exit | `MainActivityInstrumentedTest`, [04-settings](../fastlane/04-settings.png) |

## 4. Original resources and replacements

Only 20 representative images were copied from `analysis/apk/resources/drawable-xhdpi` to `app/src/main/res/drawable-xhdpi`:

- `start.png`, `device_cover_default.png`, `reset_device_img.png`.
- `backgroud_calling.png`, `btn_accept.png`, `btn_refuse.png`, `ico_logo.png`, `logo.png`.
- `device_add_sound_waver.png`, `device_add_wifi_set.png`.
- Eight normal and selected tab icons: `main_btn_device*`, `main_btn_call_log*`, `main_btn_file*`, `main_btn_setting*`.
- `device_status_online.png` and `device_status_offline.png`.

Their origin and hashes can be checked in `analysis/apk/resources/manifest.json`. For example, `start.png` keeps SHA-256 `53a1229992f5df3e8039d3cd760c7dfbb18bb1f1235a574e8e1ceb7e9ae8648f` and `device_cover_default.png` keeps `429e0a1ae5172d9017752de81876c5ad4b7df38012fe071bdecd111099ce3b40`. No fonts, certificates, credentials, native binaries, complete layouts or JADX trees were copied.

The rest of the icons are own vectors with Material geometry or simple equivalents. They replace actions, chevrons and controls that were not necessary to copy. The cover and the call controls do reuse original resources because of their visual weight.

## 5. Visual system and accessibility

- Fixed palette: `#78c9e7`, background `#f4f4f4`, text `#4d4d4d`, inactive tab `#929292`, selection `#00a3dd`, error `#e73e04` and black preview.
- System sans-serif. Titles of 20 sp, buttons of 18 sp and tabs of 12 sp.
- Header of 60 dp, tabs of 55 dp and buttons of at least 50 dp. Rows can grow if the text needs it so as not to cut content with larger font scales.
- Covers of 200 dp, rectangular spacing and inventory hierarchy. No dashboard or a different generic Material theme was added.
- There is no dark theme of its own. Black is limited to preview and viewers.
- Insets, system back and touch targets of at least 48 dp are respected. At widths of 600 dp or more, the tabs become a rail so as not to stretch a phone bar.
- There are 213 localized keys in English and Spanish. Untranslated original strings were deliberately translated, for example `TODA LA ACTIVIDAD` into Spanish.
- The icons have content descriptions or are accompanied by text. Stable tags follow the prefix of each screen and action.

The eight captures were inspected at 1080x2400 with startup, tabs, preview, pairing, configuration and call states. Devices and pairing were also checked with `font_scale=1.3`, restoring the original `1.0` value afterwards. No overlaps or clipping were observed in that evidence. This check is not the same as a dynamic comparison with the original APK, for which there are no runtime captures in the inventory.

## 6. Cross-cutting states and tests

| State | Representation | Evidence |
|---|---|---|
| Loading | Cover and `LoadingContent` in lists and pairing | LOADING scenario in instrumentation |
| Content | Reactive and persisted local fixtures | 36 unit tests, 45 in `core:cloud` and E2E flow |
| Empty | Specific buttons/messages in devices, events, gallery and users | EMPTY scenario and invitation deletion |
| Error | Typed message, `#e73e04` colour and manual retry | ERROR scenario and `CloudError` tests |
| Disabled | Offline, insufficient permissions, upgrade and system credential unavailable | `StateAndInvitationInstrumentedTest`, device captures |
| Unsupported | Explicit text, no success or retry | UNSUPPORTED scenario and fake/ViewModels tests |

The app has 36 unit tests in five files: code parser, routes, ViewModels and the QR encoder and decoder. `core:cloud` adds 45 in seven files. There are 24 instrumented tests in six classes: real activity, local E2E, states, invitation, secondary flows, external API and surface capture. All use in-memory repositories or temporary DataStore and none uses the network.

Reference commands:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks
python3 scripts/verify_architecture.py
./gradlew :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest
python3 scripts/verify_project.py
```

`ANDROID_SERIAL` is mandatory in this environment. All ADB actions were done with `adb -s emulator-5558`, on the AVD `wibox-api37`. Nothing was installed or run on the physical phone except the manual test of the app, which was done with the debug APK.

## 7. Real captures

All come from the debug app running on `emulator-5558`, Android 17/API 37, at 1080x2400. They are generated by the `CaptureSurfacesTest` instrumented test with `UiDevice.takeScreenshot`, and those of the full inventory are in `analysis/ui-modernization/captures/` with their hash manifest.

1. [Devices](../fastlane/01-devices.png)
2. [Events](../fastlane/02-events.png)
3. [Gallery](../fastlane/03-gallery.png)
4. [Settings](../fastlane/04-settings.png)
5. [Simulated preview](../fastlane/05-preview.png)
6. [Device pairing](../fastlane/06-add-device.png)
7. [Device configuration](../fastlane/07-config.png)
8. [Simulated call](../fastlane/08-call.png)

## 8. Deliberate differences and limitations

- Scanning uses CameraX with `com.google.zxing:core`, which decodes on the device itself and does not talk to Google Play Services. It opens with the rear camera if there is one and, if not, with the front one. The front camera case is not theoretical: the emulated AVD declares its rear camera as `LENS_FACING_FRONT` and CameraX rejects the binding if the rear one is requested blindly, hence the selection is done with `hasCamera(DEFAULT_BACK_CAMERA)` instead of a fixed `selector`. In the windowless AVD the virtual scene delivers black frames and there is nothing to decode: that is an emulator limitation, not a reader one, and decoding is covered by `QrDecoderTest`.
- The sticker QR code brings five fields (`apName udid password ssid mac`) and the documented factory token brings four. The parser accepts both forms and stores `ssid` and `mac` when present. That is the only data used for pairing: the QR code does not carry cloud credentials.
- Invitations only exist in the local snapshot and their link points to `example.invalid`, so the domain does not resolve in a browser. The QR code is scannable: ZXing generates it with error correction M and a four-module quiet zone, and this same app's scanner reads it and interprets it as an invitation. What it does not do is work outside the app.
- The QR code is drawn with the modules aligned to whole pixels on a fixed white background, also in dark theme, because a QR code on a dark surface cannot be read.
- Preview and gallery use labelled synthetic placeholders. They contain no original video, photos or audio. Capturing or recording adds a local entry, not a real media file.
- AP, LAN, audio and WiFi are local state machines. They do not change the phone network, do not emit sound and do not prove pairing compatibility.
- Call acceptance opens local preview. It does not register FCM nor confirm a real call.
- Form passwords are not persisted. Non-sensitive settings, devices, events, gallery and invitations do use DataStore.
- Biometric confirmation does not use `BiometricPrompt`: it opens the system credential screen (`KeyguardManager.createConfirmDeviceCredentialIntent`), which asks for fingerprint, PIN or pattern. If the phone has no credential configured, the intent returns null and the action runs without a gate, which is the same behaviour the system would have. Time synchronization writes a local mark on the demo device, it does not talk to the doorbell. Firmware remains disabled and there are no real updates.
- Device settings replicate the rows of the original: name, passwords, opening, Intercom Way, display mode with the three stock values, Wi-Fi, users, F1 time from 1 to 10 s, call reception, motion alerts, firmware version, synchronize time and delete. The `Lock Number` row of the original is hidden with `visibility="gone"` and is not replicated.
- The local banner and the scenario selector are necessary differences to prevent a demo from being confused with an operational connection.
- The five `legacy_no_inbound` screens are omitted because they have no inbound navigation in the current inventory.
- The fidelity claimed is structural and visual against static layouts and resources. Cloud equivalence, device compatibility or dynamic visual equivalence are not declared without evidence.
