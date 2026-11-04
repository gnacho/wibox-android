# Visual and navigation inventory of Wibox Fermax v2.2

Source: static analysis of `com.fermax.wibox` v2.2, versionCode 18, SHA-256 `08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce`.

## 1. Scope and absence of captures

There are no real captures of the app running. The app has not been installed nor has a phone or emulator been used. `start.png` is a brand screen included as a resource and `guide_display1/2/3.png` are guide illustrations. They are not presented as captures of real states.

The specification comes from XML layouts, drawables, values, navigation classes and resources decoded by JADX. Each screen has evidence in `analysis/apk/evidence/ui-code-evidence.md` and resources with hashes in `analysis/apk/resources/manifest.json`.

## 2. Technology and structure

- Android Views and XML, not Jetpack Compose.
- AppCompat `Light.NoActionBar`, portrait activities except manual preview/viewer changes.
- ViewBinding and MVP. Fragments for the four main tabs.
- `CommonTitleBar` for headers and `MyGLSurfaceView` for video.
- `values-night/colors.xml` is empty. There is no dark theme of its own. Black preview and viewer are specific states.
- `FontHelper.Init(false, ...)` disables the bundled font. The system sans-serif family is used.

## 3. Visual system

### 3.1 Colours

The values are kept in ARGB when the resource defines them that way. Aliases are resolved in notes when they are obvious.

| Resource | Value | Use/note |
|---|---|---|
| `appTitleColor` | `#78c9e7` | Brand |
| `app_main_tab_bg` | `@color/appTitleColor` | Original resource |
| `bg_red` | `#e73e04` | Error |
| `bg_red_pressed` | `#cc330a` | Original resource |
| `black_bg` | `#ff000000` | Original resource |
| `black_transparent` | `#50000000` | Original resource |
| `btn_upgrade_color_bg` | `#7e9aaf` | Original resource |
| `clr_normal` | `#ffffff` | Original resource |
| `clr_pressed` | `#c9c9c9` | Original resource |
| `colorAccent` | `@android:color/darker_gray` | Original resource |
| `colorPrimary` | `#78c9e7` | Brand and bars |
| `colorPrimaryDark` | `#78c9e7` | Original resource |
| `color_bottom_tab_text` | `@color/white` | Active tab |
| `color_bottom_tab_text_normal` | `#929292` | Inactive tab |
| `common_sub_text` | `#ff888888` | Original resource |
| `conditions_agree` | `@color/colorPrimary` | Original resource |
| `conditions_disagree` | `@android:color/darker_gray` | Original resource |
| `cut_line` | `@android:color/darker_gray` | Original resource |
| `device_list_pressed` | `#e8e8e8` | Original resource |
| `disable_color_bg` | `#dfddde` | Original resource |
| `fm_fragment_text_normal` | `#7d7d7d` | Original resource |
| `fm_fragment_text_selected` | `#00a3dd` | Selected event |
| `grey` | `#ff888888` | Original resource |
| `guide_blue_tv` | `#00a3dd` | Original resource |
| `icon_black` | `#88000000` | Original resource |
| `main_ui_color_white` | `#f4f4f4` | General background |
| `new_line_color` | `#ffcecece` | Original resource |
| `preview_item_bg` | `@android:color/black` | Original resource |
| `preview_item_select` | `@android:color/transparent` | Original resource |
| `progree_bar_bg` | `#ff6700` | Original resource |
| `progressBarBg` | `@android:color/darker_gray` | Original resource |
| `snackBarBackground` | `#af78c9e7` | Original resource |
| `snackBarText` | `@color/titleText` | Original resource |
| `textColor` | `#ff4d4d4d` | Normal text |
| `textColorGray` | `@android:color/darker_gray` | Original resource |
| `titleText` | `#fff` | Header text |
| `transparent` | `#00000000` | Original resource |
| `white` | `#fff` | Original resource |
| `window_selected` | `@android:color/transparent` | Original resource |

Main rules:

