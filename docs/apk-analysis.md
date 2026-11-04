# Static analysis of Wibox Fermax v2.2

Analysis date: 2026-10-31. Sheet 1.1.2. Exclusively static and local scope.

## 1. Executive summary

Wibox v2.2 is a native Android app based on XML Views, AppCompat, fragments, ViewBinding and an MVP architecture. It does not use Jetpack Compose. The public package is `com.fermax.wibox`, but the main implementation keeps the `com.quvii.ubell` namespace and the Qualvision SDK. The app combines:

- Device pairing by QR code, WiFi AP, audio, LAN and invitations.
- Account created transparently with `userFreeRegister`, without a visible login screen.
- Dynamic credentials synchronized with the backend and local persistence with SQLCipher.
- Video and audio through native P2P tunnels and `quii://` URLs to mapped local ports.
- FCM for call, answered call and motion.
- HTTP/XML/JSON APIs, OAuth, device shadow HTTP and an MQTT channel present in the SDK.

Priority findings for the replica and for comparison with other variants:

1. The main transport is not SIP nor WebRTC signalling, but own P2P with `libqv-p2p-v2.so`, `liblive_player.so` and `libWebrtcAudio.so` (`E011`, `E021`, `E023`, `E042`).
2. There is MQTT shadow with OAuth and reconnection, but `SUPPORT_DEVICE_SHADOW=false` blocks its initialization by default and the `org.eclipse.paho` classes do not appear in the JADX output. Therefore, it is not claimed to be active nor is its dynamic behaviour (`E037` to `E041`, `E063`, `E073` and `analysis/apk/evidence/decompilation-limits.txt`).
3. The app does not apply certificate pinning. There are clients with `AllowAllHostnameVerifier` and an `X509TrustManager` that validates nothing (`E030`, `E049`).
4. In a `release` build, `DEBUG_HTTP` and `DEV_MODE` are enabled, with BODY logging. There are logs that contain credentials, tokens and invitation links (`E002`, `E003`, `E027`, `E056`, `E057`).
5. The database uses SQLCipher 4.5.4 and sensitive preferences are encrypted, but the encryption configuration is AES/ECB and there is a private PEM key in assets. The secrets have not been copied to the repo (`E045` to `E047`, `E072`).
6. There are no real runtime captures in this sheet. The `start.png` and `guide_display*.png` images are resources, not captures of the app working.

## 2. Artifact, integrity and tools

### 2.1 Verified identity

| Field | Verified value | Evidence |
|---|---|---|
| Original path | `/home/nacho/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk` | `stat` command, `analysis/apk/evidence/toolchain.txt` |
| Size | 45,816,991 bytes | `stat` |
| SHA-256 | `08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce` | `sha256sum`, `analysis/apk/evidence/apk-integrity.txt:1-2` |
| ZIP integrity | No errors in any entry | `unzip -t`, `analysis/apk/evidence/apk-integrity.txt:3-6` |
| Package | `com.fermax.wibox` | `analysis/apk/evidence/AndroidManifest.xml:7` |
| versionName / versionCode | `v2.2` / `18` | `analysis/apk/evidence/AndroidManifest.xml:3-4` |
| minSdk / targetSdk / compileSdk | 21 / 35 / 35 | `analysis/apk/evidence/AndroidManifest.xml:5,10-12` |
| BuildConfig | `release`, `DEBUG=false`, `DEBUG_MODE=false` | `com/quvii/ubell/BuildConfig.java:5-11` |
| Build timestamp | 1744710181402 ms, `2025-04-15T09:43:01Z` | `analysis/apk/evidence/toolchain.txt:11-12` |
| JAR signature | `jar verified` | `jarsigner -verify`, `analysis/apk/evidence/apk-integrity.txt:33-35` |
| Certificate | `CN=Qualvision`, self-signed, RSA 2048, SHA256withRSA | `analysis/apk/evidence/apk-integrity.txt:10-20` |
| Certificate SHA-256 | `5D:DA:34:C8:E6:DC:DD:45:BA:F5:8D:AB:64:5B:F5:96:48:B9:4C:9B:B4:36:02:9C:72:78:D0:C9:10:40:47:81` | `analysis/apk/evidence/apk-integrity.txt:15-17` |

`jarsigner` also reports a self-signed certificate, unvalidated chain, absence of timestamp and packaging read inconsistencies. They are recorded warnings, they have not been hidden. The cryptographic verification of the APK is repeated in `verify_analysis.py`.

### 2.2 Tools

| Tool | Version/origin | Hash or record |
|---|---|---|
| JADX | 1.5.6, official `skylot/jadx` release | ZIP `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974` |
| OpenJDK | 17.0.20.1 | `java -version` |
| Python | 3.14.8 | `python3 --version` |
| UnZip | 6.00 | `unzip -v` |
| ImageMagick | 7.1.2-34 Q16-HDRI | `identify -version` |

