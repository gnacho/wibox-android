# Wibox UI modernization

## 1. Goal and limits

The UI can be modernized without keeping a pixel-by-pixel clone. The main restriction becomes functional: keep the 33 reachable non-legacy screens or representations, their states, actions, navigation and necessary resources, and document the 5 legacy screens without a current entry. QVCloud/Quvii compatibility and honesty about what is not implemented take priority over visual finish.

This document is design and acceptance criteria. It does not describe new captures already produced nor a later Compose implementation. The current captures cited in `docs/ui-implementation.md` belong to the existing demo, they do not prove the changes proposed here nor compatibility with the original APK.

Reference inventory: `analysis/apk/ui-inventory.json` contains 38 entries and 71 edges. Of the 33 non-legacy ones, `cache` is a relay without UI. Therefore, the later implementation must cover 33 non-legacy destinations or representations, with 32 visual surfaces and one relay, plus 5 documented legacy specifications.

## 2. Diagnosis of the current Compose implementation

The current base already has Compose, routes for the 33 non-legacy representations, demo states and local mode labels. It also has limitations that a real modernization must solve:

1. `WiboxApp` concentrates a single `NavHost` and creates three long-lived ViewModels for all flows. The pairing draft, the global state and the preview share a life beyond their screen.
2. `WiboxRepository` is built on `FakeWiboxCloud`. The ViewModels receive `DemoDevice`, `DemoSnapshot` and `DemoScenario`, so the presentation model is coupled to fixtures.
3. `LoadingScreen` navigates after 450 ms. It does not represent session restoration, discovery, provider preparation or startup error.
4. Navigation uses strings and manual arguments. It is readable, but it needs explicit rules for process death, deep links, duplicate calls and return from subflows.
5. The demo completes pairing and preview through timers. Its `PLAYING_SIMULATED` texts and local ack are honest, but those states must not be reused as stock success states.
6. Some features keep the shape, not the content: placeholders instead of media, metadata instead of capture or recording and synthetic links instead of real invitations. Modernization must keep those gaps visible until they are closed. QR scanning is no longer in this list: the camera reads the codes with CameraX and ZXing, and the manual field remains as an alternative.
7. The current palette reproduces white text on `#78c9e7`. It must not be taken as accessible without measuring contrast. Modernizing allows choosing dark content or a darker primary colour.
8. The demo load tolerant to damaged data is not valid for real data. A corrupt snapshot cannot silently reset the installation to fixtures.

## 3. Proposed Compose architecture

### 3.1 Graphs and navigation

Keep a small root `NavHost` and separate graphs with stable routes and typed arguments:

| Graph | Destinations | Responsibility |
|---|---|---|
| `session` | `loading`, `cache` relay, connection decision | Restore, prepare provider, resolve initial push and errors |
| `main` | `main_tab`, `tab_devices`, `tab_events`, `tab_event_list`, `tab_gallery`, `tab_settings` | Four tabs, state of each tab and editing |
| `pairing` | `add_type`, `online_type`, `product_intro`, `scan`, `online_devices`, `handle_wifi`, `router`, `voice`, `wifi_progress`, `device_bind`, `share_bind`, `pairing_fail` | Single pairing draft, permissions and cancelled return |
| `device` | `preview`, `config`, `password_modify`, `unlock_setting`, `device_info`, `router` in modification, `upgrade` | Selected device and channel, actions and settings |
| `sharing` | `share_manage`, `share_device`, acceptance from QR | Invitations, permissions and navigation between accounts |
| `media` | `image_pager`, `video_player` | Real local media and player state |
| `system` | `push_call`, `about`, `debug` | Incoming call, information and restricted diagnostics |

Rules:

- Stable IDs for `deviceId`, `channelId`, `mediaId`, `invitationId` and `callId`. Do not encode secrets in routes or deep links.
- `SavedStateHandle` keeps identity and position, not tokens, passwords or `CredentialMaterial`.
- Each tab keeps scroll, pagination, selection and loading state when switching tabs or processes.
- `router` is a shared destination with a `modify` argument, not two divergent implementations in the pairing and device graphs. Its result returns to the right origin.
- Pairing has a single result `bound`, `cancelled`, `failed` or `outcomeUnknown`. Going back leaves no zombie draft nor active pairing.
- Accepting a call consumes a `callId` only once. Rotation and recomposition do not accept or reject again.
- The back stack is tested with the 71 original edges as reference. A grouping change is valid only if it keeps destinations, actions and predictable exit, and has a test.
- The five legacy screens are not inserted for aesthetics. Reactivating `guide`, `product_choose`, `add_method_legacy`, `wifi_list_legacy` or `wifi_set_legacy` requires a functional decision, an explicit entry and its tests.