- Header and bottom tabs: `colorPrimary`/`appTitleColor` `#78c9e7`.
- General background: `main_ui_color_white` `#f4f4f4`.
- Normal text: `textColor` `#ff4d4d4d`, header titles: `titleText` white.
- Inactive bottom tab: `#929292`, active: white.
- Selected events: `fm_fragment_text_selected` `#00a3dd`.
- Error: `bg_red` `#e73e04`.
- Preview: black, with white icons and overlays.

### 3.2 Typography

| Use | Specification |
|---|---|
| Family | System sans-serif. `fonts/alger.ttf` exists, but is not applied by default |
| Standard button | `text_normal_size`, 18sp, normal style, `textAllCaps=false` |
| Secondary text | `text_small_size`, 15sp |
| Title | `title_text_size`, 20sp |
| Bottom tabs | `bottom_text_size`, 12sp |
| Guide | 30sp, bold |
| Name in About | 25sp |
| Auxiliary | 10sp, 13sp, 14sp, 15sp, 16sp and 18sp depending on the layout |
| Highlighted styles | `item_alarm_header` and `file_grid_header` use 16sp bold |

It is not replaced by Lucide in the description of the original: the real icons are PNGs and XML selectors.

### 3.3 Dimensions

| Resource | Value |
|---|---|
| `activity_device_config_wifi_textsize` | `18sp` |
| `bottom_menu_height` | `130dp` |
| `bottom_text_size` | `12sp` |
| `btn_height` | `50dp` |
| `btn_height_large` | `60dp` |
| `btn_height_small` | `25dp` |
| `btn_small_width` | `120dp` |
| `btn_width` | `300dp` |
| `default_gap` | `8dp` |
| `devlist_height` | `55dp` |
| `fm_fragment_dial_info_line` | `3dp` |
| `fullscreen_img_size` | `55dp` |
| `logo_text_size` | `25sp` |
| `main_bottom_height` | `55dp` |
| `margin_large` | `20dp` |
| `margin_normal` | `10dp` |
| `margin_small` | `5dp` |
| `padding_large` | `30dp` |
| `padding_mini` | `3dp` |
| `padding_normal` | `15dp` |
| `padding_small` | `5dp` |
| `padding_small_normal` | `10dp` |
| `preview_item_icon_small` | `50dp` |
| `standard_width` | `45dp` |
| `talk_record_text_size` | `12sp` |
| `text_large_size` | `22sp` |
| `text_mini_size` | `10sp` |
| `text_nomal_size` | `16sp` |
| `text_normal_size` | `18sp` |
| `text_small_size` | `15sp` |
| `title_height` | `60dp` |
| `title_text_size` | `20sp` |

Measurements worth noting for the replica:

- `option_button` button: match_parent width, 50dp high and 30dp side margins.
- Bottom navigation: 55dp high, four items with weight 1 and 60x60 px icons at xhdpi.
- Device cover: 200dp high, `centerCrop`.
- Invitation QR code: 120x120 dp.
- Guide: 640x960 px images at xhdpi.
- `standard_width`: 45dp side margin in explanatory texts.

### 3.4 Resources by density

The table only counts the representative extracted images, not the whole APK. The original manifest has many more library resources and variants.

| Density | Extracted images | Examples |
|---|---:|---|
| `drawable-ldrtl-xhdpi` | 1 | icon_arrow_list_right.png 44x44 |
| `drawable-xhdpi` | 52 | backgroud_calling.png 720x1280, btn_accept.png 120x120, btn_preview_mode_normal.png 50x50, btn_refuse.png 120x120, device_add_sound_waver.png 564x180, device_add_wifi_set.png 564x180, device_cover_default.png 600x450, device_status_offline.png 48x48 |

## 4. Global navigation

- Entry: `loading` -> `main_tab`.
- Tabs: `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings`.
- Pairing: `add_type` separates new and online, the QR code leads to router/AP/audio or bind.
- Device: `tab_devices` -> `preview` or `config`.
- Settings: `config` links information, passwords, unlock, WiFi, firmware and users.
- Gallery: `tab_gallery` -> `image_pager` -> `video_player`.
- Push: event 19 -> `push_call` -> `preview`, event 20 closes the call.
- Legacy: `guide`, `product_choose`, `add_method_legacy` and `wifi_list_legacy` have no static entry. `wifi_set_legacy` only hangs from the legacy list.

