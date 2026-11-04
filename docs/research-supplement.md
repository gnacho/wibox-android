# Wi-Box public research supplement, custom firmware and DUOX PLUS

Consultation date: 2026-10-31. Sheet: `leaf-1.2.11`.

## 1. Executive result

- **duhow/wibox is custom firmware, not stock.** The repository builds and writes a modified `/usr` image, replaces the boot and adds Dropbear, HTTP and MQTT. Its notes about Sofia and NTS do not become a QVCloud contract of the APK (`README.md:3-19`, `INSTALL.md:77-117`, `scripts/50_move_run.sh:1-4`).
- **cmos486 and segator are two other custom firmware lines.** They share a base, but they do not have identical capabilities nor the same commit. SIP, RTSP, MQTT, OTA and Prometheus are present in code. The cmos486 return audio uses RTSP with ONVIF backchannel and go2rtc to reach WebRTC, not a native WebRTC server inside the daemon.
- **The Polish blogs deal with Fermax WayFi, not Wi-Box F03266.** They describe other hardware, other firmware and another app. There are only specific correlations with duhow, such as two UART frame meanings. Their Hi3518EV200, their stock RTSP/ONVIF or their partitions cannot be transferred to Wi-Box.
- **CVE-2026-86585 is confirmed by Fermax, CVE and NVD.** It affects DUOX PLUS firmware of Wi-Fi monitors in versions before `01.48.001`, with CWE-347. It does not list Wi-Box F03266 and it is not automatically applicable to that product, to QVCloud or to the forks.
- **The Recon talk and the TROOPERS talk are DUOX PLUS and RFID research.** They prove nothing about Wi-Box, VDS, QVCloud or the Wibox app.
- **The ESP32 project is an electrical bridge.** It uses relays, GPIO and timed sequences to reproduce presses. It does not prove integration with Wi-Box, QVCloud or DUOX.
- **The official F03266 sheet identifies a discontinued VDS product.** It publishes app features and remote update, but it does not publish QVCloud, OAuth, MQTT, SIP, RTSP, WebRTC or a local API.
- **It does not change any compatibility.** This supplement does not modify `analysis/architecture/provider-matrix.json`, does not implement providers and does not present custom capabilities as available in the stock app.

## 2. Methodology and taxonomy

### 2.1 Scope

Only public websites and public code were consulted. The clones remained outside the workspace, in `/home/nacho/temp/opencode-work/wibox-research-supplement`. No scripts, builds, tests or binaries of those projects were executed. No binaries or substantial code were copied to the workspace.

None of these actions was performed:

- Contact QVCloud or devices.
- Use physical hardware, open devices, flash, restart or update firmware.
- Disable Sofia, a cloud or any service.
- Test openings, calls, credentials, exploits or techniques from the talks.
- Run Home Assistant, go2rtc, SIP, MQTT, RTSP or WebRTC against real devices.
- Modify app, core, README, `.unlazy`, other analyses or the normative matrix.

The 25 URLs and APIs downloaded with `curl` responded HTTP 200. The temporal detail, hashes and limitations are in `analysis/research-supplement/verification.txt`.

### 2.2 Source priority

1. Manufacturer, CNA CVE record and NVD.
2. Code and Git history pinned by commit, with file and line.
3. Official conference documentation and product sheet.
4. Blogs with pasted outputs and community forums, always with their independence limit.

Labels used:

- **OFFICIAL**: statement published by manufacturer, CNA or NVD within their scope.
- **CODE**: behaviour visible in the cited commit. It does not amount to execution nor physical testing.
- **DOCUMENTED**: statement from a README, guide or conference page. It is not raised to proven operation.
- **NARRATED**: blog or forum account, even if it contains logs or pasted code. There is no independent reproduction.
- **UNCONFIRMED**: datum that cannot be fixed with the available source.

### 2.3 Preserved baseline

Before the research these hashes were computed:

| File | SHA-256 |
|---|---|
| `docs/rea-analysis.md` | `19bce697e5e1b5482cfd3249a953cb9e32d2761086b97d60f3b5f8bf1d8338c7` |
| `docs/provider-architecture.md` | `675b059d23499ec551115966026e2bb352dbf1b3183b775fdca64a23ba403a63` |
| `docs/dynamic-analysis.md` | `2b7efc28e3987785153e8472ae678e729bf3b0e9837a73092674cdf7925b0160` |
| `analysis/architecture/provider-matrix.json` | `912878dcef3f697cef5d7fc561dc1d1542a241673993343909fbdf4d6dd6a915` |
| `analysis/dynamic/inventory.json` | `f31824799489b4e89712be985c611efc80f6502a830fd513e74c540bb9523ea6` |

The workspace does not expose `.git` metadata, so an own commit is not invented. Preservation is verified by hash and by structural comparison of states in `verify_research.py`.

## 3. Sources, revisions and credibility

All entries were consulted on 2026-10-31. The full URLs, metadata, evidence and machine classification are in `analysis/research-supplement/source-matrix.json`.

| ID | Source and credibility | Product and revision | Licence | Classification |
|---|---|---|---|---|
| `duhow_repo` | Main repo, code | Wi-Box, `main`, `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7` | GPL-3.0, `LICENSE:1-2` | duhow Wi-Box custom |
| `duhow_install` | Guide of the same commit | B007/B013 stock documented, `mtd4` patch | Same GPL-3.0 source | Custom installation, not stock |
| `linuch_part1` | Archived blog, narration with outputs | WayFi, `V500.R001.A003.00.CG0021.B003` | Not evaluated | Historical WayFi |
| `linuch_part2` | Archived blog, narration with boot log | WayFi Hi3518EV200, 47 MB RAM, 16 MB flash | Not evaluated | Historical WayFi |
| `linuch_part3` | Archived blog, narration and pasted script | WayFi, `mtd4` modification and CGI | Not evaluated | Historical WayFi custom |
| `cmos486_blog` | Blog of the fork author | Wi-Box GK7102S, documented download v0.18.14 | Not evaluated | cmos486 custom |
| `cmos486_media_repo` | Main repo, code and history | `custom`, `090854bef1085679fb3f42e431e9ffa2d1ad958c`, `v0.18.15-4-g090854b` | GPL-3.0 | cmos486 custom |
| `cmos486_card_repo` | Main repo, code | `main`, `a0d24354f1daf1cff02ce0cf9381b72d8c90fc14`, `v1.0.1-1-ga0d2435` | Apache-2.0 | Home Assistant UI |
| `segator_media_repo` | Main repo, code and history | `main`, `a64530bb5a17d7b1ce512bf065ccba90ae2d0027`, `v0.18.1-1-ga64530b` | GPL-3.0 | segator/SIP custom |
| `ha_forum_post7` | Forum, community corroboration | Wi-Box without a specific version | Not evaluated | Stock account and cross-reference to custom |
| `esp32_repo` | Main repo, code | `main`, `cb21e44ce0820975dfeff47a53ec915e0976099c` | GPL-3.0 | ESP32 physical bridge |
| `recon_talk` | Official Recon 2026 page | DUOX PLUS and RFID modules, talk 2026-06-20 | Not evaluated | DUOX PLUS/RFID research |
| `troopers_home` | Official general site | Conference context only | Not evaluated | Context, no Wi-Box evidence |
| `troopers_duox_talk` | Official TROOPERS26 page | DUOX PLUS, two-wire digital signal | Not evaluated | DUOX PLUS research |
| `circl_cve` | Aggregator that reproduces CNA/NVD | CVE-2026-86585 | Not evaluated | DUOX PLUS CVE |
| `fermax_advisories` | Official manufacturer advisory | VEO/VEO-XS, `< 01.48.001` | Not evaluated | Primary CVE confirmation |
| `nvd_page` | NVD page | CVE-2026-86585 | Not evaluated | NVD confirmation |
| `nvd_api` | Official NVD API | NVD publication and structured platforms | Not evaluated | Readable NVD confirmation |
| `cve_awg_api` | Official CVE record of the Fermax CNA | CNA publication, CWE and affected range | Not evaluated | Primary CVE record |
| `fermax_product` | Official product sheet | Wi-Box reference 3266, F03266 | Not evaluated | Official VDS stock/product |