### 3.2 ViewModels and presentation models

| ViewModel | Scope | State it owns |
|---|---|---|
| `SessionViewModel` | Session graph | Provider, demo/real mode, preparation, error and pending call |
| `DevicesViewModel` | `tab_devices` | List, freshness, presence, refresh and pending actions |
| `EventsViewModel` | Events | Pages, visible filters, photos, selection and gaps |
| `GalleryViewModel` | Gallery | Files, selection, pager and media operations |
| `PairingViewModel` | Pairing graph | Non-secret draft, stage, permissions, candidates and result |
| `DeviceDetailViewModel` | One device | Detail, Shadow, capabilities, configuration and errors |
| `PreviewViewModel` | One `MediaSessionHandle` | Panels, channel, playback, audio, capture, recording and actions |
| `CallViewModel` | One `callId` | Ringing, accept, reject, timeout and answered elsewhere |
| `SharingViewModel` | One device | Invitations, permissions, limit and ephemeral links |
| `SettingsViewModel` | Settings | Preferences, privacy, diagnostics and provider |

Composables receive immutable state and events. They do not call repositories, do not parse QR codes, do not know DTOs and do not decide support. A `UiMapper` translates provider-neutral models into presentation models. `Demo*` is confined to the demo adapter.

Physical actions run in the ViewModel of the corresponding device through a common `ActionCoordinator`. This coordinator prevents duplicates by `commandId`, keeps `ActionOutcome` and stops two screens from repeating an opening when recomposed.

### 3.3 Maintainable visual design

- Compose Material 3 as the component base, with own tokens for colour, typography, elevation, states and dimensions. Not a generic Material theme overlaid without review.
- Common components: `ProviderStatusBanner`, `LoadContent`, `DeviceCard`, `CapabilityAction`, `ActionOutcomeText`, `PermissionRationale`, `PairingProgress`, `MediaControls`, `SelectionBar` and `ErrorPanel`.
- Original icons only when they are traced in `analysis/apk/resources/manifest.json` and are necessary for identity or action. For new iconography, Lucide converted to a vector or the Android standard is preferred if no equivalent exists. Binaries, certificates and secret resources are not reused.
- The identity can keep the Wibox blue, but content on primary is decided by measured contrast. White text on `#78c9e7` is not fixed if it does not reach AA.
- Four tabs on phone and `NavigationRail` at sufficient width. Do not turn the app into a new dashboard nor remove the pairing, device and gallery hierarchy.
- Preview and viewers keep a dark surface for media legibility. The rest is not darkened automatically without a contrast suite.

## 4. Full inventory and modernized destination

Table conventions:

- The original column summarizes functionality, all the state IDs of the inventory and resources or actions that cannot be lost.
- The current situation refers to the existing demo app, not to the original APK.
- The tests and captures named are future obligations. `CAP-*` does not imply that the file exists.
- `legacy_documented` keeps the specification outside the main graph, without claiming it is reachable today.