Main command:

```bash
/home/nacho/temp/opencode-work/jadx-1.5.6/bin/jadx \
  --threads-count 4 --show-bad-code \
  --output-dir /home/nacho/temp/opencode-work/wibox-jadx \
  /home/nacho/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk
```

Result: exit 3, 6,637 source files and 1,772 resources. JADX reported seven errors. There are four files with an explicit `JADX ERROR` block and other methods marked as incorrect. The `OkHttpUtil.replaceString` method was left undecompiled, but the cited TLS paths are present and readable. Perfect bytecode coverage is not claimed (`analysis/apk/evidence/decompilation-limits.txt`).

## 3. Manifest and exported surface

The application declares `allowBackup="false"`, `supportsRtl="true"`, `resizeableActivity="false"` and `extractNativeLibs="true"`. It does not declare `networkSecurityConfig` or `usesCleartextTraffic`, a custom policy is not invented. Being targetSdk 35, the platform default behaviour applies, but the permissive clients described below weaken TLS regardless of cleartext (`analysis/apk/evidence/AndroidManifest.xml:61-71`).

Permissions: 27 entries, 26 unique. `USE_FULL_SCREEN_INTENT` is duplicated. It includes Internet, WiFi, fine/coarse location, camera, microphone, phone, legacy external storage, Bluetooth, notifications, foreground service, wake lock, biometrics and FCM. The full list is in `analysis/apk/inventory.json`, `permissions` section.

Rule applied to `exported`: the explicit value is kept and the effective one is computed. If it is missing, `true` is used only when there is an intent-filter, otherwise `false`.

### 3.1 Activities

| Short class | Effective exported | Notes |
|---|---:|---|
| `com.quvii.ubell.main.view.LoadingActivity` | true | Launcher, singleTask, portrait and `wibox://wibox.com` deep link |
| `com.quvii.ubell.push.view.CacheActivity` | false | Relay without UI, `taskAffinity=""`, excluded from recents |
| `com.quvii.ubell.main.view.MainTabActivity` | false | singleTask, portrait |
| `com.quvii.ubell.device.add.view.DANewTypeChooseActivity` | false | New or online pairing |
| `com.quvii.ubell.video.view.PreviewActivity` | false | singleTask, initial portrait, `configChanges` |
| `com.quvii.ubell.device.add.view.DAOnlineTypeChooseActivity` | false | Online or manual |
| `com.quvii.ubell.device.add.view.DANewProduceChooseActivity` | false | Product selector |
| `com.quvii.ubell.device.add.view.DANewProduceIntroduceActivity` | false | Reset and QR |
| `com.quvii.ubell.device.add.view.DANewHandleWifiActivity` | false | Manual step to the AP |
| `com.quvii.ubell.device.manage.view.DMWifiListActivity` | false | Legacy WiFi list |
| `com.quvii.ubell.device.manage.view.DMWifiSetActivity` | false | Legacy WiFi configuration |
| `com.quvii.ubell.device.add.view.DADeviceBindActivity` | false | Bind form, singleTask |
| `com.quvii.ubell.device.add.view.DAOnlineDeviceListActivity` | false | LAN results |
| `com.quvii.ubell.device.add.view.DACaptureActivity` | false | QR scanner |
| `com.quvii.ubell.device.manage.view.DMConfigActivity` | false | Settings, singleTask |
| `com.quvii.ubell.device.manage.view.DMPasswordModifyActivity` | false | Password change |
| `com.quvii.ubell.device.manage.view.DMUnlockSettingActivity` | false | Quick unlock and biometrics |
| `com.quvii.ubell.device.manage.view.DMDeviceInfoEditActivity` | false | Device information |
| `com.quvii.ubell.me.view.MeAboutActivity` | false | About and debug access |
| `com.quvii.ubell.fileManage.view.FMImagePagerActivity` | false | Image viewer |
| `com.quvii.ubell.fileManage.view.FMLocalVideoPlayActivity` | false | Local player |
| `com.quvii.ubell.push.view.PushCallActivity` | false | Incoming call, singleTask |
| `com.quvii.ubell.test.view.TestInfoActivity` | false | Debug |
| `com.quvii.ubell.device.manage.view.DMUpgradeActivity` | false | Firmware |
| `com.quvii.ubell.main.view.StartGuideActivity` | false | Declared guide |
| `com.quvii.ubell.device.add.view.DAAddTypeActivity` | false | AP or audio |
| `com.quvii.ubell.device.add.view.DARouterInfoActivity` | false | Router and password |
| `com.quvii.ubell.device.add.view.DAVoiceSetActivity` | false | Pairing by audio |
| `com.quvii.ubell.device.add.view.DAWifiSetActivity` | false | Pairing progress |
| `com.quvii.ubell.share.view.ShareDeviceActivity` | false | QR and invitation link |
| `com.quvii.ubell.device.add.view.DADeviceConfigFailActivity` | false | Pairing error |
| `com.quvii.ubell.share.view.ShareManageActivity` | false | User management |
| `com.quvii.ubell.share.view.ShareBindActivity` | false | Accept invitation |
| `com.quvii.qvlib.util.QvPermissionActivity` | false | Permissions |
| `com.google.android.gms.common.api.GoogleApiActivity` | false | GMS |
| `com.yanzhenjie.permission.PermissionActivity` | false | Permissions |
| `com.huawei.hms.hmsscankit.ScanKitActivity` | false | Huawei ScanKit |