The JSON contains 71 edges. The following table does not invent destinations when there is no reference.

## 5. Screens, states and resources

Conventions:

- `layout` indicates the extracted file. `cache` has no layout.
- States include visibility, error, loading and variants detected in code.
- `legacy_no_inbound` means absence of inbound references in the current version, not a demonstrated impossibility of opening through undocumented external intents.

| ID / screen | Layout | States | Navigation |
|---|---|---|---|
| `loading`<br>WI-BOX<br>Reach: `entry` | `activity_loading.xml` | `splash`: Full-screen start background<br>`storage_permission`: Storage and settings dialog<br>`phone_permission`: READ_PHONE_STATE result does not block startup<br>`startup_wait`: Login wait or 10 s timeout<br>`push_redirect`: Redirects event 19 to call or preview | `main_tab`, `push_call`, `preview` |
| `cache`<br>Push relay<br><small>Activity without layout, excluded from recents</small><br>Reach: `linked` | no layout | `relay`: Copies extras to LoadingActivity<br>`finish`: Finishes without its own UI | `loading` |
| `main_tab`<br>Main tabs<br>Reach: `root` | `activity_main_tab.xml` | `devices_selected`: Device tab active by default<br>`events_selected`: Events tab<br>`gallery_selected`: Gallery tab<br>`settings_selected`: Settings tab<br>`terms`: Non-cancellable terms dialog<br>`android_hint`: Android 10 notice with do not show again<br>`edit_mode`: Bottom bar hidden in edit | `tab_devices`, `tab_events`, `tab_gallery`, `tab_settings` |
| `add_type`<br>Add Device<br>Reach: `linked` | `activity_da_type_choose.xml` | `new_device`: New device option<br>`online_device`: Add over network option | `product_intro`, `online_type` |
| `preview`<br>Auto power-on<br>Reach: `linked` | `activity_preview.xml` | `empty`: Slot to add device<br>`connecting`: Connecting or buffering<br>`playing`: Playing with controls<br>`stopped`: Stopped<br>`refresh`: Error and retry button<br>`password_error`: Rejected credential<br>`offline`: Device or channel offline<br>`one_window`: One panel<br>`four_windows`: Four panels<br>`portrait`: Vertical controls<br>`landscape`: Horizontal controls<br>`listening`: Incoming audio active<br>`talking`: Microphone active<br>`recording`: Recording and visible time<br>`snapshot`: Capture confirmation<br>`unlock_progress`: Opening<br>`unlock_success`: Successful opening<br>`unlock_failure`: Opening failure or timeout<br>`f1`: F1 sending/success/failure states<br>`timeout`: End of preview time<br>`no_permission`: Feature not allowed | `config`, `password_modify` |
| `online_type`<br>Add over Network<br>Reach: `linked` | `activity_da_online_type_choose.xml` | `scan_online`: Search online devices<br>`manual`: Manual pairing | `online_devices`, `device_bind` |
| `product_choose`<br>Setup Wizard<br><small>No inbound reference was found in the current code</small><br>Reach: `legacy_no_inbound` | `activity_da_new_produce_choose.xml` | `doorbell`: Wi-Fi Doorbell type 7410<br>`monitor`: Wi-Fi Monitor type 9440 | `product_intro` |
| `product_intro`<br>Wi-Fi Settings<br>Reach: `linked` | `activity_da_new_produce_introduce.xml` | `reset_instructions`: Reset instructions and image<br>`location_permission`: Location disabled or denied<br>`camera_permission`: Camera request<br>`scan_error`: Invalid QR code<br>`wifi_switch_error`: Manual step to the AP | `scan`, `router`, `handle_wifi` |
| `handle_wifi`<br>Wi-Fi Settings<br>Reach: `linked` | `activity_da_new_handle_wifi.xml` | `instructions`: SSID and password visible with copy<br>`copied`: Copy confirmation<br>`ap_mismatch`: Snackbar to open WiFi settings<br>`ap_connected`: Continues to router or progress | `router`, `wifi_progress` |
| `wifi_list_legacy`<br>Wi-Fi Settings<br><small>No inbound reference was found in the current code</small><br>Reach: `legacy_no_inbound` | `activity_dm_wifi_list.xml` | `loading`: Network refresh<br>`list`: SSID list<br>`error`: Notice without list<br>`password`: Password dialog<br>`connect_failure`: Connection error and settings<br>`success`: Finishes on connect | `wifi_set_legacy` |
| `wifi_set_legacy`<br>Wi-Fi Settings<br><small>Only linked from wifi_list_legacy, which has no entry either</small><br>Reach: `legacy_no_inbound` | `activity_dm_wifi_set.xml` | `password_hidden`: Hidden password<br>`password_visible`: Visible password<br>`countdown`: Wait with counter<br>`connect_error`: Dialog to open WiFi<br>`modify_success`: Returns to settings<br>`set_failure`: Error and possible pairing restart | `config`, `product_intro` |
| `device_bind`<br>Add Device<br>Reach: `linked` | `activity_da_device_bind.xml` | `qr_locked`: UID locked by QR code<br>`manual`: Editable UID and scanner<br>`password_hidden`: Hidden password<br>`password_visible`: Visible password<br>`share_link`: Shared link disables incompatible fields<br>`validation_error`: Duplicate or empty name/UID<br>`binding`: Non-cancellable progress<br>`success`: Correct configuration<br>`invitation_error`: Invalid or used invitation | `main_tab`, `scan` |
| `online_devices`<br>Add over Network<br>Reach: `linked` | `activity_da_online_device_list.xml` | `refreshing`: LAN search<br>`results`: Device list<br>`empty`: No device found message<br>`error`: Finishes refresh without results | `device_bind` |
| `scan`<br>Scan the QR code<br>Reach: `linked` | `activity_da_capture.xml` | `scanning`: Viewfinder and QR frame<br>`pick_image`: Scan from gallery with progress<br>`invalid`: Invalid QR dialog and retry<br>`result`: Returns the result to the caller<br>`new_device`: Continues new pairing<br>`share`: Opens invitation | `product_intro`, `device_bind`, `share_bind` |
| `config`<br>Settings<br>Reach: `linked` | `activity_dm_config.xml` | `owner`: All owner options<br>`shared`: Hides password, WiFi, alarms, users and firmware<br>`loading`: Info and alarm query<br>`alarm_on`: Call/motion enabled<br>`alarm_off`: Call/motion disabled<br>`upgrade_available`: New version icon<br>`delete_confirm`: Deletion confirmation<br>`delete_error`: Deletion error<br>`bind_required`: Password dialog | `device_info`, `password_modify`, `unlock_setting`, `router`, `upgrade`, `share_manage`, `main_tab` |
| `password_modify`<br>Modify password<br>Reach: `linked` | `activity_dm_password_modify.xml` | `device_mode`: Changes device password<br>`admin_mode`: Changes admin and unlock<br>`unlock_mode`: Opening password only<br>`init_mode`: Initialization without old field<br>`mismatch`: Red labels<br>`empty`: Empty fields error<br>`success`: Password changed | `config` |
| `unlock_setting`<br>Unlock configuration<br>Reach: `linked` | `activity_dm_unlock_setting.xml` | `quick_on`: Quick opening active<br>`quick_off`: Quick opening inactive<br>`bio_on`: Biometric confirmation active<br>`bio_off`: Biometric confirmation inactive<br>`bio_unavailable`: Biometric row hidden<br>`warning`: Explanatory dialog with do not show again<br>`saved`: Saves and finishes | `config` |
| `device_info`<br>Device information<br>Reach: `linked` | `activity_dm_device_info_edit.xml` | `read_only`: Name and UID not editable<br>`editing`: Editable name and save button<br>`validation_error`: Empty or duplicate name<br>`success`: Local/remote save<br>`failure`: Modification error | `config` |
| `about`<br>Info<br>Reach: `linked` | `activity_me_about.xml` | `version`: Logo, WI-BOX and version<br>`privacy`: Dialog with local HTML<br>`debug_trigger`: Hidden sequence opens TestInfo | `debug` |
| `image_pager`<br>Gallery viewer<br>Reach: `linked` | `activity_fm_image_pager.xml` | `white`: White background with title and actions<br>`black`: Black background without title or actions<br>`page`: Position/total indicator<br>`share`: Share item<br>`delete_confirm`: Deletion confirmation<br>`save_result`: Save snackbar | `video_player`, `tab_gallery` |
| `video_player`<br>Video player<br>Reach: `linked` | `activity_fm_local_video_play.xml` | `playing`: Playback and pause button<br>`paused`: Overlay and play button<br>`stopped`: Visible overlay<br>`muted`: Mute icon<br>`unmuted`: Sound icon<br>`seeking`: Temporary pause and seek<br>`tools_visible`: Visible controls<br>`tools_hidden`: Hides controls after 5 s<br>`landscape`: Full-screen video | `image_pager` |
| `push_call`<br>street panel call<br>Reach: `linked` | `activity_push_call.xml` | `ringing`: Name, accept and reject with ringtone<br>`vibration`: Optional vibration<br>`accept`: Opens preview and records acceptance<br>`refuse`: Closes or sends to background<br>`auto_refuse`: Auto-reject at 30 s<br>`answered_elsewhere`: Message and close by event 20 | `preview`, `main_tab` |
| `debug`<br>Debug<br><small>Access by sequence in About and code for advanced options</small><br>Reach: `hidden_gesture` | `activity_test_info.xml` | `debug_toggle`: Debug mode and restart notice<br>`forwarding`: Forces forwarding<br>`connection_type`: Shows connection type<br>`advanced`: Options after code<br>`service_switch`: Server change and local deletion<br>`send_log`: Share log<br>`token`: Token available or null | `loading` |
| `upgrade`<br>Firmware Version<br>Reach: `linked` | `activity_dm_upgrade.xml` | `query`: Query current and available version<br>`up_to_date`: Firmware up to date<br>`available`: Confirmation dialog<br>`progress`: Non-cancellable progress<br>`success`: Success dialog and later restart<br>`failure`: Failure dialog | `config` |
| `guide`<br>Start guide<br><small>Declared but LoadingPresenter always opens MainTabActivity</small><br>Reach: `legacy_no_inbound` | `activity_start_guide.xml` | `page_locks`: More locks guide<br>`page_cameras`: More Cameras guide<br>`page_playback`: Quick Playback guide<br>`start`: Start Now button | `main_tab` |
| `add_method_legacy`<br>Add Device<br><small>No inbound reference was found in the current code</small><br>Reach: `legacy_no_inbound` | `activity_da_add_type.xml` | `ap`: AP add<br>`voice`: Sound waves to add<br>`switch_success`: Continues to router<br>`switch_failure`: Manual WiFi step | `router`, `handle_wifi` |
| `router`<br>Wi-Fi Settings<br>Reach: `linked` | `activity_da_router_info.xml` | `loading`: Network refresh<br>`selected`: Chosen SSID with wifi_1 to wifi_4 level<br>`password_hidden`: Hidden password<br>`password_visible`: Visible password<br>`save_password`: Save password active by default<br>`validation`: Short password<br>`new_mode`: Continues AP or audio<br>`modify_mode`: Change texts and additional notes | `wifi_progress`, `voice`, `config` |
| `voice`<br>Sound waves to add<br>Reach: `linked` | `activity_da_voice_set.xml` | `sending`: Acoustic emission<br>`waiting`: Waiting for online device<br>`volume_low`: Insufficient volume notice<br>`failure`: Failure and retry button<br>`success`: Continues bind or main | `device_bind`, `main_tab` |
| `wifi_progress`<br>Wi-Fi Settings<br>Reach: `linked` | `activity_da_wifi_set.xml` | `connecting`: Connects device<br>`sending`: Sends data<br>`waiting`: Waits online with countdown<br>`network_error`: Network notice and manual restoration<br>`retry`: Retry button<br>`failure`: Pairing error screen<br>`success`: Continues bind or main | `pairing_fail`, `device_bind`, `main_tab`, `handle_wifi` |
| `share_device`<br>New user<br>Reach: `linked` | `activity_share_device.xml` | `qr`: 120 dp QR code<br>`link`: Text link<br>`expiration`: Available before date<br>`copied`: Link copied<br>`save_result`: Result on saving the QR code<br>`share_sheet`: Android chooser | `share_manage` |
| `pairing_fail`<br>Error<br>Reach: `linked` | `activity_da_device_config_fail.xml` | `error`: Explanation of failed pairing<br>`main_only`: Next button only, physical back ignored | `main_tab` |
| `share_manage`<br>Users<br>Reach: `linked` | `activity_share_manage.xml` | `loading`: Invitation refresh<br>`empty`: No Users<br>`list`: Users with permissions<br>`create`: New invitation with default permissions<br>`edit`: Edit name/permissions<br>`delete`: Deletion confirmation<br>`limit`: Add button hidden at 100<br>`error`: Not authorized or failure | `share_device`, `config` |
| `share_bind`<br>Add Device<br>Reach: `linked` | `activity_share_bind.xml` | `form`: Locked UID/code and editable name<br>`validation`: Empty or duplicate name<br>`accept`: Acceptance with progress<br>`success`: Returns to main<br>`invalid`: Invalid or used invitation | `main_tab` |
| `tab_devices`<br>Device<br>Reach: `main_tab` | `fragment_device_list.xml` | `empty`: Central add button<br>`list`: Cards with cover and actions<br>`refresh`: SwipeRefresh and online state<br>`offline`: Offline state<br>`password`: Opening/F1 dialog<br>`biometric`: Biometric verification<br>`action_result`: Success/failure toast | `add_type`, `preview`, `config` |
| `tab_events`<br>Events<br>Reach: `main_tab` | `fragment_message_list.xml` | `loading`: Initial load and refresh<br>`empty`: No events text<br>`list`: A single All ACTIVITY list<br>`filters_hidden`: RINGS/MOTION hidden<br>`warning`: Notice about photos and firmware<br>`edit`: Selection and bottom bar<br>`delete`: Deletion confirmation<br>`image`: Photo dialog | `tab_event_list` |
| `tab_event_list`<br>Activity list<br>Reach: `nested_fragment` | `fragment_main_message_type.xml` | `empty_all`: Home call message<br>`empty_call`: Empty call message<br>`empty_motion`: Motion message and icon<br>`refresh`: Pull to refresh<br>`load_more`: Loads more from 15<br>`edit`: Multiple selection | `tab_events` |
| `tab_gallery`<br>Gallery<br>Reach: `main_tab` | `fragment_flie.xml` | `empty`: No photos or videos text<br>`grid`: Three columns grouped by date<br>`edit`: Selection bar and hidden tabs<br>`selected`: Counter in the title<br>`select_all`: Select all<br>`share`: Share selection<br>`save`: Save selection and result<br>`delete`: Deletion confirmation | `image_pager` |
| `tab_settings`<br>Settings<br>Reach: `main_tab` | `fragment_me.xml` | `sound_on`: Sound enabled<br>`sound_off`: Sound disabled<br>`vibration_on`: Vibration enabled<br>`vibration_off`: Vibration disabled<br>`about`: Opens information<br>`exit_confirm`: Exit dialog<br>`hidden_options`: Auto rotate, channels and preview time hidden | `about` |

