# REA analysis and Wibox sources for a maintainable Kotlin clone

## 1. Executive summary

**FACT:** `https://github.com/morluto/rea`, `main` branch, commit `d48e472fa6a664cacbb400560e673533005795ce`, is **REA: Reverse Engineer Anything**, a local reverse engineering tool with CLI and MCP. It is not a Wibox client nor a Fermax SDK (`README.md:6-10`, `package.json:2-10`). In its tree `wibox`, `fermax` and `duox` do not appear: `git grep -in -e wibox -e fermax -e duox HEAD` finished with exit 1 and no output.

**FACT:** REA is useful for inspecting the APK and historical captures, not as a cloud specification. It offers Android static analysis and offline reading of HAR, SAZ and pcap (`README.md:187-229`, `docs/web-network-captures.md:1-6`, `docs/pcap-analysis.md:1-6`).

**CODE FACT, additional source:** `duhow/wibox`, commit `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7`, does contain a local implementation for patched Wibox. Its scripts confirm four-byte UART commands, HTTP CGI and MQTT topics for opening, call, forwarding, F1, doorbell and Home Assistant (`include/wibox_codes.txt:1-24`, `include/web/run.cgi:1-21`, `include/bin/listener_mqtt.sh:37-88`). This proves the behaviour of the **patched firmware**, not the cloud API of the original APK.

**THIRD-PARTY DOCUMENTATION:** `duhow/wibox` also documents an NTS cloud agent, an IP with UDP 8300 and HTTPS 443, authentication with `DevId` and `AuthPwd`, registration and P2P connection. They are notes and a pasted log in `docs/system.md:148-193`, not a capture or binary that we have checked. That service was not contacted.

**THIRD-PARTY CLAIM:** the cmos486 blog describes another custom firmware with RTSP, MQTT and direct WebRTC two-way audio, without depending on SIP for its board. It is evidence about `cmos486/wibox-media`, not about the factory firmware or cloud. Source: [cmos486, "Firmware custom para el Fermax WiBox"](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), sections "Implementado en el custom firmware" and "La tarjeta de Home Assistant".

**Conclusion:** there is still no complete specification of the Wibox cloud of the APK. There is, however, usable evidence to bound the domain and local protocols. `core:cloud` must not adopt the topics of the patch nor the WebRTC of the fork as if they were the manufacturer's contract.

## 2. Sources, revisions, licences and method

### 2.1 Pinned sources

| Source | Revision and role | Licence and reuse |
|---|---|---|
| `https://github.com/morluto/rea` | `main`, `d48e472fa6a664cacbb400560e673533005795ce`, package `rea-agents` 6.8.0. Analysis tool | MIT, `LICENSE:1-13`. Only identifiers and facts |
| `https://github.com/duhow/wibox#1` | `main`, `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7`. Patched firmware and Wibox notes | GPL-3.0, `LICENSE:1-18`. No substantial code is copied |
| `https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant` | Article published 2026-09-12, read 2026-10-31. Custom `cmos486/wibox-media` | Summarized with a link. No code is reused |

For `morluto/rea`, `git remote get-url origin`, `git branch --show-current` and `git rev-parse HEAD` confirmed origin and revision. Its date is `2026-10-31T03:13:07+08:00`. For `duhow/wibox`, the same commands confirmed the indicated commit, dated `2026-10-18T15:52:35+02:00`. Both clones remained in `/home/nacho/temp/opencode-work/rea-reference` and `/home/nacho/temp/opencode-work/duhow-wibox-reference`, respectively, with `git status --short` showing no changes.

The `#1` fragment of the URL provided for duhow is kept as the identifier of the request. The analysis covers the repository root at the pinned commit, not an issue extracted from that fragment.

### 2.2 Evidence taxonomy

- **CODE FACT:** seen directly in a script or contract of the cited commit. It does not imply execution or hardware testing.
- **DOCUMENTED FACT:** a repository states it in documentation or in a kept log. It is cited, but not raised to behaviour verified by us.
- **THIRD-PARTY CLAIM:** comes from the blog or another linked project and has not been checked against its code.
- **HYPOTHESIS:** interpretation pending proof. It is never used to complete an endpoint or sequence.
- **PROPOSAL:** recommendation for Kotlin, not behaviour attributed to Wibox.

### 2.3 Coverage and limits

- Only static analysis and public reading. No credential was used, no firmware was executed and Wibox, NTS, MQTT, SIP or real WebRTC were not contacted.
- There was no Wibox APK nor an own network capture. Therefore, no source is correlated with the `applicationId`, version or certificate of the target APK.
- In REA, 6860 tracked files were reviewed, including 5429 `.ts`, with `git ls-files`. It contains no `.kt` or `.kts`.
- The five REA submodules are generic analysis engines and were not initialized (`.gitmodules:1-15`). They provide no Wibox evidence.
- duhow does not include the proprietary Sofia binary nor `npc_nts_client_config.ini`. Its cloud claims cannot be completed with the scripts present only.
- The firmware versions tested by duhow are `V500.R001.A103.00.G0021.B007` and `V500.R001.A103.00.G0021.B013` (`README.md:17-19`). Universal compatibility is not deduced.

## 3. Comparability between sources, APK and firmware