| ID | Original functionality, states and resources | Current situation | Modernized destination | Required evidence and test |
|---|---|---|---|---|
| `loading` | Entry and restoration. States: `splash`, `storage_permission`, `phone_permission`, `startup_wait`, `push_redirect`. Resource `start.png`. Navigates to `main_tab`, `push_call`, `preview`. | 450 ms cover and jump to main. There is no session nor real permissions. | `SessionScreen` with real preparation, recoverable error and deep link, brief cover only if it does not delay startup. | `SessionViewModelTest` for wait/error/push and storage/phone permissions without undue blocking, `NavigationTest` for three destinations, `CAP-loading-splash` and `CAP-loading-error` on the emulator. |
| `cache` | Push relay without layout. States: `relay`, `finish`. Navigates to `loading`. | Representation inside `MainActivity`, without a real relay. | `PushRelay` without UI, with validated extras and immediate close. | `PushRelayTest` to copy only allowed data and finish. No capture because it has no UI. |
| `main_tab` | Four tabs and dialogs. States: `devices_selected`, `events_selected`, `gallery_selected`, `settings_selected`, `terms`, `android_hint`, `edit_mode`. Normal/selected tab resources. Navigates to `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings`. | Four tabs, wide rail and editing with hidden tabs. Terms and Android notice are not real. | `MainScaffold` with state per tab, terms and actionable notices only when they apply. | `MainNavigationTest`, tab and selection restoration, `CAP-main-tabs`, `CAP-main-edit`, `CAP-main-terms`. |
| `add_type` | Choose new or online pairing. States: `new_device`, `online_device`. `option_button` style. Navigates to `product_intro`, `online_type`. | Both options exist. | `AddTypeScreen` in the pairing graph, with clear entries and provider capability. | `PairingNavigationTest` for two branches, `CAP-add-type`. |
| `preview` | Video and actions. States: `empty`, `connecting`, `playing`, `stopped`, `refresh`, `password_error`, `offline`, `one_window`, `four_windows`, `portrait`, `landscape`, `listening`, `talking`, `recording`, `snapshot`, `unlock_progress`, `unlock_success`, `unlock_failure`, `f1`, `timeout`, `no_permission`. Controls play, channel, quality, listen, talk, capture, recording, unlock/F1. Navigates to `config`, `password_modify`. | Honest simulation with panels, controls and local actions, no P2P, real files or physical ack. | `PreviewScreen` over `MediaSessionHandle`, controls per capability and results per ack level. Keep one/four panels and rotation. | `PreviewViewModelTest` for all states, `MediaContractTest`, `ActionOutcomeTest`, `CAP-preview-one`, `CAP-preview-four`, `CAP-preview-landscape`, `CAP-preview-offline`, `CAP-preview-unsupported`, `CAP-preview-action-pending`, `CAP-preview-ack`. Later hardware test for media/actions. |
| `online_type` | Search online or manual pairing. States: `scan_online`, `manual`. Navigates to `online_devices`, `device_bind`. | Both routes exist, simulated search. | `OnlineTypeScreen` with real LAN origin or labelled demo. | `PairingNavigationTest`, `DiscoveryStateTest`, `CAP-online-type`. |
| `product_choose` | Wi-Fi Doorbell 7410 and Monitor 9440 selector. States: `doorbell`, `monitor`. Resources `device_type_7410`, `device_type_9440`. Navigates to `product_intro`. `legacy_no_inbound`. | Omitted as legacy. | `legacy_documented`: keep models, states and resources in the specification, do not add an entry without evidence of need. | `LegacyCoverageTest` to prevent accidental deletion. If reactivated: `PairingNavigationTest` and `CAP-product-choose`. |
| `product_intro` | Reset and access to QR/WiFi. States: `reset_instructions`, `location_permission`, `camera_permission`, `scan_error`, `wifi_switch_error`. Resource `reset_device_img`. Navigates to `scan`, `router`, `handle_wifi`. | Instructions and manual step, no real permissions or camera. | `ProductIntroScreen` with just-in-time permissions and recovery to settings without blocking non-dependent routes. | `PermissionFlowTest` for location/camera, `PairingNavigationTest`, `CAP-product-intro`, `CAP-product-intro-permission`. |
| `handle_wifi` | Manual step to the AP. States: `instructions`, `copied`, `ap_mismatch`, `ap_connected`. Resources `prompt_setup_wifi`, `icon_copy`. Navigates to `router`, `wifi_progress`. | Text, copy and demo continuation. | `HandleWifiScreen` with real SSID detection when it exists and a safe manual exit. | `WifiSelectionTest` for copy/mismatch/connected, `CAP-handle-wifi-mismatch`. |
| `wifi_list_legacy` | Legacy WiFi list. States: `loading`, `list`, `error`, `password`, `connect_failure`, `success`. Resource `wifi_0`. Navigates to `wifi_set_legacy`. `legacy_no_inbound`. | Omitted as legacy. | `legacy_documented`: do not merge with `router` without keeping its six states in the specification. | `LegacyCoverageTest`. If reactivated: `WifiLegacyTest` and `CAP-wifi-list-legacy-*` per state. |
| `wifi_set_legacy` | Legacy WiFi configuration. States: `password_hidden`, `password_visible`, `countdown`, `connect_error`, `modify_success`, `set_failure`. Navigates to `config`, `product_intro`. `legacy_no_inbound`. | Omitted as legacy. | `legacy_documented`: keep the countdown, errors and two documented destinations. | `LegacyCoverageTest`. If reactivated: `WifiLegacyTest` and `CAP-wifi-set-legacy-*` per state. |
| `device_bind` | Pairing form. States: `qr_locked`, `manual`, `password_hidden`, `password_visible`, `share_link`, `validation_error`, `binding`, `success`, `invitation_error`. Actions name, UID, auth code, QR and bind. Navigates to `main_tab`, `scan`. | Validation, duplicates, binding and persistent local pairing. | `DeviceBindScreen` with ephemeral secret, `PairingResult` and unknown result after timeout. | `PairingViewModelTest` for all states, `SecretStateTest`, `CAP-device-bind-manual`, `CAP-device-bind-validation`, `CAP-device-bind-pending`. |
| `online_devices` | LAN results. States: `refreshing`, `results`, `empty`, `error`. Navigates to `device_bind`. | Simulated loading, results, empty and error. | `OnlineDevicesScreen` with real list, freshness and empty only after a completed search. | `DiscoveryViewModelTest` for four states and duplicates, `CAP-online-devices-empty`, `CAP-online-devices-results`. |
| `scan` | QR and gallery scanner. States: `scanning`, `pick_image`, `invalid`, `result`, `new_device`, `share`. Resources `qr_scan`, gallery. Navigates to `product_intro`, `device_bind`, `share_bind`. | Camera with CameraX and ZXing over the parser, plus manual entry. No image selection. | `ScanCodeScreen` with FOSS camera, gallery, paste as alternative and camera close on exit. | `CameraPermissionTest`, parser and three destinations, `CAP-scan-camera`, `CAP-scan-pick-image`, `CAP-scan-invalid`. |
| `config` | Device settings. States: `owner`, `shared`, `loading`, `alarm_on`, `alarm_off`, `upgrade_available`, `delete_confirm`, `delete_error`, `bind_required`. Actions info, passwords, unlock, WiFi, users, firmware, alarms, time sync, F1/FPS/lock/audio and deletion. Navigates to `device_info`, `password_modify`, `unlock_setting`, `router`, `upgrade`, `share_manage`, `main_tab`. | Owner/shared, alarms and local editing, upgrade and several operations are not real. | `DeviceConfigScreen` by capabilities and role, without hiding unsupported as if it were an absent feature. | `DeviceConfigViewModelTest` for owner/shared and all states, `NavigationTest` of seven destinations, `CAP-config-owner`, `CAP-config-shared`, `CAP-config-delete-error`. |
| `password_modify` | Password change. States: `device_mode`, `admin_mode`, `unlock_mode`, `init_mode`, `mismatch`, `empty`, `success`. Navigates to `config`. | Four modes and local validation without storing secrets. | `PasswordModifyScreen` with real operation per mode, field cleanup and credential error. | `PasswordViewModelTest` for modes/errors, `SecretStateTest`, `CAP-password-modify-mismatch`. |
| `unlock_setting` | Quick unlock and biometrics. States: `quick_on`, `quick_off`, `bio_on`, `bio_off`, `bio_unavailable`, `warning`, `saved`. Navigates to `config`. | Persistent quick and biometrics, biometrics resolved with the system credential and without it the action goes straight through. | `UnlockSettingScreen` with system credential before opening or triggering F1, and a security warning, without confusing local authorization with ack. | `BiometricTest` for available/unavailable/fallback, `CAP-unlock-setting-warning`, `CAP-unlock-setting-bio-unavailable`. |
| `device_info` | Information and renaming. States: `read_only`, `editing`, `validation_error`, `success`, `failure`. Non-editable UID. Navigates to `config`. | Editing, duplicate and local save. | `DeviceInfoScreen` with stable UID, local/remote save and stale state. | `DeviceInfoViewModelTest` for five states, `CAP-device-info-validation`. |
| `about` | Version, privacy and debug access. States: `version`, `privacy`, `debug_trigger`. Resource `logo.png`. Navigates to `debug`. | Local version, local privacy and gesture. | `AboutScreen` with package version, provider and accessible privacy, restricted debug. | `AboutTest` for version/privacy/gesture, `CAP-about`, `CAP-about-privacy`. |
| `image_pager` | Image viewer. States: `white`, `black`, `page`, `share`, `delete_confirm`, `save_result`. Navigates to `video_player`, `tab_gallery`. | Local placeholders, modes and deletion, share/export unavailable. | `ImagePagerScreen` with real media, accessible zoom, minimal URI grants and real operations. | `MediaPagerTest` with fixture images, `FileProviderSecurityTest`, `CAP-image-pager-white`, `CAP-image-pager-black`, `CAP-image-pager-delete`. |
| `video_player` | Local player. States: `playing`, `paused`, `stopped`, `muted`, `unmuted`, `seeking`, `tools_visible`, `tools_hidden`, `landscape`. Navigates to `image_pager`. | Simulated controls over a black panel. | `VideoPlayerScreen` with real player, restored position, audio and accessible controls. | `VideoPlayerTest` for all states and process death, `CAP-video-player-playing`, `CAP-video-player-seeking`, `CAP-video-player-landscape`. |
| `push_call` | Incoming call. States: `ringing`, `vibration`, `accept`, `refuse`, `auto_refuse`, `answered_elsewhere`. Original background and button resources. Navigates to `preview`, `main_tab`. | Local call with vibration, accept/reject and close at 30 s. | `IncomingCallScreen` with `callId`, one-time actions, permissions and event 20. | `CallViewModelTest` with clock, `CallNavigationTest` anti-duplicate, `CAP-push-call-ringing`, `CAP-push-call-answered-elsewhere`. Later FCM/hardware test. |
| `debug` | Diagnostics. States: `debug_toggle`, `forwarding`, `connection_type`, `advanced`, `service_switch`, `send_log`, `token`. Navigates to `loading`. `hidden_gesture`. | Fake scenarios, reset and absence of token. | `DebugScreen` only in permitted builds, per-provider diagnostics and sanitized export. Never a visible token. | `DebugAccessTest`, `LogRedactionTest`, confirmation for change/reset, `CAP-debug-redacted`. |
| `upgrade` | Firmware. States: `query`, `up_to_date`, `available`, `progress`, `success`, `failure`. Navigates to `config`. | Local up-to-date query and disabled upgrade. | `FirmwareScreen` with query and apply separated. Apply only with evidence, authenticity and hardware authorization. | `FirmwareViewModelTest` for six states without flashing, `CAP-upgrade-available`, `CAP-upgrade-failure`. Physical test outside normal tests. |
| `guide` | Paginated guide. States: `page_locks`, `page_cameras`, `page_playback`, `start`. Resources `guide_display1/2/3`. Navigates to `main_tab`. `legacy_no_inbound`. | Omitted because loading always opens main. | `legacy_documented`: keep the three pages and button without presenting it as current onboarding. | `LegacyCoverageTest`. If reactivated: accessible pager and `CAP-guide-locks`, `CAP-guide-cameras`, `CAP-guide-playback`. |
| `add_method_legacy` | Legacy AP/audio selector. States: `ap`, `voice`, `switch_success`, `switch_failure`. Navigates to `router`, `handle_wifi`. `legacy_no_inbound`. | Omitted as legacy. | `legacy_documented`: do not duplicate the modern selector without reviewing these transitions. | `LegacyCoverageTest`. If reactivated: `PairingNavigationTest` and `CAP-add-method-legacy`. |
| `router` | Router and password. States: `loading`, `selected`, `password_hidden`, `password_visible`, `save_password`, `validation`, `new_mode`, `modify_mode`. Resources `wifi_1` to `wifi_4` levels. Navigates to `wifi_progress`, `voice`, `config`. | Demo list and form, preferred save without persisted secrets. | `RouterScreen` with real networks, opt-in save, ephemeral secret and two modes. | `RouterViewModelTest` for eight states, `WifiPermissionTest`, `CAP-router-new`, `CAP-router-modify`, `CAP-router-validation`. |
| `voice` | Acoustic pairing. States: `sending`, `waiting`, `volume_low`, `failure`, `success`. Resource `device_add_sound_waver`. Navigates to `device_bind`, `main_tab`. | Local machine without emitting audio. | `VoiceProvisioningScreen` with real cancellable emission, volume and adapter progress. | `AudioProvisioningTest` for five states, `CAP-voice-waiting`, `CAP-voice-volume-low`, later hardware test. |
| `wifi_progress` | AP progress. States: `connecting`, `sending`, `waiting`, `network_error`, `retry`, `failure`, `success`. Resource `device_add_wifi_set`. Navigates to `pairing_fail`, `device_bind`, `main_tab`, `handle_wifi`. | Local stages, manual failure and navigation. | `WifiProvisioningScreen` with real progress, 150 s if the profile confirms it, cancellation and `outcomeUnknown`. | `ProvisioningViewModelTest` for all states/cancellation, `CAP-wifi-progress-waiting`, `CAP-wifi-progress-network-error`, `CAP-wifi-progress-unknown`. |
| `share_device` | New invitation. States: `qr`, `link`, `expiration`, `copied`, `save_result`, `share_sheet`. 120 dp QR code. Navigates to `share_manage`. | Local link and QR/export unavailable, it does not show a fake QR code. | `ShareDeviceScreen` with real QR code, expiry, copy and chooser, sensitive content outside logs. | `InvitationViewModelTest`, decode the generated QR code, real share/save, `CAP-share-device-qr`, `CAP-share-device-expiration`. |
| `pairing_fail` | Pairing error. States: `error`, `main_only`. Physical back ignored and exit to `main_tab`. | Simulated error and return to main. | `PairingFailScreen` with typed cause, safe retry and predictable exit without leaving an active task. | `PairingFailureNavigationTest` for back and retry, `CAP-pairing-fail`. |
| `share_manage` | Users and invitations. States: `loading`, `empty`, `list`, `create`, `edit`, `delete`, `limit`, `error`. Navigates to `share_device`, `config`. | Local list, create/delete, limit and errors. | `ShareManageScreen` with real CRUD, permissions, owner role and limit 100 when applicable. | `SharingViewModelTest` for eight states and permissions, `CAP-share-manage-empty`, `CAP-share-manage-list`, `CAP-share-manage-limit`. |
| `share_bind` | Accept invitation. States: `form`, `validation`, `accept`, `success`, `invalid`. Locked UID/code. Navigates to `main_tab`. | Local invitation, duplicates and shared pairing. | `ShareBindScreen` with real acceptance, re-acceptance and expiry/use errors. | `InvitationAcceptTest` for five states and re-acceptance, `CAP-share-bind-form`, `CAP-share-bind-invalid`. |
| `tab_devices` | List and actions. States: `empty`, `list`, `refresh`, `offline`, `password`, `biometric`, `action_result`. Cover and online/offline resources. Navigates to `add_type`, `preview`, `config`. | Loading/empty/list/offline and demo actions disabled according to permission. | `DevicesScreen` with real/stale data, actions per capability and differentiated ack. | `DevicesViewModelTest` for seven states, `ActionOutcomeTest`, `CAP-devices-empty`, `CAP-devices-list`, `CAP-devices-offline`, `CAP-devices-biometric`. |
| `tab_events` | Event container. States: `loading`, `empty`, `list`, `filters_hidden`, `warning`, `edit`, `delete`, `image`. Navigates to `tab_event_list`. | Local list, warning, editing and deletion, documented hidden filters. | `EventsScreen` with an All list by default and filters only if enabled, real photos and visible gaps. | `EventsViewModelTest` for eight states, `CAP-events-empty`, `CAP-events-list`, `CAP-events-edit`, `CAP-events-image`. |
| `tab_event_list` | Activity list. States: `empty_all`, `empty_call`, `empty_motion`, `refresh`, `load_more`, `edit`. Navigates to `tab_events`. | Rows, manual refresh, pages of 15 and local editing. | `EventList` with real cursor, three empty states and selection, without turning an error into an empty list. | `EventPaginationTest` for pages/errors/gaps, `CAP-event-list-empty-motion`, `CAP-event-list-load-more`. |
| `tab_gallery` | Local gallery. States: `empty`, `grid`, `edit`, `selected`, `select_all`, `share`, `save`, `delete`. Three columns and dates. Navigates to `image_pager`. | Local grid with placeholders and operations over metadata. | `GalleryScreen` with real files, selection and safe URI actions. | `GalleryViewModelTest` for eight states, `FileProviderSecurityTest`, `CAP-gallery-empty`, `CAP-gallery-grid`, `CAP-gallery-selected`. |
| `tab_settings` | Settings. States: `sound_on`, `sound_off`, `vibration_on`, `vibration_off`, `about`, `exit_confirm`, `hidden_options`. Navigates to `about`. Auto rotate, channels and preview time hidden in the original. | Persistent sound/vibration, about, scenarios, reset and demo exit. | `SettingsScreen` with real preferences, provider and connection mode. Documented hidden options, debug/reset outside production. | `SettingsViewModelTest` for seven states, `CAP-settings`, `CAP-settings-exit-confirm`, `CAP-settings-hidden-options-review` documentary only. |

