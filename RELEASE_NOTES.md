# v0.4.1 - Accepting an invitation no longer hangs

November 7, 2026

Two fixes over v0.4.0, both around pairing with an invitation. One of them was a real hang: accepting an invitation could sit on "Accepting the invitation" for 75 seconds and then time out.

## What works, verified on a OnePlus Open (CPH2551)

- **The stale userauth session is renewed instead of waited out.** When the gateway does not know the session any more it answers `404` with an empty body on the up channel and never sends a down packet. The app used to ignore the status and keep polling until the timeout. Now it drops the session, opens a new one and retries once, and a second failure surfaces the error immediately. Real logcat of an accept: `UP DONE status=404 bodyLen=0`, one retry, `UP DONE status=200 bodyLen=0`, `DOWN RESPONSE result=100151007 command=accept-nologin-share-invitation polls=0`. The answer arrives in about 470 ms instead of 75 s.
- **An expired invitation is no longer reported as already used, and an already used one is no longer reported as a network error.** The vendor codes are `100151007` (`EmErrServerUExpiredOrIllegalInvitation`, expired or unknown) and `100152007` (`EmErrServerDExceedDevviceShareLimits`, already used), and the app had them swapped and incomplete. The message now tells the truth and asks for a fresh code instead of suggesting a retry that cannot work.
- Both paths were exercised on the phone with a code that had already been consumed: the invitation is parsed, the accept is sent, the gateway rejects it and the screen explains why.

## What does not work yet

- **A successful invitation acceptance on the phone still needs a fresh code from the owner account.** The per-installation enrolment itself is proven: the `bridge/` probe accepted a fresh invitation against the real gateway with `result: 0`, got a new account with its own password, the device model, its powers and an expiry date. Codes expire quickly and the server answers `100151007` for an expired one, so the last end to end run in the app needs a code scanned soon after it is generated.
- **Changing the doorbell WiFi is still out.** The stock commands were never captured on the wire and a wrong write can leave the bell offline with no physical access to recover it.
- Listing, creating, editing and revoking share groups is owner only. With a shared account the gateway answers `100101129` and the app shows "not authorized for this device" instead of an empty list.

## What is not in the repository

Same as in v0.4.0: the vendor native SDK, its client certificate and private key, and the account credentials. The Qualvision root is already versioned as `app/src/main/assets/ca.pem`, and the app hands that file to the SDK, so no extra trust anchor is needed.

## How it was tested

- `core/cloud`: 168 tests, 0 failures, including the two new stale session cases (exactly one retry with a fresh cookie, and a fast failure instead of a 60 s wait).
- `app`: 58 tests, 0 failures, including the invitation error code mapping.
- `:app:assembleDebug` builds, and the APK was installed and driven on the real phone over ADB.

## Pending

- Run the invitation flow in the app with a fresh owner code to watch the success path end to end.
- WiFi configuration, once the stock exchange can be captured or the bell is on a controlled network.

# v0.4.0 - The live view now goes the fast way

November 7, 2026

This release closes the gap with the official app. The video no longer comes in at the wrong size, the decoder is no longer rebuilt on every keyframe, and above all the tunnel is opened by the manufacturer's SDK through its local port instead of the long pure Kotlin path. It is also the first release that can manage the users of the doorbell, and the first that tells the truth when the account has no permission to do it.

## What works, verified on a OnePlus Open (CPH2551)