| Object | What it proves | What it does not prove |
|---|---|---|
| REA | How to inspect artifacts and keep evidence | No Wibox endpoint, token or action |
| duhow scripts | Local behaviour of the patched firmware at the commit | APK API, factory cloud or behaviour of all versions |
| duhow `docs/system.md` notes | Third-party findings about Sofia, NTS, ports and secrets | That the current APK uses exactly those messages or that the IP is still valid |
| cmos486 blog | Declared features of a later custom fork | Original cloud protocol, APK authentication or the need for WebRTC in factory |
| Target APK, not yet analyzed | Will be the primary reference for the app to clone | It cannot be replaced by analogies with these projects |

**FACT:** REA is not associated with any Wibox version. Its `jadxApp` only defines generic fields `package`, `version_name`, `version_code`, SDK and permissions that it could extract from the APK (`src/android/JadxProtocol.ts:73-80`).

**HYPOTHESIS:** REA may have been provided as a tool to produce the reference from the APK, not as a reference implementation. Context is missing to confirm it and it does not affect the technical conclusions.

## 4. REA architecture and languages

REA organizes dependencies towards the domain. The dispatcher selects CLI or MCP, composition creates providers, the server translates requests and the application shares workflows. Contracts and domain contain schemas and semantics (`docs/architecture.mermaid:4-28`, `docs/architecture.mermaid:72-83`).

| Component | Responsibility | Evidence |
|---|---|---|
| `scripts/rea.mjs`, `src/cli.ts` | CLI and stdio/HTTP MCP selection | `scripts/rea.mjs:9-20`, `scripts/rea.mjs:31-68` |
| `src/main.ts`, `src/server/` | MCP, sessions and transport | `src/main.ts:41-69` |
| `src/application/` | Shared workflows | `docs/architecture.mermaid:21-28` |
| `src/contracts/`, `src/domain/` | Schemas, Evidence and errors | `src/contracts/errorSchemas.ts:37-38`, `src/domain/evidence.ts:84-107` |
| `src/android/`, `bridge/android/` | Static APK with external JADX | `docs/android-analysis.md:1-14` |
| `src/captures/`, `src/pcap/` | Stored captures | `docs/pcap-analysis.md:1-6` |
| `bridge/`, `native/` | Python, Java, Swift and native bridges | `package.json:49-108`, `docs/architecture.mermaid:65-69` |

**Value for Kotlin:** the separation between contracts, domain and adapters is reusable as an idea. There is no Wibox logic to port nor Kotlin files in REA.

## 5. Matrix of Wibox features by provenance

| Feature | Factory, duhow documentation | duhow custom, code | cmos486 blog | Status for the APK clone |
|---|---|---|---|---|
| Cloud login | NTS starts authentication and registration (`docs/system.md:168-179`) | Not applicable | Not covered | No APK request/response |
| Token renewal | Not documented | Not applicable | Not covered | Unknown |
| Pairing | `DevId`, `AuthPwd` and Verify Code (`docs/system.md:148-153`) | Local AP to configure WiFi (`include/bin/ap_start.sh:13-28`) | WiFi configuration after flashing | Original sequence missing |
| LAN discovery | UDP 5000 and string `ASZENO.SEARCH.V4.1` (`docs/system.md:276-290`) | Inherited from Sofia, not reimplemented | Not covered | Candidate to compare |
| Opening | No cloud message | UART and MQTT/HTTP confirmed (`include/web/run.cgi:5-14`, `include/bin/listener_mqtt.sh:45-50`) | Opening declared | Plausible domain, cloud transport unknown |
| Start/stop call | No cloud message | `START_CALL` and `STOP_CALL` (`include/wibox_codes.txt:19-21`) | Custom WebRTC call | Original sequence missing |
| F1 and guard | No cloud message | `START_F1`, `STOP_F1`, `CALL_GUARD` (`include/wibox_codes.txt:17-23`) | Not the focus | Do not assume cloud equivalence |
| Forwarding | No cloud message | `PUSH_STATE_0/1` and `forward` topic (`include/bin/listener.sh:147-155`) | Not the focus | Do not assume cloud equivalence |
| Doorbell and states | No cloud events | `ALARM_REPORT`, `HANG_UP`, `CMD_STOP_RING` (`docs/codes.md:18-23`, `docs/codes.md:34-38`) | Events declared | Cloud models unknown |
| Audio/video | Not specified | Audio marked as pending in README (`README.md:9-15`) | RTSP and WebRTC declared | Do not transfer WebRTC to the APK |
| SIP | Not implemented in duhow | There is no SIP client in the tree | SIP daemon of another fork remains, but is not the focus | No evidence of cloud SIP |
| MQTT | It is not the documented NTS cloud | Plain 1883 and local topics (`USAGE.md:10-25`) | MQTT for HA declared | Do not use as factory cloud |

**FACT:** this matrix does not prove that the manufacturer lacks features. It separates what is confirmed for each implementation and avoids filling gaps by analogy.

## 6. Endpoints, transports and models

### 6.1 Cloud and Sofia, duhow documentation only

The NTS Client is described as an intermediary between the cloud and the local management port `TCP/34567`. It requires `AuthPwd`, which can be passed with `-E`, and takes `DevId` from persistent storage (`docs/system.md:148-153`).

Documented sequence:

1. Agent startup and network/NAT detection.
2. Connection to authentication over HTTPS and login.
3. Device registration on the server.
4. P2P request and creation of logical TCP connections (`docs/system.md:168-193`).