The card has a real Apache-2.0 file even though the GitHub API returned `NOASSERTION`. The `LICENSE:1-2` file takes priority over that heuristic. The GPL-3.0 licences are equally checked in each repo's file, not only in the API.

## 4. duhow/wibox: what it proves and what it does not

### 4.1 Hardware and versions

**CODE/DOCUMENTED:** the README links the Wi-Box sheet and declares tests with `V500.R001.A103.00.G0021.B007` and `V500.R001.A103.00.G0021.B013` (`README.md:17-19`). There is no basis to generalize to other versions.

**DOCUMENTED:** the notes describe 18 V DC power, 16 MB flash, `mtd4` as `/usr` in CramFS and `mtd5` as `/mnt` in JFFS2 (`docs/system.md:1-30`). INSTALL places the serial console on `ttySGK2` at 115200 baud, while the intercom commands use `/dev/ttySGK1` (`INSTALL.md:6-12`, `include/web/run.cgi:5-12`). They are different interfaces and functions.

The guide contains a typographic variant `G021` next to `G0021` (`INSTALL.md:39-40`). The discrepancy is kept, without normalizing it as if it were a confirmed new version.

### 4.2 Proof that it is custom

There are four independent pieces of evidence within the same commit:

1. The README says it contains scripts to patch and use the device locally instead of the cloud (`README.md:3-5`).
2. INSTALL builds a custom image and writes only `mtd4` (`INSTALL.md:77-85`, `INSTALL.md:109-117`).
3. The build renames the original `run.sh` to `run-orig.sh` and copies the new content (`scripts/50_move_run.sh:1-4`, `scripts/93_include.sh:11-16`).
4. Factory mode runs the original boot and the guide warns that Sofia and the patch do not work as two simultaneous control planes (`include/run.sh:37`, `USAGE.md:27-37`).

Therefore, HTTP, MQTT, Dropbear and the topics of this repository are not evidence of stock QVCloud.

### 4.3 Sofia and cloud

The README presents Sofia as the original program that can be disabled (`README.md:9`). The custom boot has more nuance: unless configured otherwise, it can invoke `Sofia_temp.sh` for a bounded time to initialize hardware (`include/run.sh:108-126`, `include/bin/Sofia_temp.sh:3-4`, `include/bin/Sofia_temp.sh:36-45`). This does not prove that the stock cloud remains operational. The script then kills the NTS client (`include/bin/Sofia_temp.sh:43-45`).

The notes describe Sofia, ports and an NTS agent with registration and P2P (`docs/system.md:73-101`, `docs/system.md:148-193`). They are third-party documentation inside the repo. They do not include a request/response specification of the APK, tokens, login paths or event format. This supplement did not contact those services and does not reuse their historical IPs.

### 4.4 SSH, HTTP and MQTT

- **Custom SSH:** the build compiles Dropbear 2020.81 and the boot starts it, disabling telnet when Dropbear becomes available (`scripts/82_dropbear.sh:5-9`, `scripts/82_dropbear.sh:47-68`, `include/run.sh:27-35`). It does not prove SSH in stock.
- **Custom HTTP:** BusyBox httpd listens on 80 or 81 and the CGI writes actions to UART (`include/web/setup.sh:13-18`, `include/web/run.cgi:3-21`). There is no visible authentication in those files. A UART write or an `OFF` pulse does not prove a physical effect.
- **Custom MQTT:** the listener subscribes to local topics and translates opening, call, forwarding and F1 (`include/bin/listener_mqtt.sh:37-88`). The guide says the binaries are not included and that the test was plain MQTT on 1883 (`USAGE.md:10-25`). The build sets `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`). It is not the static OAuth MQTT of the APK nor a QVCloud channel.