Total: 37 activities, 33 Wibox's own. The complete reference with launch modes and flags is in `analysis/apk/inventory.json`.

### 3.2 Services, receivers and providers

| Type | Class | Effective exported | Notes |
|---|---|---:|---|
| service | `com.quvii.ubell.push.fcm.MyFirebaseMessagingService` | false | `MESSAGING_EVENT` |
| service | `com.quvii.ubell.push.PushHelperService` | false | `shortService`, foreground call |
| service | `org.eclipse.paho.android.service.MqttService` | false | Declared, Paho classes not found in sources |
| service | `com.google.firebase.messaging.FirebaseMessagingService` | false | FCM base |
| service | `com.google.firebase.components.ComponentDiscoveryService` | false | Firebase |
| service | `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` | false | Transport |
| service | `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService` | false | Protected by `BIND_JOB_SERVICE` |
| service | `com.huawei.agconnect.core.ServiceDiscovery` | false | Huawei |
| receiver | `com.quvii.ubell.push.PushHelperReceiver` | false | Notification rejection |
| receiver | `com.google.firebase.iid.FirebaseInstanceIdReceiver` | true | Limited by the `com.google.android.c2dm.permission.SEND` permission |
| receiver | `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver` | false | Transport |
| provider | `androidx.core.content.FileProvider` | false | `com.fermax.wibox.file_provider`, URI grants |
| provider | `com.google.firebase.provider.FirebaseInitProvider` | false | Firebase initialization |
| provider | `com.yanzhenjie.permission.FileProvider` | false | `com.fermax.wibox.file.path.share` |
| provider | `razerdp.basepopup.BasePopupInitializer` | false | Initializer |
| provider | `com.huawei.hms.mlsdk.common.provider.MLInitializerProvider` | false | Huawei |
| provider | `com.huawei.agconnect.core.provider.AGConnectInitializeProvider` | false | Huawei |

Totals: 8 services, 3 receivers and 6 providers. Only LoadingActivity and FirebaseInstanceIdReceiver are exported, the second with Google's signature permission. There are no own boot receivers despite the `RECEIVE_BOOT_COMPLETED` permission.

## 4. Navigation and UI technology

### 4.1 Stack

- XML and Android Views: 74 own layouts extracted, without library layouts in the selected set.
- `AppTheme` derives from `Theme.AppCompat.Light.NoActionBar`, with `colorPrimary` and `colorPrimaryDark` `#78c9e7` (`values/styles.xml:24-29`, `values/colors.xml:39-41`).
- ViewBinding generated for activities/fragments. MVP with `BasePresenter`, models and contracts.
- Four fragments in `MainTabActivity`: devices, events, gallery and settings (`E053`).
- Video with `MyGLSurfaceView`, not Compose. There is Kotlin and coroutines, but no Compose APIs nor `setContent`.
- `values-night/colors.xml` is empty. There is no dark theme of its own. The black viewer and black preview are local states.

### 4.2 Main flow

```text
LoadingActivity
  -> MainTabActivity
       -> Devices -> DANewTypeChooseActivity / PreviewActivity / DMConfigActivity
       -> Events -> MainMessageTypeFragment -> alarm photo
       -> Gallery -> FMImagePagerActivity -> FMLocalVideoPlayActivity
       -> Settings -> MeAboutActivity -> TestInfoActivity (hidden gesture)

DANewTypeChooseActivity
  -> DANewProduceIntroduceActivity -> DACaptureActivity
  -> DAOnlineTypeChooseActivity -> DAOnlineDeviceListActivity / DADeviceBindActivity

New pairing:
  QR -> DARouterInfoActivity -> DAWifiSetActivity or DAVoiceSetActivity
     -> DADeviceBindActivity -> MainTabActivity
```