| Endpoint or channel | Documented value | Evidence class and limit |
|---|---|---|
| Cloud UDP | `47.254.155.166:8300` | Third-party log, `docs/system.md:168-175`. Not contacted nor verified as current |
| Cloud HTTPS | `47.254.155.166:443` | Same limit. There is no HTTP path, headers or body |
| Local management | `TCP/34567` | `docs/system.md:75-81`. There is no message schema in the repo |
| Sofia web | `TCP/80` and `TCP/443` | `docs/system.md:75-81`. ONVIF appears disabled |
| Autodiscovery | `UDP/5000` | `docs/system.md:75-81`, `docs/system.md:276-290` |
| Additional UDP | `UDP/5002` | Unknown purpose, `docs/system.md:75-81` |
| `system_sofia` | `TCP/6683`, loopback according to notes | 1032-byte packets, `docs/system.md:103-112` |
| `interDebug` | `TCP/9527` after signal | Debug console, `docs/system.md:195-212` |

**DOCUMENTED FACT:** the cloud configuration is associated with `npc_nts_client_config.ini` and `/var/cloud/states` states (`docs/system.md:59-60`, `docs/system.md:95-101`). Those files are not included for inspection, so complete paths, keys or format are not known.

### 6.2 Local HTTP of the patched firmware, confirmed in code

`setup.sh` starts BusyBox httpd on port 80, or 81 if 80 is busy (`include/web/setup.sh:13-18`). The front end calls `./cgi-bin/run.cgi?c=` (`include/web/index.html:38-63`).

| CGI command | Confirmed effect | Response or limit |
|---|---|---|
| `open` | Writes `TRANSFER_CMD_UNLOCK_DOOR` to UART | No physical ack nor JSON (`include/web/run.cgi:5-6`) |
| `dspy` | Writes `START_CALL` | `include/web/run.cgi:7-8` |
| `dspn` | Writes `STOP_CALL` | `include/web/run.cgi:7-8` |
| `fone` | Activates F1, waits 3 seconds and deactivates | `include/web/run.cgi:9-10` |
| `guard` | Writes `CALL_GUARD` | `include/web/run.cgi:9-10` |
| `unlock` | Starts call, opens after 2 seconds and closes 1 second later | Timed sequence, not lock confirmation (`include/web/run.cgi:12-14`) |

The CGI returns `Content-type: text/html` if the function finishes, 404 if the command does not exist and 500 if the UART write fails (`include/web/run.cgi:5-21`). There is no visible authentication in `setup.sh` nor in the CGI. This is a risk of the patch if the network is exposed, not proof that the factory cloud lacks authentication.

### 6.3 Local MQTT of the patched firmware, confirmed in code

The root is `wibox/${MODEL}`, the client id is `wibox_${MODEL}` and the optional credentials are read from `/mnt/mtd/mqtt.conf` (`include/bin/mqtt_functions.sh:9-30`). The guide says plain MQTT was tested on 1883 (`USAGE.md:10-25`) and the build uses `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`).

| Topic under `wibox/${MODEL}` | Payload and action | Evidence |
|---|---|---|
| `/door/set` | `ON` or `PRESS` writes unlock. Publishes `OFF` after one second | `include/bin/listener_mqtt.sh:42-51` |
| `/door` | `ON/ONLINE` starts call, `OFF/OFFLINE` ends it | `include/bin/listener_mqtt.sh:52-63` |
| `/forward` | `ON/OFF` changes forwarding with `PUSH_STATE_1/0` | `include/bin/listener_mqtt.sh:64-75` |
| `/f1` | `ON`, `OFF` or `PRESS`, the latter with a 3-second pulse | `include/bin/listener_mqtt.sh:77-88` |
| `/ding` | `ON/OFF` for the doorbell | `include/bin/listener.sh:122-140` |
| `/ding/last` | Retained timestamp of the last call | `include/bin/listener.sh:122-127` |
| Root | `init`, `config`, `online`, `offline`, retained will and keepalive 300 | `include/bin/listener_mqtt.sh:37-43`, `include/bin/listener.sh:65-74` |
| `/wifi/stats` | JSON with metrics, including RSSI | `include/bin/mqtt_wifi_stats.sh:6-26` |

Home Assistant discovery uses `homeassistant/<type>/<unique_id>/config` and publishes entities for door, F1, call, forwarding, doorbell, last call and WiFi quality (`include/bin/mqtt_config_homeassistant.sh:5-16`, `include/bin/mqtt_config_homeassistant.sh:64-98`).

### 6.4 Local UART, confirmed in code and table

The device is `/dev/ttySGK1`. The listener reads four bytes per code (`include/bin/listener.sh:3-7`, `include/bin/listener.sh:86-99`). The documentation describes an `FB` prefix and a sum checksum, with examples and meanings (`docs/codes.md:1-12`).

| Name | Frame | Direction and function |
|---|---|---|
| `ALARM_REPORT` | `FB 11 00 1C` | Input, call from street |
| `TRANSFER_CMD_UNLOCK_DOOR` | `FB 12 01 1E` | Output, relay 1 opening |
| `HANG_UP_0` | `FB 13 00 1E` | Input, timeout without answer documented as 30 seconds |
| `START_CALL` | `FB 14 01 20` | Input/output, call start |
| `STOP_CALL` | `FB 14 00 1F` | Output, call end |
| `CALL_GUARD` | `FB 15 00 20` | Output, call to guard |
| `START_F1`, `STOP_F1` | `FB 17 01 23`, `FB 17 00 22` | Output, F1 relay |
| `PUSH_STATE_0`, `PUSH_STATE_1` | `FB 19 00 24`, `FB 19 01 25` | Input/output, disables or enables WiFi/calls |
| `CMD_STOP_RING` | `FB 23 00 2E` | Input, call taken by physical phone |