- Fast path through the vendor SDK. `createP2PClient rc=0` and `LIVE source=sdk-tunnel`: the app opens the doorbell port through the SDK instead of waiting for the MQTT and `p2pconnect` round trip. Before, every attempt fell back to the slow path because a certificate asset was missing.
- The missing asset was not needed. `qv_ca.pem` was a redundant copy of the `ca.pem` already in the repository, so the app now hands that file to the SDK. The vendor client certificate, its key and the `.so` remain local and uncommitted, and a build without them says so once and falls back instead of failing on every call.
- Correct frame size. The app asks for `stream = 2`, the same one the Go daemon uses, so frames arrive at 352x288 with an in-band SPS that matches the decoder. Before it asked for 688x576 with parameters of 352x288, which is where the green blocks came from.
- Correct frame timing. The presentation timestamp of every frame comes from the frame rate announced by the stream, 20 fps, so the decoder no longer runs 25 fps against a 20 fps source. Discarded frames also advance the clock.
- No more decoder rebuilds. The decoder is only rebuilt when the parameter sets really change. When the keyframe repeats the same SPS and PPS it logs `VIDEO RECONFIG omitido, csd ya coincide con el decoder`, and a real failure now prints the exception message instead of an empty line.
- Out of order packets are reordered, not dropped. The RB-UDP tunnel keeps a bounded queue by packet id, delivers in order and only skips a gap after a short timeout, so the H.264 stream no longer carries holes.
- Encrypted media prefix aligned to whole AES blocks, so a prefix that is not a multiple of 16 decrypts what it can instead of aborting the whole chain.
- Device list comes from the cloud. `listDevices` asks the real provider, and the userauth list stays only as a fallback. `performAction` unlocks the door through the same transport, and `observeEvents` emits parsed alarms when a payload source is wired.
- User management. The app speaks the real share group commands of the vendor gateway (`get-share-group-list`, `generate-nologin-share-invitation`, `update-share-group-config`, `delete-share-group`, `get-share-link`) on the authenticated channel. The commands were sent to the wrong endpoint before; the stock app only uses `/auth/user`.
- The users screen is reachable on shared devices too. The row was hidden behind an owner check, so with a doorbell that arrives as shared the whole feature was unreachable. The screen now opens and shows the server answer: `get-share-link` returns `result=0`, and `get-share-group-list` returns `100101129` on an account that is not the owner, which the app shows as "not authorized for this device".
- Read only firmware version for shared devices, and the last frame of the live view persisted per device so the card keeps a thumbnail without opening the stream.
- Tests: 166 in `core/cloud` and 53 in `app`, all deterministic and without network, plus the `bridge` probe in Go with 18 tests under `-race`.

## What does not work yet

- Creating an invitation needs the owner account. The share group commands answer `100101129` (`EmErrNoPowerGetShareGroup`) when the doorbell is shared, so this build cannot issue a fresh invitation code by itself.
- Per installation enrolment is not verified end to end. The probe exists and reproduces the gateway envelope, but it needs a fresh invitation code and the `DeviceId` that the official app prints on the "New user" screen.
- Changing the doorbell WiFi network is not implemented. The command is classified as local only, it was never captured from the stock app and a wrong write can leave the doorbell without network and without physical access to recover it.
- Talk to the visitor is not in the app either. The speaker only exists on the LAN channel, and the cloud tunnel does not expose it.
- Colour still comes out with a green or cyan cast on the Shield's Tegra decoder where it was measured. It has not been measured on the target phone.

## What is not in the repository

The manufacturer's native SDK (`libqv-p2p-v2.so`), its client certificate and key and the doorbell credentials are not published. The binary is proprietary and the certificate is a real key pair from the vendor. Without them the project builds and starts, the live view falls back to the slow path and no tunnel is opened.

To use it locally two things are needed:

- The SDK in `app/src/main/jniLibs/arm64-v8a/libqv-p2p-v2.so`, plus `app/src/main/assets/client.pem` and `client.txt`, extracted from the official APK. The trust anchor is not listed: it is the same Qualvision root already versioned as `app/src/main/assets/ca.pem`, and the app hands that file to the SDK.
- In `local.properties`: `wibox.account`, `wibox.password`, `wibox.clientId` and `wibox.unlockPassword`.

The credentials can also be passed through the environment (`WIBOX_ACCOUNT`, `WIBOX_PASSWORD`, `WIBOX_CLIENT_ID`, `WIBOX_UNLOCK_PASSWORD`).

## How it was tested

- 166 deterministic `core/cloud` tests and 53 `app` tests green, without network.
- 18 Go tests in `bridge` under `-race`, against a fake gateway.
- Real device runs over ADB on a OnePlus Open: `createP2PClient rc=0`, `LIVE source=sdk-tunnel`, frames at 352x288 with the in-band SPS, 20 fps timestamps and a clean picture on screen.
- The users screen was opened on the real device with an account that sees the doorbell as shared, and the log shows the exact answer of the gateway for both commands.

## Pending

- A fresh invitation code from the owner account plus the `DeviceId`, to run the enrolment probe and close the per installation enrolment.
- A capture of the stock app changing the WiFi network, or access to the doorbell access point, to implement the WiFi screen with something more than a guess.
- Confirm whether a busy channel frees itself over time, and measure the colour on the target phone.

# v0.3.0 - Live view through the SDK tunnel

November 4, 2026