`StartGuideActivity`, `DANewProduceChooseActivity`, `DAAddTypeActivity` and `DMWifiListActivity` have no external inbound references in the analyzed code. `DMWifiSetActivity` is only called from that legacy list. Relationships would be invented if they were taken as active, which is why they are marked as `legacy_no_inbound` in the UI inventory. `LoadingPresenter` opens `MainTabActivity` in both branches, never `StartGuideActivity` (`com/quvii/ubell/main/presenter/LoadingPresenter.java:55-64`, `E006`).

The declared deep link opens `LoadingActivity`, but no reading of `Intent.getData()` was found in that class. Link parameter resolution is not claimed. The invitation is processed when scanning the QR code or pasting text with `DeviceHelper.getShareInfo` (`E008`).

The actionable inventory of 38 screens and states is in `docs/ui-inventory.md` and `analysis/apk/ui-inventory.json`.

## 5. Network, endpoints, headers and authentication

These services have not been contacted. Dynamic hosts cannot be resolved statically.

| Area | Static endpoint/contract | Authentication/headers | Evidence |
|---|---|---|---|
| Bootstrap | `https://fermax.qvcloud.net:443` | OEM `G0021`, APP_ID 2021 | `AppConfig.java:23-36`, `E001`, `SdkManager.java:283-318`, `E005` |
| Upgrade | `https://matainde.qvcloud.net:1443/webs/version/app/version_check.jsp` | GET, query `os/appid/version/clientid`, `Accept-Language` | `AppVersionManager.java:272-313`, `E050` |
| Discovery | Regional URLs returned by P2P | `QvLocationManager`, regional cache | `QvLocationManager.java:70-96`, `E034` |
| User/no-login | `/auth/nologin;jus_duplex=up/down`, `/auth/user;jus_duplex=up/down` | Cookies and account/device credentials | `HttpDeviceConst.java:29-44`, `E010` |
| OAuth | `/qvoauthv2/token`, GET | Query `grant_type/client_id/usr/pwd/...`, header `token`, not Bearer | `QvOAuthManager.java:85-113`, `E033`, `OkHttpUtil.java:407-414`, `E029` |
| Alarm | `UserAlarm`, `DeviceAlarm` | Cookie and XML payload | `HttpDeviceConst.java:31,42`, `E051` |
| Push | `/openapi-tdk/client/v2/push/token`, `/client/push/token`, `/pushserver/api/v1/client/register` | FCM token, IDs, HMAC-SHA1 in v2 | `QvPushHelper.java:64-76`, `E048`, `E057` |
| HTTP Shadow | `/openapi-tdk/device/status`, `/multi`, `/sub`, `/unsub` | `token` header | `QvShadowConfig.java:11-18`, `E041` |
| MQTT | `ssl://...` for mqtts, `tcp://...` for mqtt | `C_<account>_<client>`, access token as password | `QvMqttManager.java:416-434`, `E040` |
| Local CGI | `https://<ip>:<cgiPort>/tdkcgi`, legacy `http://` routes in unbind and depending on device/cgiScheme | Credentials or Digest MD5 | `DeviceCustomRequestHelp.java:38-51`, `E031`, `RetrofitUtil.java:299-309,433-482`, `E074` |
| Password encryption | `https://<user>:<password>@<ip>:<port>/tdkcgi/passwordencryption` | Credentials in userinfo | `QvNetDeviceCoreHelper.java:107-122` |
| AP pairing | `192.168.111.1:443` | Fallback `admin/admin`, alternative `adminapp2` + hash | `DAWifiSetModel.java:51-100`, `E014` |
| Media | `quii://<user>:<password>@127.0.0.1:<port>/...` | Dynamic password and `dataEncodeKey` | `DeviceHelper.java:846-915`, `E011` |
| Firebase | FCM and `tdkvdp.firebaseio.com` | Embedded config, API key not reproduced | `values/strings.xml:60,73-81`, `E056` |
| Privacy | `http://94.101.81.118/privacy/privacy_policy.html` | Constant, current flow uses local HTML | `AppConst.java:69`, `E064`, `MainTabActivity.java:66-76` |
| Connectivity | `https://www.apple.com`, `https://www.google.com` | GET with 5 s timeout | `QvNetUtil.java:225-240`, `E065` |

The HTTP routes are documented as code constructs, not as tested connections. The absence of `usesCleartextTraffic` can block them depending on the platform, but it does not turn the code into HTTPS nor remove the risk in custom clients (`E074`).

### 5.1 Account and credentials

There is no user login screen. On the first bind, `DeviceHelper` chooses `bindDevRegister` if no username exists. It generates a UUID, calls `userFreeRegister`, stores account and password, and then performs `userLogin`. On later startups `AppInfo` or the migrated preferences are reused (`E004`, `E010`). This does not mean absence of authentication: the app creates an opaque account for the user and then authenticates against the backend.