## 5. Polish blogs: real value and WayFi/Wi-Box limit

The series uses the name **Fermax WayFi** and a JSON with package `com.fermax.wayfi` in the first part. That separates it from `com.fermax.wibox` v2.2 analyzed in the workspace. The first part fixes firmware `V500.R001.A003.00.CG0021.B003`, different from the B007/B010/B013 of the Wi-Box forks.

| Part | What the source provides | Independent comparison | What cannot be claimed |
|---|---|---|---|
| 1, 2019-03-29 | Scan with telnet, HTTP, RTSP, UPnP, ONVIF, QVCloud/UMEYE and an RTSP URI. Includes Nmap/ONVIF outputs and narrated JSON | duhow also documents Sofia and NTS, but for Wi-Box it says ONVIF disabled (`docs/system.md:75-81`). There is no original PCAP nor checked binary | That stock Wi-Box has RTSP or ONVIF open, that it uses the same URI or that the endpoints are still valid |
| 2, 2019-04-04 | WayFi opening, UART, U-Boot, Hi3518EV200, ARMv5, 47 MB RAM and 16 MB flash | The boot logs are pasted in the article. There is no original image analyzed. Wi-Box custom uses Goke drivers and `ttySGK*` in duhow/cmos486 | That Wi-Box F03266 has Hi3518EV200, 47 MB RAM, the same pin layout or the same access |
| 3, 2020-10-26 | Sofia, UMEYE, CramFS, `mtd4` modification, CGI and frames to open/start preview | The `TRANSFER_CMD_UNLOCK_DOOR` and `START_CALL` meanings match `duhow/include/wibox_codes.txt`. The blog serial device is `/dev/ttyAMA2`, not `/dev/ttySGK1` | That the whole command set, the CGI, the MTD map or the SoC is identical in Wi-Box |

**Conclusion:** they provide reverse engineering history, protocol candidates and two UART correlations. Most of it is still narration with pasted evidence, not current primary verification. The existence of a pasted script in part 3 proves what the author said, not that it was executed on Wi-Box.

## 6. cmos486/wibox-media: capabilities confirmed in code

Commit: `090854bef1085679fb3f42e431e9ffa2d1ad958c`. `git describe` returns `v0.18.15-4-g090854b`. The README links a v0.18.14 download and a later v0.18.15 tag exists. That is why the commit is fixed, HEAD is not presented as if it were a single release.

| Capability | Concrete evidence | Classification and limit |
|---|---|---|
| RTSP with H.264 and PCMA | SDP `trackID=0` H.264, `trackID=1` PCMA and default listener 8554 (`src/sip_media/rtsp_stream.c:29`, `src/sip_media/rtsp_stream.c:445-475`, `docs/architecture.md:85-100`) | Custom CODE. It does not prove RTSP in stock |
| ONVIF return audio | `sendonly` PCMA `trackID=2` track and input RTP processing (`src/sip_media/rtsp_stream.c:453-460`, `src/sip_media/rtsp_stream.c:480-535`, `src/sip_media/rtsp_stream.c:829-880`) | Custom CODE. It is RTSP backchannel, not native WebRTC |
| End-to-end WebRTC | The published architecture is WiBox RTSP -> go2rtc -> WebRTC browser (`docs/homeassistant-two-way-audio.md:7-20`) | DOCUMENTED + CODE of the card. It requires go2rtc and HA components |
| Call/UART events | Discovery with `ringing`, `established`, `hang_up` events and JSON publication with `call_id`, sequence and state (`src/sip_media/mqtt.c:617-640`, `src/sip_media/mqtt.c:1646-1664`) | Custom CODE. It is not the FCM/QVCloud model |
| Snapshots | Publication of `snapshot/image` in base64, MQTT entities and atomic write (`src/sip_media/mqtt.c:476-527`, `src/sip_media/mqtt.c:868-909`, `src/sip_media/snapshot_file.c:10-56`) | Custom CODE. A real image was not verified |
| OTA | HTTPS updater, GitHub download, MD5, MTD write and MQTT buttons (`docs/updates.md:6-22`, `src/firmware_update.c:1234-1311`, `src/sip_media/mqtt.c:1265-1354`) | Custom CODE. Nothing was installed nor is a cryptographic signature claimed |
| Prometheus | `/metrics` and `/healthz`, version, uptime, MQTT, calls, UART and RSSI (`src/sip_media/prometheus.c:126-242`, `src/sip_media/prometheus.c:261-286`, `docs/architecture.md:202-212`) | Custom CODE. Documented port 9617 |
| SIP | Inherited SIP daemon and flow (`README.md:40-48`, `docs/architecture.md:39-83`) | Custom CODE/DOCUMENTED. It does not prove stock SIP |