## 6. Components and cross-cutting states

| Component | Resources/states |
|---|---|
| Header | `publico_include_title.xml`, left/centre/right, no bottom line, light mode |
| Bottom tabs | `main_btn_device`, `main_btn_call_log`, `main_btn_file`, `main_btn_setting` and `_select` variants |
| Device card | `item_device.xml`: 48x48 state, name, 600x450 cover, settings, concierge, F1 and lock |
| Shared card | `item_device_share.xml`: CardView, preview/playback/alarm/unlock/F1 permissions, edit/close/open |
| Preview | `preview_item_play.xml`: add, play, refresh, progress, speed and state text |
| Portrait controls | `activity_preview.xml`: play, sound, recording, capture, unlock/F1, channel, mode and talk |
| Landscape controls | `multi_full_screen_operate_bar.xml`: horizontal equivalents and unlock/F1 |
| Opening keyboard | `cust_keyboard.xml` and `cust_keyboard_full_screen.xml`: digits, delete, show, lock1/2/F1 |
| Dialogs | `custom_dialog`, `custom_dialog_with_never_show`, `custom_conditions_dialog`, `device_version_dialog`, `new_app_version_dialog` |
| Progress | `publico_custom_progress`, `publico_custom_progress_dlg`, `custom_horizontal_progressbar` |
| Lists | `item_message`, `item_new_device`, `item_lan_search_device`, `item_wifi_info` |
| Refresh | `xrefreshview_header/footer`, drag/release/refreshing/more texts |
| Call notification | `custom_notification.xml` and `custom_notification_big.xml`, accept/reject |
| Selector states | `btn_switch_on/off`, `selector_btn_unlock*`, `selector_f1*`, `main_selector_*`, `publico_selector_*` |