OAuth uses `grant_type=password` and sends `usr` and `pwd` as query parameters in a GET. Access and refresh tokens are JWTs, the client decodes `exp` from the payload. It uses the cached token if more than five minutes remain, refresh if it also exceeds that margin and the `token` header (`E033`, `E034`). The tokens are stored encrypted in the `oauth` SharedPreferences (`E036`). Device authentication can use Digest MD5 after a 401, with `nc00001` and `suiji` constants (`E031`, `E032`).

## 6. Pairing, devices and actions

### 6.1 Formats

Device QR code: four space-separated fields, `apName uid authCode model` (`E007`, `E012`). Invitation: query with `InviteCode`, `OwnerId`, `OemId`, `AppVersion`, `DeviceId`, `From` and `AppId`, compatibility with `G0021` is required (`E008`). The OEM comparison uses `"G0021".contains(deviceShareInfo.getOemId())`, not strict equality, a detail worth not copying without deciding its semantics.

### 6.2 AP and audio

AP pairing searches for the device on the LAN and, if there are no results, uses `192.168.111.1:443` with `admin/admin`. If there is a response, it tries an alternative with `adminapp2` and `SHA-256(authCode)` (`E014`). Router SSID and password are Base64 encoded, they do not constitute encryption (`E015`).

Audio pairing configures 19 frequencies from 4,000 Hz in 150 Hz steps and emits SSID plus the last four characters of the UID and the WiFi password (`E016`). Progress lasts 150 seconds, polling every 2 seconds, connecting/sending/waiting/failure states and manual retry (`E017`, `E018`). Bind has a 35-second timeout and `retry(4)` only for two codes and in the mode that allows it, with a 5-second wait (`E009`, `E010`).

### 6.3 List, preview and actions

The list is loaded from SQLCipher, checked against `getDevList` and updated with dynamic credentials. The visible states are empty, list, refreshing, online/offline and permissions (`E020`). The actions include:

- Preview of one or four panels, channels, high/low/audio+photo quality, listen, talk, capture and recording (`E021`, `E023`).
- lock1 and F1 opening with quick unlock, password or biometrics. Opening has a 15-second timeout (`E019`, `E025`).
- Call to the concierge, sharing with permissions, WiFi change, time synchronization, name editing and firmware update.
- Invitations with preview/playback/alarm/unlock/F1/callGuard permissions. The UI hides the create button when it reaches 100 (`ShareManageActivity.java:139-150`).
- Events in pages of 15, associated photo, individual/total deletion and local gallery (`E051`, `E052`, `E062`).

### 6.4 Device and app settings

Extracted from `res/layout/activity_dm_config.xml` and from `smali_classes2/com/quvii/ubell/device/manage/view/DMConfigActivity.smali`, and checked against the equivalent screen of this app. The order is the one seen on screen.

| Row | Layout | Observed behaviour |
|---|---|---|
| Device name | `tv_device_name` | Opens `activity_dm_device_info_edit`, with name and UID |
| Change device password | `tv_modify_password` | AP password form |
| Change opening password | `tv_modify_unlock_password` | Opening password form |
| Opening settings | `tv_unlock_setting` | `activity_dm_unlock_setting` with `key_quick_unlock` and `key_biological_unlock`, plus `key_biological_unlock_tip` |
| Intercom Way | `ll_select_voice_model` | Two values: `key_hold_talk` and `key_full_duplex_speech` |
| Display mode | `ll_fps` | Three values by index: `key_video_fps_high`, `key_video_fps_low` and `key_video_fps_picture`. `showFps(I)` maps 1 to low, 2 to photo and the rest to high. Note `key_record_fail_fps_hint`: in photo mode video cannot be recorded |
| Lock Number | `ll_select_lock_num` | `android:visibility="gone"`, not shown to the user |
| Wi-Fi settings | `ll_config_wifi` | `activity_dm_wifi_list` and `activity_dm_wifi_set` |
| Users | `tv_user` | `activity_share_manage`, `activity_share_device` and `share_dialog_detail` |
| F1 activation time | `ll_f1_time` | `F1_MIN_TIME=1` and `F1_MAX_TIME=0xa`, that is 1 to 10 seconds, with `MyBottomLoopDialog` |
| Call reception | `rl_ring_alert` | Switch |
| Motion alerts | `rl_move_alert` | Switch |
| Firmware version | `ll_upgrade` | `activity_dm_upgrade` with current version, available version and update button |
| Synchronize date and time | `bt_syn_time` | Button with `key_time_sync_success` and `key_time_sync_fail` as the only results |
| Delete | `bt_del_device` | Removes the device from the account |