### 6.1 WebRTC precision

The blog says "two-way audio over WebRTC". The system description is reasonable if it includes go2rtc and the card, but it would be wrong to attribute an `RTCPeerConnection` or native WebRTC signalling to the daemon. The daemon announces and consumes an ONVIF track over RTSP. go2rtc transforms the browser session and the audio return. The card contains `RTCPeerConnection`, three transceivers and signalling through the Home Assistant proxy (`wibox-intercom-video-card.js:525-539`, `wibox-intercom-video-card.js:574-628`).

### 6.2 OTA and configuration that must not be simplified

The configuration packaged in the commit still uses `firmware_update_repo=segator/wibox-media` (`include/etc/sip_media.conf.default:74-75`, `src/sip_media/config.c:84-85`). `FORK.md:93-102` documents configuring the device with the fork repo. Therefore, the OTA code is confirmed, but it is not claimed that every cmos486 installation updates from cmos486 without reviewing its configuration.

MD5 checks integrity against an expected value. It is not an origin signature. The code also has TLS logic, but this supplement does not certify the complete security of the updater nor relate that design to CVE-2026-86585.

### 6.3 Licence and dependencies

The top-level file is GPL-3.0. In cmos486 and segator, `git ls-files '*LICENSE*' '*COPYING*' '*NOTICE*'` only returns `LICENSE`, without separate additional notices in the tree. `FORK.md:124-136` says that a complete build needs a private base image with proprietary parts of the Goke SDK and that not everything is in Git. The top-level licence does not by itself prove the rights nor the reproducibility of all blobs and dependencies. No legal or redistribution audit was made.

## 7. cmos486/wibox-intercom-video-card: UI, not daemon

**CODE:** it is a Lovelace card in JavaScript. It uses `RTCPeerConnection`, microphone with `getUserMedia`, `sendonly/recvonly` tracks, WebSocket `/api/webrtc/ws` signed by Home Assistant and configurable actions (`wibox-intercom-video-card.js:47`, `wibox-intercom-video-card.js:507-539`, `wibox-intercom-video-card.js:617-628`, `wibox-intercom-video-card.js:227-240`).

**DOCUMENTED:** it requires cmos486/wibox-media for the return, AlexxIT/WebRTC, go2rtc, HTTPS and Home Assistant 2024.4 or later (`README.md:89-97`, `hacs.json:1-6`). Its own guide says that stock does not accept that return (`README.md:15-16`, `README.md:247-251`).

It does not implement the RTSP, MQTT, SIP, OTA or metrics daemon. It does not raise `media.*` or actions in the Kotlin app. The file licence is Apache-2.0 (`LICENSE:1-2`, `README.md:299-301`).

## 8. segator/wibox-media and diff with cmos486

### 8.1 What segator adds

The README defines it as Wi-Box GK7102S custom firmware that replaces the cloud/app flow with a local SIP daemon (`README.md:1-5`). In code and documentation SIP PCMA, optional H.264 D1, snapshots, DTMF/MQTT, Home Assistant, Prometheus and OTA appear (`README.md:9-19`, `docs/architecture.md:39-54`).