## 7. ES/EN strings

- Base English: 282 own strings (`key_*`, `app_name`, `co_consent_info`).
- Spanish: 281. `key_is_phone_calling` is missing, so the English fallback is used.
- 59 keys have exactly the same text in both resources. Among them there are untranslated strings and also brands or abbreviations, such as `Motion Alerts`, `RINGS`, `All ACTIVITY`, `Sound waves to add`, `Setup Wizard`, `WI-BOX` and `UID`.
- `values-en` only adds library strings. The own English strings are in `values/strings.xml`.

| Key | English | Spanish | Note |
|---|---|---|---|
| `app_name` | WI-BOX | WI-BOX | Brand |
| `key_device` | Device | Dispositivo | Tab and title |
| `key_device_add` | Add Device | Añadir Dispositivo | Pairing |
| `key_call_logs` | Events | Eventos | Tab |
| `key_album` | Gallery | Galería | Tab |
| `key_setting` | Settings | Ajustes | Tab |
| `key_unlock` | Unlock | Abrir | Action |
| `key_alarm_title_all` | All ACTIVITY | All ACTIVITY | Untranslated |
| `key_alarm_title_call` | RINGS | RINGS | Untranslated |
| `key_alarm_title_motion` | MOTION | MOTION | Untranslated |
| `key_is_phone_calling` | During the call, please try again later! | Missing | English fallback |