The source of frames is `include/wibox_codes.txt:1-24` and the interpretation `docs/codes.md:12-39`. Fields marked `unknown` must remain unknown.

**Real listener coverage:** the codes file is not the same as a list of supported features. `listener.sh` does handle `CMD_RESET` to return to factory (`include/bin/listener.sh:92-99`), but it has no branches for all defined codes. For example, `HANG_UP_1` is defined in `include/wibox_codes.txt:4-6` and has no `elif is_code HANG_UP_1` in `include/bin/listener.sh:86-159`. Other codes such as `MCU_STATE`, `SAVE_ADDR` or `CALL_GUARD_ERROR_2` remain as observations, not as a complete state machine.

### 6.5 REA models that are not Wibox

| Model | Real purpose | Evidence |
|---|---|---|
| `Evidence` | Provenance with `evidence_id`, `provider`, `normalized_result`, `confidence`, `authority` and `limitations` | `src/domain/evidence.ts:84-107` |
| `AnalysisErrorProjection` | REA error with `code`, `category`, `retryable` and `remediation` | `src/contracts/errorSchemas.ts:7-38` |
| `jadxApp` | Generic APK metadata | `src/android/JadxProtocol.ts:73-80` |
| ADB/Frida device | Analysis devices, not video intercoms | `src/domain/adb/adbDeviceAnalysis.ts:471-485`, `src/domain/fridaDiscovery.ts:3-13` |

## 7. Authentication, pairing, credentials and tokens

### 7.1 Factory cloud

**DOCUMENTED FACT:** NTS uses `DevId` and an `AuthPwd` Verify Code. The startup with `-E ${m_dVerifyCode}` appears in the list of Sofia commands (`docs/system.md:130-146`) and the requirement is explained in `docs/system.md:148-153`.

**UNKNOWN:** there is no login request, response, access token, refresh token, expiry, logout or renewal. The phrase "Device is registered" in the log (`docs/system.md:173-179`) does not allow reconstructing the pairing nor the exchanged messages.

### 7.2 Documented persistent secret

`docs/system.md` places base64-encoded secrets in `/mnt/mtd/Config/secret` (`docs/system.md:305-318`). Base64 is encoding, not proof of encryption.

| Field | Interpretation of the documentation | Certainty |
|---|---|---|
| `TD` and `TD5` | `deviceToken` split into sections | Documented, without message format or expiry |
| `K1`, `K2` | Two 32-character alphanumeric keys | Documented, use unknown |
| `VC` | Verify Code printed in the hardware QR code | Documented |
| `DVC` | Device Verify Code, `innerAuthCode` or `m_dVerifyCode` | Documented |
| `HVC` | Hash Verify Code | The text says "Apparently a SHA256", algorithm not confirmed |
| `FD` | `Activated` or `NotActive` | Documented |
| `ASC`, `RST` | Flags | Uncertain meaning even in the notes |

No values are copied. These fields do not justify calling the mechanism OAuth or JWT nor inventing a renewal policy.

### 7.3 WiFi, shell and MQTT credentials

- The duhow guide documents different factory root passwords depending on console (`INSTALL.md:39-52`). They were not used nor are they reproduced here.
- The same guide says the Sofia WiFi credentials are encrypted and that they could not recover them (`INSTALL.md:90-107`). It does not identify algorithm or key.
- The patch stores MQTT in `/mnt/mtd/mqtt.conf`, copies it to `/tmp`, exports values with `xargs` and passes them as `-u` and `-P` arguments (`include/bin/mqtt_functions.sh:9-26`). This exposes a secret surface in files and command line that must not be copied in Kotlin.
- The AP of the patch derives SSID `IDS7938` plus four characters of the UDID, password from the full UDID and IP `192.168.111.1` (`include/bin/ap_start.sh:13-28`). It is local provisioning, not cloud pairing. If the UDID is observable, it is not a robust secret.
- `STA_TO_AP` is documented as the code received after holding the button for more than 5 seconds, which causes a restart and AP mode (`docs/codes.md:34-35`). It is a documented physical provisioning mechanism, not cloud account creation.

### 7.4 REA provides no Wibox authentication

The MCP UUID session is created with `randomUUID()` after `initialize` (`src/server/http/serveStreamableHttp.ts:201-220`, `src/server/http/serveStreamableHttp.ts:404-417`). It is not user login. The REA configuration only selects engines, paths, limits and timeouts (`src/config/environment.ts:47-134`). The lists of sensitive names serve to exclude credentials from captures, not to authenticate (`src/application/environment/environmentBuildReport.ts:23-79`).

## 8. Discovery, devices, actions and states

### 8.1 Documented autodiscovery

The notes propose UDP listening and broadcast of the string `ASZENO.SEARCH.V4.1` to port 5000. The pasted response includes version B007 (`docs/system.md:276-290`). There is no complete binary datagram, response schema, authentication or test with the APK.

### 8.2 Actions and events of the patch

The listener turns UART events into MQTT and local alarms. `ALARM_REPORT` publishes doorbell and timestamp, `HANG_UP_0` marks a missed call and `CMD_STOP_RING` indicates that the physical phone was picked up (`include/bin/listener.sh:122-146`). The visible alarm states are `Ring`, `Missed`, `Answered` and `Open door` (`include/web/index.html:38-45`).