## 5. UI state model

### 5.1 Loading, empty, error and offline

| State | Presentation rule | Data rule |
|---|---|---|
| `loading` | Skeleton or indicator next to the affected area, without blocking the whole app if there is previous content | Only indicates an operation in progress, not success |
| `empty` | Message and specific action: add device, no events, no media or no users | Only after a correct response that proves zero items |
| `error` | Actionable cause and allowed retry. Keeps previous content if it exists | Never turn a failure into empty nor replace with demo |
| `offline` | Distinguish phone without network, disconnected provider and offline device | Keep stale data with the last update time |
| `unsupported` | Feature known but not offered by provider/device/role | Do not retry nor present a timeout |
| `unknown` | Feature not negotiated or without evidence | Allow revalidation, do not claim the hardware lacks it |
| `permission` | Explain the Android permission or owner/shared role and offer a valid action | Do not confuse platform denial with cloud unsupported |
| `action-pending` | Show action and device, disable duplicates and allow cancelling only if safe | Keep `commandId` and state after rotation |
| `ack` | Text according to level: local, provider, device or physical observation | Never turn a transport ack into a physical effect |
| `failure` | Typed error, possible unknown result and next step | Do not automatically repeat ambiguous physical actions |

`LoadState` must be able to carry previous data, `isStale`, `lastUpdated`, `error` and `refreshing`. This avoids choosing between an old list and an indicator with no context.