GitHub marks `segator/wibox-media` as `fork=false`. Therefore, "duhow fork" describes technical provenance acknowledged in its credits, not a GitHub relationship that we should invent. In contrast, the API confirms `cmos486/wibox-media` as a segator fork, with `parent=segator/wibox-media`.

### 8.2 Measured divergence

segator/main was obtained inside the cmos486 clone and the following was computed:

| Measure | Result |
|---|---|
| Common ancestor | `a55b8de9b19b954238a3c160e283a2c1867e8ce6` |
| cmos486 / segator exclusive commits | 29 / 3 |
| cmos486 diff from the ancestor | 39 files, 2843 insertions, 227 deletions |
| segator diff from the ancestor | 16 files, 110 insertions, 10 deletions |
| Initial commit of the ONVIF track | `018ceb6 feat(rtsp): ONVIF audio backchannel track for go2rtc two-way audio` |
| Interoperability commit | `3539923 Fix RTSP interop and two-way audio for go2rtc/WebRTC clients` |

The three later segator commits are `eed32e1`, `8e2d085` and `a64530b`, about fail-closed hostname, release and documentation.

**Relevant static difference:** in cmos486, if `SSL_set1_host` fails, the updater logs the error and continues (`src/firmware_update.c:471-475`). In segator HEAD, the same failure releases resources and aborts (`src/firmware_update.c:471-478`, commit `eed32e1`). This proves a branch difference. It does not prove exploitation, it is not qualified as a CVE and it is not extrapolated to stock F03266.

## 9. ESP32: physical bridge, not cloud integration

Commit: `cb21e44ce0820975dfeff47a53ec915e0976099c`, GPL-3.0.

**CODE:** the sketch defines two relays on GPIO 4 and 5, an LED, an HTTP server on port 80 and a sequence of writes and waits (`porteroCasa/porteroCasa.ino:13-33`, `porteroCasa/porteroCasa.ino:79-95`). The README describes the idea of reproducing the camera and key presses (`README.md:5-10`, `README.md:16-36`). The Telegram bot calls the ESP32 endpoint (`telegramBot/espH0m3_bot.py:12-17`, `telegramBot/espH0m3_bot.py:26-36`).

`Wi-Box`, `QVCloud`, `DUOX`, `MQTT`, `SIP`, `RTSP` or `WebRTC` do not appear in the reviewed text files. The Fermax model and the firmware are not specified. The electrical schematic has not been validated. The action was not executed and it is not declared compatible with Wi-Box.

## 10. Recon and TROOPERS: DUOX PLUS/RFID, outside QVCloud

**Recon 2026:** the talk "From Bus Wires to Badges: Breaking Into FERMAX Through RFID", from 2026-06-20, deals with the DUOX PLUS proprietary system and panels with EM4100, MIFARE Classic and MIFARE Desfire modules. It summarizes previous two-wire research and extends it to RFID. Official source, talk summary.

**TROOPERS26:** "Reverse engineering the DUOX PLUS protocol" deals with the digital signal, identification and security of a non-polarized two-wire system. The official page includes slides and video, but this supplement only used the summary to classify the scope. The generic `troopers.de` cover adds no technical evidence.

No source mentions Wi-Box F03266, VDS, QVCloud, the Wibox app or a reusable API. A bus or RFID weakness cannot be transferred to stock QVCloud. Attack instructions are not reproduced either.

## 11. CVE-2026-86585: confirmation and exact scope

### 11.1 Confirmation

The CVE is **confirmed** by three convergent sources:

1. Fermax lists it under "VEO / VEO-XS monitors", with a score of 7.7 and versions `< 01.48.001` (`fermax.com/security-advisories`).
2. The official CVE record has assigner `FERMAX`, status `PUBLISHED`, CWE-347 and product "DUOX PLUS monitor firmware (VEO Wi-Fi range)".
3. The NVD API returns a published entry, with the same description and range. Its analysis status is `Deferred`, not a denial of the CVE.

CIRCL reproduces the record and adds correlations. It is used as an aggregator, not as a substitute for the manufacturer.

### 11.2 Dates, product, version and mitigation