Automatic opening can chain `ALARM_REPORT`, `START_CALL`, unlock and `STOP_CALL` when certain flags exist (`include/bin/listener.sh:108-135`). It is a state machine of the patch, not a confirmed cloud state machine.

### 8.3 Device list

There is no cloud model of installations or devices in duhow. The ADB and Frida models of REA do not fill that gap. The MQTT `MODEL` identifier is derived from the hostname, it does not prove the `DevId` or the manufacturer identity scheme (`include/bin/mqtt_functions.sh:20-30`).

### 8.4 VDS address, different from cloud pairing

duhow records `SAVE_ADDR 0x04` after configuring with the P2 button and pressing the doorbell (`docs/codes.md:30-31`). The cmos486 blog states that the WiBox needs a VDS address to hear the correct push button and that a wrong address prevents receiving calls with no visible error. This address identifies the destination on the VDS bus according to that source. It must not be modelled as a cloud `deviceId` nor as proof of NTS pairing without additional evidence. Blog source: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), "Paso 7: dile que piso es".

## 9. Real time: MQTT, SIP, WebRTC and other channels

| Channel | What is confirmed | Applicability limit |
|---|---|---|
| NTS/P2P | Log documents P2P and logical TCP after authentication (`docs/system.md:184-193`) | There is no wire format nor demonstrated relationship with the current APK |
| duhow MQTT | Local pub/sub, will, retained, keepalive and action/state topics | Patched firmware, not factory cloud |
| REA WebSocket | Loopback CDP (`src/browser/CdpConnection.ts:79-113`) | Analysis tool, not Wibox |
| REA SSE | Streamable MCP (`src/server/http/serveStreamableHttp.ts:180-199`) | Analysis tool, not Wibox |
| SIP | Blog says the daemon of the segator fork is still available | Third-party claim about a custom. It does not prove SIP in stock nor give endpoints |
| WebRTC | Blog declares direct two-way audio against `cmos486/wibox-media` and an HA board | Not checked against code and not automatically applicable to the APK |
| RTSP | Blog declares H.264 video over RTSP in the custom | Same as above |

Blog source for SIP, WebRTC and RTSP: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), sections "Implementado en el custom firmware", "El origen de todo" and "La tarjeta de Home Assistant".

**UNKNOWN:** no source provides SIP registrar or URI, extension, password, SDP negotiation, WebRTC signalling, STUN/TURN servers, ICE credentials, RTSP URL or credentials. Nor is the original cloud event channel detailed. The blog describes at most the intention and declared architecture of the fork, not an implementable media contract.

**HYPOTHESIS:** the original system may combine cloud, local channel and media, but the sources do not allow deciding the exact topology of the APK. The proof must come from libraries, classes, manifest and traffic of the target version.

## 10. Cryptography and communications protection

- **Cloud:** `AuthPwd`, token and the `K1/K2` fields are documented, but not their cryptographic use. `HVC` is only suspected to be SHA-256 (`docs/system.md:305-318`). There is no proof of pinning, command signing or payload encryption.
- **Sofia WiFi:** the guide claims encryption without credential recovery, without algorithm (`INSTALL.md:90-107`).
- **Patched MQTT:** plain, build `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`). Optional user/password does not encrypt the channel.
- **Patched HTTP:** BusyBox httpd on 80/81 without visible TLS configuration (`include/web/setup.sh:13-18`).
- **Patched update:** MD5 compares bytes before and after writing, it does not authenticate origin (`include/bin/update_firmware.sh:23-49`).
- **REA:** Evidence SHA-256 and the Cutter bridge HMAC belong to the tool (`src/domain/evidence.ts:35-46`, `src/cutter/cutterBridgeMac.ts:1-16`). They do not reveal Wibox cryptography.

## 11. Firmware and compatibility

### 11.1 duhow

The README marks Sofia as disableable, plus HTTP/MQTT opening, Home Assistant, toolchain and Dropbear as implemented. Audio is left unmarked (`README.md:9-15`). The tested versions are B007 and B013 (`README.md:17-19`).

The guide indicates telnet possibly available on B007 and disabled on new versions like B013, so it recommends serial access (`INSTALL.md:35-43`). There is a typographic difference between `G021` in that sentence and `G0021` in the README, it must not be normalized without checking the real version.

Sofia and the patch are mutually exclusive according to `USAGE.md:27-37`. `/mnt/mtd/factory` returns the original boot.

The update writes only `mtd4` `/usr`, limits size and compares MD5 (`include/bin/update_firmware.sh:1-18`, `include/bin/update_firmware.sh:23-49`). The guide requires backups and serial recovery (`INSTALL.md:3-12`, `INSTALL.md:109-126`). None of those steps was executed.

### 11.2 cmos486 blog

The article declares a Goke GK7102S SoC, cramfs, VDS bus, UART and telnet access depending on version. It adds B010 with telnet and B013 serial only, and recommends treating new versions as serial only until proven otherwise. It also states that a wrong VDS address prevents receiving calls with no visible error. They are third-party claims not checked here with code or laboratory.

Source: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), sections "Informacion WiBox", "Lo que tocaba averiguar" and "Paso 1: lee antes de flashear".

The custom uses `/mnt/mtd/sip_media.conf`, with `mqtt_host`, `mqtt_user` and `mqtt_pass`, and the article says video/RTSP start disabled. It is not the same `/mnt/mtd/mqtt.conf` file as the duhow patch, so configurations must not be merged. Source: same article, "Paso 5: configuralo".