The app settings tab comes from `res/layout/fragment_me.xml`. Its visible rows are `ll_sound` (sound), `ll_vibration` (vibration), `ll_about` (information) and `ll_exit` (exit). The `ll_auto_rotate`, `ll_channels_switch_mode` and `ll_preview_time` rows exist but are hidden. The `activity_me_about` screen shows the icon, the name, the version and `bt_privacy_policy` with the `key_privacy_policy_url` text, which points to `http://94.101.81.118/privacy/privacy_policy.html`.

`key_f1_password` appears in `R$string.smali` and in no layout nor code: it is an orphan string and does not correspond to any row.

## 7. WebSocket, MQTT, SIP, WebRTC and P2P

| Technology | Static result | Evidence/scope |
|---|---|---|
| WebSocket | Only the OkHttp API, without `newWebSocket` or `WebSocketListener` in `com.quvii` | `analysis/apk/evidence/security-search.txt` |
| MQTT | Implemented in shadow SDK with Paho, QoS 1, OAuth token and incremental reconnection, but disabled by default | `SUPPORT_DEVICE_SHADOW=false` prevents `QvShadowHelper.init()` and no override was found (`E063`, `E073`). Paho classes not located in sources, behaviour not executed |
| SIP | Models for device SIP parameters, without a SIP call client in Wibox | Playback uses P2P, `E021`, `E023`, negative search recorded |
| WebRTC | `libWebrtcAudio.so` provides audio processing, it does not prove PeerConnection | There is no `org.webrtc`/`PeerConnection` in `com.quvii`, `E023` |
| P2P | Main transport: `libqv-p2p-v2.so`, local ports, `quii://` and native player | `E011`, `E021`, `E042`, `E068` |

MQTT subscribes to `tdk/client_api/users/get/<account>/<client>/#` and `tdk/client_api/sys/get/<client>/#` with QoS 1. It publishes heartbeat on `tdk/client_api/sys/update/<client>` every 300 seconds. The reconnection wait increases 0 to 10 seconds and the service is probed when that level is exceeded (`E037` to `E040`).

## 8. Persistence and cryptography

| Area | Static implementation | Evidence |
|---|---|---|
| DBFlow | `database` base, version 11, Device/AppInfo/AppTask entities | `AppDatabase.java:14-17`, `E069` |
| SQLCipher | 4.5.4, key from `QvEncryptKeyStoreUtil.get()`, `PRAGMA cipher_migrate` | `SQLCipherHelperImpl.java:15-36`, `E045` |
| `config` preferences | FCM token, server and sensitive values with AES, normal settings in cleartext | `SpUtil.java:61-91`, `E043` |
| WiFi | Save password is active by default, SSID and value are encrypted | `SpUtil.java:188-190,212-214,348-350`, `E044` |
| OAuth | Access/refresh encrypted in the `oauth` SharedPreferences | `QvOAuthSpUtil.java:23-47`, `E036` |
| Local key | Random AES-128, wrapped with RSA AndroidKeyStore | `QvEncryptKeyStoreUtil.java:240-251,452-498`, `E046`, `E047` |
| AES mode | `AES/ECB/PKCS7Padding` | `QvEncryptKeyStoreUtil.java:33-49` |
| Device | SHA-256 hex if capability, keeps values already >=64 characters | `QvEncrypt.java:17-19`, `E048` |
| Push | `HmacSHA1(clientId-timestamp, clientId)` | `QvPushHelper.java:64-76`, `E048` |
| Digest | MD5 with fixed nc and cnonce | `DeviceAuthHeaderInterceptor.java:24-44`, `E031` |
| Media | Paths by API: legacy external, externalFilesDir 29, filesDir >=30, separate cache | `SdkManager.java:252-282`, `E005` |
| FileProvider | Not exported, URI grants, `root-path path=""` and `external-path path="."`. The permissions provider also declares `.` paths | `res/xml/provider_paths.xml:3-17`, `res/xml/permission_file_paths.xml:3-20`, `E075` |

## 9. Errors, timeouts and retries