### 5.2 Demo versus real connection

- All surfaces show `DEMO LOCAL` or the real provider and its state, not only the four tabs.
- The banner identifies whether the data is synthetic, local cache or a real response. A cached stock datum is not labelled demo.
- The demo provider cannot be activated as recovery from a stock error. Switching to demo requires an explicit action and restarts the data scope.
- A capture for documentation must include the banner when applicable. It is not cropped to hide the simulated nature.
- Demo acks use specific text, for example "local acknowledgement, no physical action". "Successful opening" is never reused for that result.

### 5.3 Physical actions

1. Before sending: check capability, role, session state and quick/biometric protection.
2. During: show `action-pending` and avoid a second order. Do not use a spinner as a substitute for the state.
3. With ack: show the exact level and its meaning. `PROVIDER_ACCEPTED` is not "door open".
4. With timeout: show unknown result and refresh/check options. Do not repeat by default.
5. With a later event: correlate with `commandId` or call if it exists and update the same result, without creating another command.
6. With unsupported or unknown: keep the feature in the inventory and explain the block. Do not delete unlock, F1, call or preview from the design.

## 6. Accessibility, sizes, theme and navigation

### 6.1 Accessibility

- Minimum touch targets of 48 dp, including preview icons, tabs, pager and selection.
- Normal text with minimum contrast 4.5:1 and large text/essential icons 3:1. Measure the real pairs, do not infer them from the palette.
- Do not depend only on red/green or on the online/offline icons. Add text, shape and description for TalkBack.
- Contextual `contentDescription`: "Open gate, device online", not "button". Decorative icons are excluded from the accessibility tree.
- `stateDescription` for switches, selection, recording, talk, listen and panels. Custom actions for video controls without mandatory gestures.
- Visible focus, logical order and error/progress announcements without repetitive reading by recomposition.
- Font scales 1.0, 1.3 and 2.0 without clipping or ellipsis in critical messages. Rows grow and actions can stack.
- ES/EN and RTL support for forms, chevrons, text and navigation. Codes/UIDs keep LTR direction when applicable.
- Respect animation reduction. Physical progress does not use animation as the only signal.
- Permissions with prior explanation and alternative when possible. Do not request microphone, camera or location before entering the feature that needs them.