## 12. Errors, retries and timeouts

### 12.1 Factory cloud

**UNKNOWN:** there is no error envelope, codes, backoff, retries, rate limit, idempotency, reconnection or resynchronization of the original cloud. The NTS log messages are not an error contract.

### 12.2 duhow custom, confirmed in code

| Mechanism | Real policy | Limit |
|---|---|---|
| CGI | 404 for unknown command, 500 if UART write fails | No JSON nor business code (`include/web/run.cgi:5-21`) |
| MQTT opening | Publishes `OFF` after one second | It is command reset, not physical ack (`include/bin/listener_mqtt.sh:45-50`) |
| Keepalive | `mosquitto_sub -k 300` and retained offline will | It does not imply exactly-once delivery (`include/bin/listener_mqtt.sh:37-43`) |
| MQTT heartbeat | 45-second read timeout and restart after 60 seconds in certain states | Restart of the custom device, not backoff (`include/bin/heartbeat_mqtt.sh:7-24`) |
| Network healthcheck | 10 attempts, 20-second wait, 3-second ping timeout, restart when exhausted | Do not copy as cloud policy (`include/bin/heartbeat.sh:3-10`, `include/bin/heartbeat.sh:33-61`) |
| Call | `HANG_UP_0` is documented as a 30-second timeout | Original firmware datum observed by a third party (`docs/codes.md:18-23`) |

### 12.3 REA, not reusable as Wibox values

REA has typed errors with `retryable` and `remediation` (`src/contracts/errorSchemas.ts:7-38`). MCP uses JSON-RPC with `error.code`, `error.message` and `id: null` (`src/server/http/serveStreamableHttp.ts:111-120`). JADX uses 120000 ms by default (`src/domain/analysisResourceLimits.ts:109-115`) and CDP 20000 ms per command/handshake (`src/domain/browserDeadline.ts:8-18`). None is a Wibox default.

## 13. Configuration, secrets and risks

| Risk | Evidence | Bounded recommendation |
|---|---|---|
| Replacing the APK with custom references | duhow and the blog describe different firmware | Keep `stock-cloud`, `duhow-local` and `cmos486-custom` profiles separate |
| Local HTTP without visible auth and actions over GET | `include/web/setup.sh:13-18`, `include/web/index.html:50-63`, `include/web/run.cgi:14-18` | Do not expose nor reuse that design. Require authentication, authorization and CSRF protection in any local clone |
| MQTT without TLS | `USAGE.md:14-25`, `scripts/81_mosquitto.sh:59-63` | Do not assume confidentiality. For Kotlin, TLS and broker validation according to evidence and environment |
| Secrets through argv/files | `include/bin/mqtt_functions.sh:14-26` | Do not copy the export with `xargs` nor arguments with password. Atomic storage and redacted logs |
| AP with password derived from UDID | `include/bin/ap_start.sh:13-20` | Do not treat an observable identifier as a secret |
| Firmware without authenticity verification | Only MD5 in `include/bin/update_firmware.sh:23-49` | Verified signature and origin for any own update |
| Cloud documented by an old IP | `docs/system.md:168-179` | Do not fix an IP. Resolve endpoints from APK/config/capture of the target version |
| Absence of physical ack | UART write and timed OFF | Do not report "door open" without an event that confirms it |
| Debug and inherited services | `system_sofia`, interDebug and telnet documented | Do not enable services by analogy. Review the surface in the real APK/firmware |
| Captures with credentials | TD/VC/DVC fields and configs | Sanitized fixtures and no real tokens |

REA also has a plain HTTP listener `/mcp` with a loopback default (`src/server/http/serveStreamableHttp.ts:44-45`, `src/config/mcpHttpEndpoint.ts:13-16`). Its deployment risk is not part of Wibox and was not audited here.

## 14. Confirmed, hypothesis and unknown

### 14.1 Confirmed directly in code

- UART, commands and events of the duhow patch at the pinned commit.
- Local CGI and its timed sequences.
- Topics, payloads, retained, will and keepalive of the duhow MQTT.
- WiFi/AP configuration and MQTT identity derivation of the patch.
- Startup, Sofia/patch exclusion and update MD5 verification.
- REA static analysis and offline capture features.

### 14.2 Documented by third parties, pending primary comparison

- NTS agent, IP/ports, authentication, registration and P2P.
- Sofia ports, autodiscovery and `system_sofia` packets.
- Fields of `/mnt/mtd/Config/secret` and attributed meaning.
- Versions and firmware access paths.
- VDS, hardware and behaviour described by cmos486.
- RTSP, WebRTC, SIP and features of the cmos486 fork.

### 14.3 Explicit hypotheses

- **HYPOTHESIS:** REA may have been provided as an APK analysis tool, not as a protocol reference.
- **HYPOTHESIS:** the original APK could reuse NTS/P2P or some related channel. It must be tested with classes, libraries and capture, not deduced from the Sofia name.

### 14.4 Unknown

- Cloud API, current hosts, paths and versions.
- Login, pairing and device list request/response.
- Refresh, expiry, logout and storage in the APK.
- NTS wire format and exact APK-device-cloud relationship.
- Cloud actions, ack, states, order and delivery guarantees.
- SIP, WebRTC, WebSocket or factory media.
- TLS, pinning, cryptography, errors, rate limit and idempotency.
- Compatibility with the specific version of the target APK.

## 15. Recommended contract for a maintainable `core:cloud`