| Area | Static policy | Evidence |
|---|---|---|
| Startup | Opens main upon receiving login state or after 10 s | `LoadingPresenter.java:29-51`, `E006` |
| General HTTP | `retryOnConnectionFailure=true`, 20 s base timeout | `OkHttpUtil.java:112-120,562-588`, `E027`, `E030` |
| Auth/alarm | Connect 30 s, read 35 s auth and 60 s alarm, write 20/60 s | `OkHttpUtil.java:136-170` |
| OAuth | One retry if there is a cached token, clears it, 403 cuts | `QvOAuthManager.java:290-320`, `E035` |
| Bind | 35 s timeout, `retry(4)` only two codes and permitted mode, 5 s wait | `DeviceHelper.java:339-355,706-785`, `E009`, `E010` |
| CGI | `retry(1)` with port cleanup for timeout/local case | `DeviceCore.java:244-301`, `E009` |
| Device status | Deprecated method: 20 s timeout and `retry(2)` only timeout | `DeviceHelper.java:965-977` |
| Pairing | 150 s, 2 s polling, manual retry and error screen | `DAWifiSetPresenter.java:156-200,243-294`, `E017`, `E018` |
| Preview | 90 s by default, reconnection and `needResume=3` | `PreviewPresenter.java:513-550,955-965`, `E024`, `E026` |
| Unlock | 15 s timeout, sending/success/failure/timeout states | `PreviewPresenter.java:673-696`, `E025` |
| Call | Auto-reject at 30 s, event 20 closes if it was answered on another terminal | `PushCallPresenter.java:33-49`, `E059` |
| Events | Pages of 15, 502/XML/code derive in visible error, manual retry | `MainMessageListModel.java:206-253`, `E052` |
| Firmware | Progress every 1 s, retries errors for 60 s, `-2` completes | `DMUpgradeModel.java:23-68,80-104` |

No generalized exponential backoff was found. The mechanisms are counters, fixed delays, polling and limited retry per area.

## 10. Security, obfuscation and pinning

| ID | Static finding | Severity | Evidence |
|---|---|---|---|
| SEC-01 | Empty TrustManager and `AllowAllHostnameVerifier` in common clients and updater | High | `OkHttpUtil.java:562-588`, `E030`, `AppVersionManager.java:101-126`, `E049` |
| SEC-02 | `DEBUG_HTTP=true`, `DEV_MODE=true` and BODY logging in release | High | `App.java:185-192`, `E002`, `OkHttpUtil.java:67-90`, `E027` |
| SEC-03 | Logs with user/password/accountId, FCM, share links, keys and WiFi data | High | `App.java:148-151`, `E003`, `SdkManager.java:90-96`, `PushHelper.java:82-123`, `DAVoiceSetPresenter.java:187-197` |
| SEC-04 | `client.txt` is a private PEM key, BKS with embedded password | High | `E028`, `E072`, hashes in `asset-hashes.txt`, content not copied |
| SEC-05 | `admin/admin` fallback, another CGI route includes credentials in userinfo | Medium | `DAWifiSetModel.java:51-100`, `E014`, `QvNetDeviceCoreHelper.java:107-122` |
| SEC-06 | AES-ECB and Digest MD5 with fixed nc/cnonce | Medium | `E031`, `E046`, `E047` |
| SEC-07 | `quii://` URLs with password and logs of `dataEncodeKey` | Medium | `DeviceHelper.java:846-915`, `E011`, `PreviewModel.java:174-181`, `E021` |
| SEC-08 | Legacy HTTP CGI routes and HTTP privacy URL, without Network Security Config or explicit cleartext | Medium | `AndroidManifest.xml:61-71`, `AppConst.java:69`, `E064`, `RetrofitUtil.java:299-309,433-482`, `E074` |
| SEC-09 | `Utils.isRooted` exists, but no call imposing a block was found | Informative | `Utils.java:245-255`, `E071`, `security-search.txt` |
| SEC-10 | Non-exported FileProvider allows URI grants and declares broad root/external paths | Medium | `provider_paths.xml:3-17`, `permission_file_paths.xml:3-20`, `E075`. Real exposure depends on the generated URIs |

The `CertificatePinner` class belongs to OkHttp, but it is not used in `com.quvii`. An own trust store is not the same as pinning. Hostname verification is also disabled in the cited clients. The internal logic of the native libraries has not been decompiled, it is not claimed that all native traffic shares this weakness.

Obfuscation: there are one-letter packages and renamed classes alongside descriptive `com.quvii.ubell` names, and shortened resources that JADX reassigns. There is no R8/ProGuard mapping nor general DEX encryption. It is partial obfuscation, not a complete barrier.

## 11. SDKs and native libraries

Versions with evidence in `analysis/apk/evidence/library-versions.txt`:

| Component | Version |
|---|---|
| Android Gradle Plugin / ViewBinding | 8.6.0 |
| AppCompat / Material | 1.2.0 / 1.4.0 |
| AndroidX Biometric | 1.1.0 |
| Kotlin coroutines | 1.6.1 |
| RxJava 2 | 2.1.3 |
| OkHttp | 3.9.0 (`okhttp3/internal/Version.java:8-9`) |
| Retrofit | Not determined statically |
| SQLCipher for Android | 4.5.4 |
| Firebase cloud messaging / components | 17.0.1 / 17.1.0 |
| Google Play services basement | 18.1.0 |
| Huawei ScanKit | `scanplus:2.1.0.300` |
| Huawei AGConnect Core | 1.4.0.300 |
| Qualvision P2P | The log declares `p2p version: v3.2`, it is not equated to a global SDK version |

