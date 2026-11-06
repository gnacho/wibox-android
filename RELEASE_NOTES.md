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