### 6.2 Sizes and orientation

Minimum validation matrix:

| Class | Configuration | Check |
|---|---|---|
| Narrow phone | 320 dp wide, font 1.0 and 2.0 | Forms, errors, actions and bottom sheets without clipping |
| Common phone | 360 dp, font 1.0 and 1.3 | All base screens and navigation |
| Medium screen | 600 dp | Rail or adaptive layout, preview and gallery |
| Wide screen | 840 dp | Panels without stretching cards or losing hierarchy |
| Landscape | Preview, video and critical pairing | Insets, controls, keyboard and back stack |
| Android with gestures and with buttons | Insets and back | No actions hidden behind system bars |

### 6.3 Light and dark theme

The light theme is the first functional reference. The dark one is only enabled after measuring all colour pairs, disabled states, error, banners, selection maps and media controls. Preview and player can remain dark in both themes. An automatic dark mode that reduces contrast or makes original icons invisible is not adopted.

## 7. Tests the implementation must produce

### 7.1 Unit and contract tests

1. ViewModels with all the states of each row and deterministic clocks/dispatchers.
2. State reducers for loading, empty, error, offline, unsupported, unknown, permission, pending, ack and failure.
3. Navigation and back stack for the 33 non-legacy representations, the 71 reference edges and deep links.
4. Cancellation of pairing, preview, call and events on exit, rotation or account change.
5. Actions duplicated by double click, recomposition and process death. An ambiguous physical action generates zero retries.
6. Ack levels and text mapping, including fake and errors.
7. Android permissions, invitation permissions and owner/shared differences.
8. Pages of 15, duplicates, gaps, selection and deletion.
9. QR/invitation parser, OEM, expiry and secrets absent from persistent state and logs.
10. Persistence and restoration of tab, scroll, pager, selection and video position.