ABIs: `arm64-v8a` and `armeabi-v7a`. Libraries: `libqv-p2p-v2`, `liblive_player`, `libWebrtcAudio`, `libvoiceRecog`, `libsqlcipher`, `libscannative`, `libeapilrender`, FFmpeg (`avcodec`, `avfilter`, `avformat`, `avutil`, `swresample`, `swscale`) and `libmsoptimize` only on arm64. SHA-256 hashes per file in `analysis/apk/evidence/native-hashes.txt`.

## 12. Comparison with morluto/rea and related firmware

The APK does not contain literal morluto/rea identifiers in sources or DEX strings. The `analysis/rea` sheet does provide already verified external references (`analysis/rea/verification.txt: REA_ANALYSIS_VERIFIED`, exit 0). They are secondary sources, not direct evidence of the APK behaviour.

| Area | Confirmed comparison | Consequence for the replica |
|---|---|---|
| REA | It is a generic reverse engineering tool, not a Wibox client nor a source of endpoints (`docs/rea-analysis.md:5-7`) | Reuse analysis ideas, not Wibox logic |
| P2P | The duhow notes describe NTS/P2P and the APK contains native P2P, but there is no wire format nor compatibility proof (`docs/rea-analysis.md:99-118`, `E042`) | Do not call the APK protocol NTS without additional evidence |
| MQTT | APK: shadow with OAuth, QoS 1 and dynamic endpoint, disabled by default. duhow: plain local MQTT 1883 and own topics (`E037` to `E041`, `E063`, `E073`, `docs/rea-analysis.md:240-250`) | Do not exchange brokers, topics or security models |
| Media | APK: `quii://` and native P2P. The cmos486 blog describes custom WebRTC/RTSP (`E011`, `E021`, `docs/rea-analysis.md:248-256`) | Do not present WebRTC/RTSP as a stock contract |
| Actions | Unlock, F1, guard and calls share domain, not necessarily transport or payloads | Reuse functional vocabulary with separate adapters |
| OEM | `G0021` appears in the APK and in duhow B007/B013 firmwares, without proof of universal cloud compatibility (`E001`, `E008`, `docs/rea-analysis.md:44-46`) | Do not fix compatibility by OEM alone |
| Secrets | APK and customs show different credential and plain channel risks | Log redaction, secure storage and validated TLS in each adapter |

Pending for a future binary comparison: the `CN=Qualvision` certificate and its hash, `com.quvii.ubell`, the `fermax/matainde.qvcloud.net` hosts, the native set, the `userFreeRegister` flow and the XML/MVP structure. NTS/cloud compatibility or payload equality by analogy are not claimed.

## 13. Limitations and explicit HYPOTHESES

- The app was not installed or uninstalled, no phone, emulator, device network or real cloud services were used.
- There are no real captures nor dynamic validation of navigation, streaming, auth, pairing or notifications.
- **HYPOTHESIS:** the MQTT channel could take part in shadow updates if the Paho classes are available at runtime. Test needed: additional DEX inspection and controlled execution outside this sheet. It is not presented as a fact.
- **HYPOTHESIS:** the `legacy_no_inbound` screens could be remnants of old flows. Test needed: review history or variants. Only the absence of current static references is documented.
- **HYPOTHESIS:** the TLS behaviour of native libraries could differ from the Java client. Test needed: native analysis and controlled tests. The Java finding is not generalized to all native traffic.
- JADX finished with seven errors and certain methods are not readable. The conclusions are limited to visible evidence and complementary searches.

## 14. Traceability and deliverables

- `analysis/apk/inventory.json`: complete model of components, network, security, SDKs and evidence.
- `analysis/apk/ui-inventory.json`: 38 screens, states, 71 navigation relationships and resources.
- `analysis/apk/evidence/code-evidence.md`: 75 blocks `E001` to `E075`, each with `file:line` or command origin.
- `analysis/apk/evidence/ui-code-evidence.md`: 38 navigation/state blocks.
- `analysis/apk/evidence/AndroidManifest.xml`: decoded manifest.
- `analysis/apk/evidence/apk-integrity.txt`: hashes, certificate and signature.
- `analysis/apk/evidence/library-versions.txt` and `native-hashes.txt`: versions and hashes.
- `analysis/apk/resources/manifest.json`: 189 resources with size, dimensions when applicable and SHA-256.
- `analysis/apk/verify_analysis.py` and `verify_ui_inventory.py`: positive validation and negative control.
- `analysis/apk/verification.txt`: commands, exits and final outputs.