First release that manages to open the doorbell camera and paint video on the phone. It is also the first one that tells the truth when it cannot: if the channel is busy, it says so within a second instead of staying black.

## What works, verified on a Honor Magic V5

- Pairing by invitation. The doorbell QR code is accepted and the cloud returns the account and the dynamic password. The `userauth` channel speaks the manufacturer's real contract: upstream POST and downstream long-poll with cookie, XStream envelope with kebab-case fields and an empty version.
- Device token. `get-device-token` returns the `authCode` and the 32-byte `dataEncodeKey` used by the video channel.
- Tunnel to the doorbell. The SDK's `createP2PClient`, waits for the UST service to be ready and `addPortByP2P(deviceId, 0, -1, 0, 9, 1200, 20)` returns a local port. The SDK listens on `127.0.0.1` and publishes the doorbell media port there.
- QUII over TCP. 32-byte plaintext setup with `0xA9`, response with status 0, cipher 2 and SHA profile 1. Then the play with user `adminapp2`, password derived from the `authCode` and target `/mode=real&idc=1&ids=1&ap=2`. H264 and G711A frames arrive and are painted with `MediaCodec`.
- Remote opening. Transparent order 4 inside the QUII session, with the opening password in SHA-256, lock 1 and channel 1. Response `WIBOX-UNLOCK RESP 0000` and `UNLOCK error=0`.
- Local CGI through the same tunnel. `get.device.status` returns MAC, firmware version, model and time zone. `get.product.info`, `get.tfcard.info` and `get.encode.fps` also answer.
- Hang notice. The doorbell sends a transparent 0xFE with the reason. The app translates it and shows it: channel busy, no permission, video off, battery, and so on.
- Busy channel retry. Every 30 seconds, up to 20 attempts, with an on-screen countdown.
- Clean close. The session closes synchronously when leaving the screen and when stopping the service: socket, codec, audio and P2P port. Before, it stayed alive and left the doorbell busy for everyone, including the official app.

## What does not work yet

- The doorbell channel is often busy. The official app says the same when it happens, so it is not a protocol failure. While it is busy there is no video: now the app warns and retries, before it was a black screen with no explanation.
- Waking the doorbell is not solved. Neither `device-awaken` over MQTT nor the SDK's `deviceAwaken` guarantee it. After waking, the state the SDK returns is still the sleep mask and MQTT lists it as offline.
- The path without the SDK (UST, MQTT and RB-UDP tunnel in pure Kotlin) opens the tunnel but receives no candidates on `p2pconnect` on this network. It remains as a fallback and is not used.
- Colour comes out with a green or cyan cast on the Shield's Tegra decoder where it was measured. The BT709 aspects improve it and do not remove it. It has not been measured on the target phone.
- The app cannot manage the account or unpair the doorbell. The provider does not expose those operations and the official app is not touched.

## What is not in the repository

The manufacturer's native SDK (`libqv-p2p-v2.so`), its client certificate and key and the doorbell credentials are not published. The binary is proprietary and the certificate is a real key pair from the vendor. Without them the project builds and starts, but the live view cannot open a tunnel.

To use it locally two things are needed:

- The SDK in `app/src/main/jniLibs/arm64-v8a/libqv-p2p-v2.so`, plus `app/src/main/assets/client.pem` and `client.txt`, extracted from the official APK. The trust anchor is not listed: it is the same Qualvision root already versioned as `app/src/main/assets/ca.pem`, and the app hands that file to the SDK.
- In `local.properties`: `wibox.account`, `wibox.password`, `wibox.clientId` and `wibox.unlockPassword`.

The credentials can also be passed through the environment (`WIBOX_ACCOUNT`, `WIBOX_PASSWORD`, `WIBOX_CLIENT_ID`, `WIBOX_UNLOCK_PASSWORD`).

## How it was tested

- 76 deterministic core tests green, without network: envelope contract, `userauth` channel concurrency, QUII protocol, TCP framing, packet demux, CGI and device permissions.
- App tests green.
- Tests on a real device: installation over ADB, live view startup, reading our own logs and screenshots. The video was seen with the doorbell OSD on screen, and later rounds returned a hang due to a busy channel, which is exactly what the official app said.

## Pending

- Confirm whether the busy channel frees itself over time or whether a zombie session remains in the doorbell. Our leak is closed.
- Measure the colour on the target phone.
- Decide whether the native SDK can be replaced by the UST path once that network works.
- Manage the device from the app once the provider exposes those operations (issue #19).