| Field | Verified value |
|---|---|
| CNA reservation | 2026-09-08T07:00:51.689Z |
| CNA publication | 2026-09-16T10:13:22.416Z |
| NVD publication | 2026-09-16T11:16:44.070Z |
| CNA update | 2026-09-16T17:47:00.863Z |
| NVD last modification | 2026-09-18T19:44:10.957Z |
| Weakness | CWE-347, improper verification of cryptographic signature |
| Description | Update packages without adequate signature verification, if the attacker controls delivery |
| Structured product | DUOX PLUS monitor firmware, VEO Wi-Fi range |
| Affected versions | Earlier than `01.48.001` |
| CNA severity | CVSS 4.0, 7.7 High, adjacent vector |
| Mitigation derived from the advisory | Use `01.48.001` or later. The fixed range is derived from the Fermax advisory, not from our own test |
| Exploitation observed by this work | Not investigated nor tested |

The narrative text names VEO and VEO-XS. The CNA/NVD structured list also includes `VEO-XL-WIFI` and `VEO-XL-WIFI-DDA`, alongside VEO and VEO-XS/DDA. That difference between description and structured platforms is kept instead of hidden.

### 11.3 What is not affected by inference

Wi-Box F03266 does not appear in the advisory, the CNA record nor NVD. It is a VDS product different from the DUOX PLUS monitors. **The CVE is not applied to Wi-Box, QVCloud, duhow, cmos486, segator or the Kotlin app.** The presence of remote update in the official sheet does not prove the same mechanism nor the same weakness.

## 12. Official product F03266: what is published and what is absent

The official sheet identifies "DESVIO DE LLAMADA WIFI VDS WI-BOX", reference **3266**, EAN 8424299032665. The query shows **"Producto descatalogado. Disponible hasta agotar existencias"**.

**OFFICIAL:**

- VDS technology, 3+coax or 5 wires, not DUOX PLUS.
- It is installed in parallel to the VDS monitor or phone and acts as another terminal.
- Wi-Fi 2.4 GHz, 18 Vdc, two push buttons and terminals `+, L, -, V, M, Ct, F1`.
- Android/iOS app and pairing by QR code.
- Call reception with audio/video, concierge, F1 and opening.
- 30 s to accept and 90 s of conversation.
- Log, captures, settings and remote firmware update.

The page does not publish firmware name or version, SoC, QVCloud, hosts, OAuth, APIs, MQTT, SIP, RTSP, ONVIF, WebRTC, topics or command format. Those absences do not prove that the product internally lacks protocols. They only prevent attributing them to this official source.

## 13. Separation matrix of stock, custom, DUOX and models

| Object | Evidence and transport | Capabilities demonstrated within its profile | Cannot be inferred for our stock app |
|---|---|---|---|
| Wi-Box stock/QVCloud | APK and previous analyses, partial OAuth/Shadow and VDS sheet | Only the states already present in provider-matrix | No new capability from forks |
| duhow custom | HTTP CGI, local MQTT, UART, Dropbear, modified boot | Local actions/events of the patch | That stock exposes those topics, HTTP or SSH |
| segator custom | SIP daemon, native MQTT, RTSP, OTA, Prometheus | SIP/media/HA of its firmware | That stock has SIP, RTSP or local MQTT |
| cmos486 custom | segator base + ONVIF backchannel, VDS, health and fixes | RTSP/go2rtc/WebRTC return and other changes of the commit | Native stock WebRTC or parity with segator HEAD |
| cmos486 card | Lovelace JavaScript + HA + go2rtc | WebRTC UI and configured HA actions | Daemon, Kotlin provider or support without custom firmware |
| Historical WayFi | Hi3518EV200, WayFi app, narrated Sofia/ONVIF/RTSP | Historical research of another product | Hardware, endpoints or services of Wi-Box F03266 |
| Generic ESP32 | Relays, GPIO, HTTP, Telegram | Physical bridge for an unidentified door phone | Wi-Box/QVCloud/DUOX compatibility |
| DUOX PLUS Recon/TROOPERS | Two-wire digital bus and RFID | Research of that system | QVCloud, VDS or Wi-Box |
| VEO/VEO-XS monitors and CNA platforms | DUOX PLUS, firmware `< 01.48.001` | Official scope of CVE-2026-86585 | Applying the CVE to F03266 |
| Official F03266 | VDS product and mobile app, discontinued | Published commercial features | Unpublished protocols or fork support |