Everything in this section is a **PROPOSAL**. The local data provide domain vocabulary, not the cloud contract.

### 15.1 Boundaries

- `core:cloud`: pure Kotlin, models, use cases, session, errors and ports. No Android nor concrete HTTP client in the interfaces.
- `cloud:fermax`: DTOs and endpoints of the confirmed APK version. No DTO leaks into the domain.
- `local:duhow`, only if it is decided to support it: separate adapter for UART/HTTP/MQTT of the patch. It is not presented as cloud.
- `media`: SIP/WebRTC/RTSP only when a primary source defines the target mechanism.
- `infra:auth`: atomic storage and redaction. Android Keystore is evaluated by need, not by habit.

### 15.2 Candidate operations

| Operation | Minimum contract | Condition to enable it |
|---|---|---|
| `authenticate` | Mechanism and typed response | Observed request/response and errors |
| `refreshSession` | Single-flight and time margin | Refresh, expiry and revocation confirmed |
| `logout` | Local deletion and remote revocation if it exists | Confirmed sequence |
| `pairDevice` | Identity, Verify Code or real data | Original flow and confirmed permissions |
| `listDevices` | Devices and pagination | Observed cloud model |
| `discoverLocalDevices` | Result and LAN coverage | Confirmed datagrams and authentication |
| `performAction` | Action, resource, ack and correlation | Observed cloud command and response |
| `observeEvents` | `Flow<CloudEvent>` with explicit gaps | Observed channel, order, replay and deduplication |
| `close` | Releases resources without implicit revocation | Always |

`openDoor`, `startCall`, `stopCall`, `setForward` and `triggerF1` are plausible domain names thanks to the patch. They must not be published as supported cloud operations until there are manufacturer messages and responses.

### 15.3 Models and ports

- `AuthSession`: opaque secrets, optional refresh and `expiresAt` only if it exists. Redacted `toString`.
- `TokenStore`: `load`, `save`, `clear`, atomic and separated by account/environment.
- `Device`: stable id, name, capabilities and explicit unknown state.
- `DeviceCapability`: opening, call, forwarding, F1 or others only if the device declares them or they are confirmed.
- `CloudAction`: type, resource, parameters and idempotency only if the server allows it.
- `CloudEvent`: type, device, sequence/time if they exist and preserved unknown payload.
- `CloudTransport` and `RealtimeTransport`: request/response and isolated events.
- `Clock`: expiry, backoff and deterministic tests.

### 15.4 Errors and robustness

Sealed `CloudError` hierarchy for authentication, authorization, missing resource, conflict, rate limit, network, timeout, protocol, unsupported capability and unknown. Keep provider codes as sanitized metadata.

1. One renewal in flight per session.
2. Do not invent refresh if the APK does not prove it.
3. Retry only idempotent reads or actions with confirmed idempotency.
4. Do not repeat openings after an ambiguous response. The CGI/MQTT of the patch offers no physical ack and is not a confirmation model.
5. Respect `Retry-After` if it exists. Backoff with jitter and injectable limit.
6. Separate connection, read, operation and realtime timeouts.
7. Mark event loss when reconnecting. MQTT retained in the patch does not imply cloud semantics.
8. Do not log tokens, passwords, Verify Codes, full UDIDs or sensitive payloads.

### 15.5 Minimum tests

- Sanitized fixtures per version and unknown fields.
- Login, refresh, expiry, revocation and concurrency.
- Pagination, duplicates and device capabilities.
- 401, 403, 409, 429, 5xx, network, timeout and invalid JSON.
- No retry of an ambiguous physical action nor false ack.
- Reconnection, duplicates, gaps and `Flow` close.
- Log redaction and absence of secrets in fixtures.
- Golden DTO/domain contracts per APK version.
- If duhow is supported, separate tests for UART frames and topics without mixing them with `cloud:fermax`.

## 16. Comparison plan with the APK

1. Record SHA-256, `applicationId`, `versionName`, `versionCode`, signatures and provenance. REA exposes the generic model in `src/android/JadxProtocol.ts:73-80` and commands in `docs/android-analysis.md:219-253`.
2. Inventory the manifest, components, permissions, services, receivers and deep links with `inspect_android_package`.
3. Search for `Sofia`, `NTS`, `DevId`, `AuthPwd`, `Verify`, `34567`, `5000`, cloud, token and media classes and resources. A coincident name will be a clue, not proof of equivalence.
4. Follow message construction and consumption with `inspect_android_method` and `trace_android_references`. REA declares that it does not resolve reflection, JNI or runtime dispatch (`src/contracts/android/androidToolContracts.ts:166-170`).
5. Review native/JNI libraries if NTS, TLS or media is not in DEX. Do not turn DEX absence into total absence.
6. Extract endpoints and models only with producer and consumer evidence. Record class, method, overload and artifact.
7. Look for real storage: preferences, DB, files, Keystore, `Config/secret`, migrations and session deletion.
8. Compare NTS and autodiscovery with authorized datagrams/captures. Do not use the documented IP as a test destination.
9. Analyze offline captures. HAR/SAZ do not repeat requests (`docs/web-network-captures.md:1-6`) and pcap does not acquire traffic (`docs/pcap-analysis.md:1-6`).
10. Separate profiles if the APK, duhow firmware and cmos486 fork differ. Do not average tokens, paths, topics or media.
11. Only then fix DTOs, sequences, errors, retries and `core:cloud` adapters.

## 17. REA features useful for that work