The complete filtered files are in `analysis/apk/resources/values/strings-en.xml` and `strings-es.xml`. They do not include the sensitive Firebase configuration.

## 8. Resources and traceability

`analysis/apk/resources` contains 189 files, approximately 1.7 MB:

- 74 own layouts.
- 53 representative images with dimensions and format measured by ImageMagick.
- XML selectors for normal, pressed, selected and on/off states.
- Filtered colour, dimension, style and string values.
- `manifest.json` with SHA-256 and bytes per file.

Deliberately excluded: full APK, private keys, BKS, proprietary fonts, native libraries, third-party layouts and the complete JADX tree.

## 9. Replica priorities

1. Keep the four-tab navigation and the real hierarchy, do not turn it into a new dashboard.
2. Reproduce the empty/loading/error/offline states next to each screen, not only the happy path.
3. Implement AP/audio/LAN/invitation pairing and its permission and progress states before simplifying pairing.
4. Keep preview portrait/landscape, one/four panels and controls for audio, recording, opening and F1.
5. Respect the palette and typography of the original, but treat untranslated strings as defects to fix deliberately, not as a requirement.
6. Do not replicate weaknesses: permissive TLS, secret logging, admin/admin, URLs with password or storage of keys in assets.
7. Keep `legacy_no_inbound` out of the main flow until it is decided whether those screens are recovered.