## 14. Future implications for providers

Everything below is a **design opportunity, not current availability**.

| Route | Opportunity | Requirements before claiming support | Current limit |
|---|---|---|---|
| duhow MQTT | Local adapter for a versioned duhow profile | Device with that firmware, specific version, broker, topics, payload, auth, TLS if the environment allows it, discovery and ack tests | Custom firmware required. Plain MQTT and no physical ack in the evidence. Do not mix with stock |
| cmos486/segator MQTT | Another local profile, with different entities and states | Choose fork and commit, map `media/state`, UART, snapshots and commands, negotiate media separately, test reconnection and duplicates | It does not automatically share the duhow contract nor the static QVCloud of the APK |
| Custom media | Evaluate RTSP/SIP and, only for cmos486, go2rtc/WebRTC | FOSS implementation, authentication, Android permissions, call cycle, latency, tests per model/firmware and authorized hardware | None of these routes is implemented in the app nor proves stock P2P |
| Self-hosted | Learn from the local HA pattern for an own versioned API | Accounts, enrolment, authorization, inventory, events, actions, ack, media, revocation, storage and security suite | HA/go2rtc is not a cloud API ready for our app. There is no self-hosted adapter |
| Binder/AIDL | Expose metadata, capabilities, events and actions of a real provider to authorized third parties | Implemented provider, per-device scopes, UID+signature, ack by levels, gaps, cancellation and tests with two signatures | Binder does not create compatibility. It does not carry media or secrets and it follows the separate design of `docs/external-api.md` |
| ESP32 | Possible independent physical profile, only if other hardware is defined and tested | Electrical model, isolation, states, physical ack, endpoint security and express authorization | No Wi-Box evidence and no test performed |
| DUOX/RFID | Separate research to understand other products | Product, protocol and authorization specific evidence | It adds no requirements nor compatibility to stock Wi-Box |

The discontinued sheet may increase the interest of maintainable local options, but it does not replace the QVCloud research nor the evidence needed for each capability.

## 15. Limits and work not done

- There is no runtime test of duhow, cmos486, segator, the card, go2rtc, SIP, RTSP, WebRTC, MQTT, OTA or Prometheus. "CODE" means static presence, not measured operation.
- The test benches narrated by the authors, their photographs, VDS coexistence logs, jitter figures or real snapshots were not validated.
- The original WayFi PCAPs were not analyzed because they were not provided. Archived outputs are not raised to our own capture.
- Recon/TROOPERS slides or videos were not analyzed. Only the scope published on their official pages is used.
- No exhaustive security audit of the forks, their blobs, third-party licences or updaters was made.
- It was not checked whether the downloadable releases match the commits byte by byte. The source commit is fixed and, separately, what each README says.
- NVD HTML did not give useful text with `webfetch`, although `curl` responded 200. The readable confirmation was done with the NVD API and the official CVE record.
- There are no inaccessible sources in the final `curl` pass: 25/25 returned HTTP 200.
- No compatibilities, providers, app/core code or files outside the ownership of this sheet were modified.

## 16. Verification

`analysis/research-supplement/verify_research.py` uses only the standard library. It checks the mandatory sources, commits/licences, classification, CVE scope, cross-references, baseline and absence of elevation. It includes isolated negative controls for stock/custom mixing, CVE improperly applied to F03266, claims without source and provider-matrix elevation.

Expected command:

```bash
python3 analysis/research-supplement/verify_research.py
```

Expected final output:

```text
WIBOX_RESEARCH_SUPPLEMENT_VERIFIED
```