| Operation | Provides | Limit |
|---|---|---|
| `inspect_android_package` | Package, SDK, permissions, manifest and components | It does not run or authenticate the APK (`src/contracts/android/androidToolContracts.ts:52-69`) |
| `search_android_classes` | Searches local classes | It does not prove runtime (`src/contracts/android/androidToolContracts.ts:98-110`) |
| `inspect_android_class` | Members and R8 mapping | Primary observed names (`src/contracts/android/androidToolContracts.ts:113-140`) |
| `inspect_android_method` | Method with smali fallback | It can be incomplete (`src/contracts/android/androidToolContracts.ts:142-164`) |
| `trace_android_references` | Static uses | No reflection/JNI/runtime (`src/contracts/android/androidToolContracts.ts:166-170`) |
| `inspect_android_jni_bindings` | DEX/JNI relationships | It does not load libraries (`src/contracts/android/androidToolContracts.ts:23-50`) |
| `inspect_web_network_capture` | HAR, mitmproxy, SAZ | Offline (`docs/web-network-captures.md:1-6`) |
| `inspect_pcap_capture` | Endpoints, DNS, SNI, protocols and URI | Coverage limited by the capture (`docs/pcap-analysis.md:40-65`) |

## 18. Closing

**FACT:** REA does not contain the Wibox protocol. duhow provides patched local code and third-party cloud notes. cmos486 provides claims about another custom. No combination of the three allows claiming that we know the APK cloud.

**Usable result:** vocabulary of actions and events, discovery and NTS candidates to compare, specific risks not to copy and a tool for the primary analysis of the APK.

The structured inventory is in `analysis/rea/inventory.json`. It distinguishes `source` REA, `additional_sources`, `duhow_wibox.code_confirmed`, `duhow_wibox.third_party_stock_notes` and `cmos486_third_party_claims`. The local verifier checks structure, evidence, presence or justification of authentication/devices/real time and a negative control without modifying deliverables.

## 19. Record of the four passes

1. **Full delivery:** REA and duhow were pinned by commit, their licences and the blog were read, and architecture, endpoints, models, transports, features, authentication, devices, realtime, cryptography, errors, configuration and risks were inventoried.
2. **Expert reread:** the NTS sequences, pairing versus provisioning, VDS versus cloud identity, UART actions, listener coverage and SIP/WebRTC/RTSP limits were reviewed. The difference between defined codes and handled codes was added.
3. **Search for errors and omissions:** the duhow licence was corrected so as not to infer an undeclared variant, risks of GET without authentication were added, and components, negative evidence and validation of incomplete files were reinforced.
4. **Polish:** coherence between report and inventory, `file:line` references, cited fragments, separation of sources and absence of secrets were checked. The final command and its output are recorded in `analysis/rea/verification.txt`.

## 20. Verified REA run over the APK

### 20.1 Scope and toolchain

After the repository analysis, REA 6.8.0 was run against the Wibox v2.2 APK already identified by SHA-256. The official `rea-agents` package published on npm was used, with the `jadx-headless-mcp` 0.7.1 provider. The JAR has SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75` and JDK 17 enumerates it without errors.

The build from the checkout was not the final route: Node 26.12.1 rejected the `transform` TypeScript mode used by the metadata script. REA was not modified to hide that failure. The official precompiled package was installed in `/home/nacho/temp/opencode-work/rea-runtime` and `scripts/rea.mjs` was invoked. All analyses were static, without devices, credentials or connections to the Wibox cloud.

### 20.2 Generated evidence

The complete results are in `analysis/rea/wibox-*.json`. The `analysis/rea/wibox-inspect-android-package.json` artifact confirms the APK hash, package, version, SDK, 6,674 classes and 1,521 resources. The searches had complete coverage of the REA class index.

| Area | REA method | Checked result |
|---|---|---|
| Package | `inspect-android-package` | Identity and components of the analyzed APK |
| OAuth | `inspect-android-class`, `inspect-android-method` | `OAuthApi.getToken` and `refreshToken` use `/qvoauthv2/token`, the managers build the `password` and `refresh_token` grants |
| Shadow | `inspect-android-class`, `inspect-android-method` | Four API operations and callers of `QvShadowHttpManager` for status, multi, subscribe and unsubscribe |
| Opaque registration | `trace-android-references`, `inspect-android-method` | `UserApi.upChannelForFreeLogin` is used by `QvAuthManager` and `HttpUserAuthManager`, a high-level success body is not inferred |
| Discovery | `inspect-android-method` | `QvLocationManager` reaches `QvP2PManager.queryServiceAddress(typeMask, groupId, callback)` |
| MQTT and P2P | `search-android-classes`, `inspect-android-class` | Classes and constants located, their presence is not turned into dynamic activity nor a reusable wire format |

The Retrofit interfaces return signature but not body, so REA does not show their annotations in `inspect-android-method`. References were traced and the callers with a body were inspected instead of interpreting that absence as absence of contract.

### 20.3 Effect on the implementation

The run confirms the OAuth contract and the Shadow operations already implemented with their fixtures. It also confirms that opaque registration uses `UserApi`, but it does not by itself provide a safe typed response to expose it as a high-level flow.

Regional discovery is not reduced to a fixed HTTP URL: it depends on P2P, service types, region and callbacks. That is why `discoverServices` remains `Unsupported`. MQTT, realtime, P2P and physical actions keep their documented limits. The REA run does not prove runtime behaviour, credential validity, service availability or compatibility with a real account.