### 7.2 Compose and instrumentation

- Semantic tests per screen for actions, states, descriptions and disabled.
- Real navigation tests on the emulator, including process death, rotation, gesture back and incoming call.
- Demo and fake scenarios separated from stock integration. The test name declares the source.
- Accessibility tests with font 1.3 and 2.0, manual TalkBack in critical flows and automated contrast when reliable.
- Camera, gallery, microphone, biometrics and notification tests only with controlled permissions and fixtures.
- Real file tests for capture, recording, gallery, share and save. A `DemoMedia` record does not count as a file.
- Stock and hardware suites only when authorization exists, without including secrets or physical actions in the ordinary test.

### 7.3 Required captures, not produced yet

The later implementation must generate real captures of the app running. Compositions, `start.png` resources or `guide_display*.png` are not accepted as if they were captures.

| Batch | Minimum content | Acceptance condition |
|---|---|---|
| Base | One capture of each of the 32 non-legacy visual surfaces | 360 dp, font 1.0, light theme, demo/provider banner visible |
| Critical states | Loading/error/empty/offline/unsupported/permission/pending/ack/failure on the screens where they apply | Identifiable state and data consistent with its source |
| Preview | One/four panels, portrait/landscape, offline, unsupported and ack | Legible controls and no synthetic frame presented as real |
| Pairing | Permissions, QR, empty/results LAN, AP/audio/progress/failure and bind | Each stage with real origin or explicit demo |
| Call | Ringing, pending, accepted/rejected and answered elsewhere | Coherent `callId` and result |
| Forms | Validation, visible/hidden password without a real secret, owner/shared | No personal data or credentials |
| Accessibility | 320 dp font 2.0, 600/840 dp, landscape and RTL in selected flows | No overlaps, clipping or lost actions |
| Theme | Light and, only if enabled, dark | Documented contrast and visible controls |

Each capture will have a stable name `CAP-<screen>-<state>-<configuration>.png` and a manifest entry with path, SHA-256, date, non-personal emulator serial, Android API, size, font scale, language, theme, demo/real mode and commit. `cache` is validated with a test, not with an image. The five legacy ones are only captured if they are reactivated.

Reference command for the later phase, with an explicit serial and without using a physical phone:

```bash
adb -s <emulator> exec-out screencap -p > <capture-path>.png
```

## 8. Functional review gate and final APK

The later review must walk this table and `analysis/architecture/provider-matrix.json` point by point. For each screen it will record navigation, states, actions, resources, tests, capture and limitations. For each capability it will record API/wire, behaviour, security and hardware separately.

Before delivering the APK, the following will be run, at minimum and according to the available environment:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
python3 analysis/architecture/verify_provider_architecture.py
ANDROID_SERIAL=<emulator> ./gradlew :app:connectedDebugAndroidTest
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Afterwards that APK will be installed on the chosen emulator and a smoke walkthrough will be repeated: startup, four tabs, pairing, preview or unsupported, call, gallery, configuration and back navigation. The hash, APK path, commands, test counts and real captures will be kept. Building and installing does not certify cloud or hardware compatibility. This sheet has not run those commands nor produced an APK.
