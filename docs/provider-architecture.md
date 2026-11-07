# Provider architecture for Wibox

## 1. Scope, priority and level of certainty

This document is an architecture proposal, not an implementation nor a compatibility certification. The priority is to understand and preserve the behaviour of QVCloud/Quvii as it appears in Wibox Fermax v2.2, versionCode 18. The UI can be modernized, but it must not delete features so that they fit in a common denominator with MQTT or with a self-hosted cloud.

Primary sources reviewed: `analysis/integration-contract.json`, `analysis/apk/inventory.json`, `analysis/apk/ui-inventory.json`, evidence E001-E075, the REA run described in `docs/rea-analysis.md`, `docs/protocol.md` and the current public API of `core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud`. The available evidence is static and has offline tests for part of the client. No hardware, real account or new traffic was used in this sheet.

Reading rule:

- **FACT**: it is observed in the APK, in a cited piece of evidence or in the current code.
- **PROPOSAL**: a design that still has to be implemented and tested.
- **UNKNOWN**: a contract, authorized run or hardware test is missing.
- A fake, an accepted HTTP subscription or an MQTT publish does not prove a physical action or compatibility with stock.
- Total compatibility or a global percentage is not claimed. Each capability has four independent gates: API/wire, behaviour, security and hardware.

The normative capability matrix is `analysis/architecture/provider-matrix.json`. It contains 64 capabilities and their current states. Alternative providers are future design, not existing implementations.

## 2. Diagnosis of current couplings

### 2.1 In the `core:cloud` API

| Observed coupling | Consequence | Proposed correction |
|---|---|---|
| `WiboxCloudConfig` contains `baseUrl`, `clientId`, `regionId`, `clientType`, `oemId`, `appId` and `clientFlag` | The public configuration has a QVCloud shape and does not represent other profiles | Separate legacy configuration from `ProviderConfig`, with a closed `ProviderProfile` and secrets outside the public configuration |
| `CloudCredentials` and `CloudSession` expose user, password, access token and refresh token | The session is not opaque to consumers and only covers an OAuth scheme | Hand an opaque `SessionHandle` to domain/UI. Tokens stay inside the adapter and its secret store |
| `TokenStore` has a single session with no namespace | It does not distinguish provider, account, environment or installation | Keep it for legacy and add `SecretStore` with a composite key and atomic writes |
| `CloudCapabilities` is a global `Set<CloudCapability>` | It expresses no negotiation, per-device scope, unknown support or evidence | Replace it in the new port with `CapabilitySnapshot` with support, scope, provenance, evidence and revalidation |
| `CloudDevice` only has `id` | It keeps no name, model, role, channels, permissions, presence or extensions | Introduce a provider-neutral `DeviceSnapshot` and per-device capabilities |
| `discoverServices(): Map<CloudService, String>` | If the UI consumed the result it would know concrete endpoints and services | Resolve services inside `stock-qvcloud`. The UI only receives preparation states and domain errors |
| `CloudEventType` only distinguishes motion, incoming call and answered call | Gaps, connection state, unknown events, deduplication and cursors are missing | Use `EventEnvelope` with identity, cursor, gap, provenance, raw time and preserved unknown payload |
| `CloudActionAcknowledgement(providerCode, data)` | It does not distinguish local acceptance, cloud acceptance, device ack and physical effect | Introduce `ActionOutcome` with correlation, ack level, ambiguity and later observation |
| There are no pairing, invitation, configuration, firmware, preview or call operations | The current interface does not cover a large part of the APK | Add optional subports without turning those features into mandatory empty methods |
| `RealWiboxCloud` implements internal Ktor isolation, single-flight refresh, a five-minute margin, strict TLS and no retry well | It is a useful base, but only for OAuth, Shadow and the offline parser | Reuse those components inside the stock adapter, without rewriting them or announcing more coverage than demonstrated |

### 2.2 In the Android app

| Observed coupling | Current evidence | Risk and correction |
|---|---|---|
| Repository pinned to the fake | `WiboxRepository` is built in `AppContainer.local()` with `DemoWiboxProvider`, and `WiboxCloudFactory.create` exists but has no caller | Stock cannot be selected without changing the container. Introduce an explicit profile in `AppContainer` and a repository factory per profile |
| Demo models in state and ViewModels | `AppUiState`, `AddDeviceViewModel` and `PreviewViewModel` consume `DemoSnapshot`, `DemoDevice`, `DemoSettings` and `DemoScenario` | The UI cannot distinguish real domain from fixture. Create neutral presentation models and keep `Demo*` only in the demo adapter |
| Direct access to the fake from the ViewModel | `AppViewModel` calls `repository.cloud.observeEvents()` | The event must come from the neutral repository, with error and gap modelled |
| Action success tied to the fake | Any ack ends in `LOCAL_ACTION_ACKNOWLEDGED`, the fake returns `local-demo-ack-no-physical-action` | The current text is honest for demo, but it is not valid for stock. The result must keep the ack level and never translate it generically into an open door |
| Pairing and preview simulated by timers | `startProvisioning()` and `load()` complete local states, `PLAYING_SIMULATED` is labelled | Keep those states only under a demo provider. In production, progress and playback must come from real events |
| Incomplete pairing data | `CloudDevice` only provides an ID and the ViewModel infers the model if the ID contains `9440` | The adapter must return typed metadata. The UI must not infer model, endpoint or capability from an identifier |
| Three ViewModels shared by the whole `NavHost` | `WiboxApp` creates `AppViewModel`, `AddDeviceViewModel` and `PreviewViewModel` for all routes | Long-lived state is mixed between flows. Separate session, list, pairing, device, media and settings with ViewModels per graph or screen |
| Artificial initial load | `LoadingScreen` waits 450 ms and navigates | In real it must represent restoration, preparation and session decision. In demo it can keep a brief identified cover |
| Demo persistence tolerant to corruption | `LocalDemoStore` turns a parse failure into `null` and the repository falls back to fixtures | Do not reuse that pattern for real data. A damaged snapshot must produce a recoverable error and a copy, not silent loss or replacement by demo |
| Secrets in form state | `authCode` and `wifiPassword` live in `AddDeviceUiState` | They are needed during pairing, but they must not be persisted, logged or appear in UI snapshots. Use bounded ephemeral state and clear it at the end |

The app does get two limits right that must be preserved: it does not import Ktor or internal DTOs and the demo mode is labelled. Modernization must not remove those protections.

### 2.3 Wiring status when closing the UI review

This was checked in code and this is what is missing, in order of dependency, for the app to stop running on the demo:

1. `AppContainer` builds `DemoWiboxProvider` with no alternative. `WiboxCloudFactory.create` returns a working `StockQvCloudProvider`, but nobody calls it.
2. `StockQvCloudProvider` deliberately cuts three operations with `ProviderError.Unsupported`: `listDevices`, `execute` and `observeEvents`. Even if it were wired today, the device list would come out empty and no action would reach the provider. `RealWiboxCloud` already implements `authenticate`, `listDevices` and `performAction`, so the work is bridging, not protocol.
3. `WiboxRepository` requires a complete `AppOperations` (invitations, settings, media, reset) that only the demo implements. The real profile needs degraded data, not empty methods.
4. There is no login screen or real credential store. The login contract is captured and documented in `docs/protocol.md` §14.

Pairing new devices is still blocked by the server (`docs/protocol.md` §16). That does not prevent seeing a device already bound to the account: login and `/device/status/multi` are enough.

## 3. Provider-neutral boundary without cutting QVCloud

### 3.1 Conceptual modules

| Piece | Responsibility | What it cannot contain |
|---|---|---|
| `core:provider-api` | Models, capabilities, opaque sessions, events, actions, errors and subports | URLs, topics, headers, Ktor, Paho, DTOs, Android media classes |
| `provider:stock-qvcloud` | Opaque registration, OAuth, regional discovery, user auth, Shadow, push, P2P, CGI and stock pairing | `Demo*` models, Compose rules or fallback to another provider |
| `provider:mqtt` future | Versioned MQTT profile for firmware or broker defined later | Invented QVCloud topics, media promises or reuse of the duhow MQTT as stock |
| `provider:self-hosted` future | API of a versioned self-hosted cloud, accounts, devices, events and actions | Impersonating QVCloud or parity claims without its own suite |
| `provider:demo` | Deterministic fixtures for development and tests | Selection in production, real credentials or compatibility evidence |
| `app` and `core:presentation` | Use cases, UI state, navigation and rendering by capabilities | Endpoints, topics, tokens, `quii` URLs, wire parsers or implicit transport choice |

The demo provider is needed for tests, but it is not one of the three deployment providers described in the contract. It must live in a clearly separated source set or module and carry `connectionKind = DEMO` in all its snapshots.

### 3.2 Main port and subports

`WiboxProvider` is the new entry point. Its mandatory contract is limited to identity, capabilities, lifecycle, session, errors and close. The rest is obtained through optional subports. A missing function returns `Unsupported` or `Unknown`, never an empty result that looks like success.

| Subport | Domain operations | Preserves in stock |
|---|---|---|
| `SessionPort` | Prepare, restore, authenticate with ephemeral material, refresh if applicable, close the local session, revoke if proven | Opaque registration, later login, OAuth password/refresh and dynamic credentials without inventing a visible login |
| `DevicePort` | Observe and refresh devices, read detail, presence and Shadow, query specific fields | Local list synchronized with `getDevList`, dynamic credentials, unknown states and four Shadow operations |
| `DiscoveryPort` | Discover LAN candidates or prepare internal regional discovery | `QvLocationManager`/P2P for services and LAN search for pairing, without exposing endpoints |
| `PairingPort` | Parse the QR code, provision over AP/audio/LAN/manual, observe progress, cancel, bind | QR fields, 150 s, polling, retries limited per stage and errors without physically repeating blindly |
| `EventPort` | History, pages, photos, deletion, live events, push and gaps | Alarms 2/19/20, pages of 15, FCM, MQTT shadow disabled by default and associated photo |
| `ActionPort` | Execute unlock, F1, guard and call actions with correlation | Permissions, optional local biometrics, timeout, ack and ambiguous result |
| `MediaCallPort` | Open/close preview, panels, channel, quality, listen, talk, capture, recording, accept/reject call | Real P2P, one/four panels, modes, 90 s, reconnection and incoming call without replacing it with a simulated panel |
| `InvitationPort` | List, create, edit, revoke and accept invitations | QR/link, expiry, six permissions and the limit of 100 |
| `DeviceConfigPort` | Rename, delete, passwords, alarms, WiFi, time, F1, FPS, intercom mode and opening protection | owner/shared differences and specific configuration that a minimal port would remove |
| `MaintenancePort` | Query firmware, apply update, query app update and allowed diagnostics | Progress and failures without turning a query into an executed update |

Subports can have partial implementations. For example, `stock-qvcloud` can offer `DevicePort` with Shadow and still not offer a device list. The individual capability, not the presence of the interface, decides whether an operation is available.

## 4. Negotiated capabilities

### 4.1 Two axes that must not be mixed

**Design support**:

- `required`: mandatory for the common contract or for the declared stock parity.
- `optional`: the provider can offer it and the UI adapts.
- `unsupported`: it is known that this provider does not offer it. It must be shown as unavailable, not as a network error.
- `unknown`: it has not been negotiated yet or there is no evidence. It must be shown as unchecked and allows revalidation.

**Implementation evidence**:

- `implemented_contract`: there is an implementation and an offline contract test.
- `implemented_offline`: there is local logic or a parser with a test, without a real call.
- `local_only`: demo or local storage only, without provider compatibility.
- `static_evidence_only`: the APK proves a construct or flow, but the clone does not implement it.
- `unsupported`: the real client explicitly returns it as unsupported.
- `unknown`: there is not enough basis either to implement or to deny the feature.
- `design_required`: it is an obligation of the future architecture and is not finished yet.
- `not_implemented`: the alternative provider is only future design.

### 4.2 Content of `CapabilitySnapshot`

Each entry must include:

1. Stable and versioned `CapabilityKey`, with namespaced extensions when no common equivalent exists.
2. Support `required`, `optional`, `unsupported` or `unknown` and current evidence kept separate.
3. Scope `provider`, `account`, `device`, `channel` or `session`.
4. `DeviceRef` and channel when applicable. A capability of one lock is not inherited by all devices.
5. Origin: local declaration, account negotiation, Shadow, invitation permission, runtime observation or contract test.
6. `evidenceLevel`, profile version and revalidation moment. A static datum does not become runtime just by the passage of time.
7. `limitations`: absence of physical ack, lack of media, denied permission or unknown semantics.
8. Typed domain extensions. A generic DTO is not carried to the UI.

Negotiation is performed when preparing a session, when refreshing devices, when permissions change, after certain protocol errors and when reconnecting. `unknown` is not stored as `unsupported`. The UI can disable an action while it is being checked, but it must distinguish both texts.

## 5. Port models and semantics

### 5.1 Opaque session and credentials

`SessionHandle` contains provider, installation, account alias, environment, state and an opaque identifier. It contains no tokens, password, `dataEncodeKey`, Verify Code, URLs with userinfo or device secrets.

`CredentialMaterial` is ephemeral, single-purpose and never part of `StateFlow`, `SavedState`, logs, analytics, fixtures or snapshots. The current password authentication stays inside stock. The new store uses the key `(installationId, providerId, accountId, environment)` and separates:

- OAuth and account secrets.
- Dynamic device credentials and media keys.
- WiFi only if the user explicitly chooses to store it.
- Non-secret configuration and cache, in another store.

The write is atomic and encrypted with a key protected by Android Keystore. Deleting the local session does not imply remote revocation. `logout` remains unknown until the revocation contract and the effects on the opaque account are known.

### 5.2 Devices

`DeviceSnapshot` includes `DeviceRef`, name, declared model, owner/shared role, permissions, channels, presence `online/offline/unknown`, last observation, known firmware, capabilities and non-secret metadata. The model is not inferred from the ID.

The list must keep the `cache`, `cloud`, `local` or `merged` origin, and the freshness of each source. A device missing from Shadow is not marked offline. A `Device_state` integer is kept untranslated until its semantics are proven.

### 5.3 Shadow

`ShadowSnapshot` keeps requested fields, returned fields, original integers, `path`, timestamp, sequence if present, freshness and unknown fields. `get`, `multi`, `subscribe` and `unsubscribe` are different capabilities:

- `get` and `multi` prove an HTTP query, not real-time presence.
- The `subscribe` ack does not prove MQTT opening or later delivery.
- `unsubscribe` with `ACCOUNT_LOGOUT` or `DEVICE_UNBIND` is not the same as revoking OAuth or physically unbinding.

Unknown fields are kept in a safe domain envelope, not as a network DTO. Known extensions receive typed names and unknown ones are not discarded.

### 5.4 Events and real time

`EventEnvelope` contains provider ID, type, device, channel, raw and optional parsed time, local reception, attachments by reference, unknown fields, cursor, FCM/MQTT/HTTP/local provenance and duplicate flag.

The stream also emits `Connected`, `Reconnecting`, `ResyncRequired` and `Gap`. There must be deduplication by `(provider, account, eventId)` and a persisted cursor policy. QoS 1 can duplicate and retained can be old. Neither guarantees exactly once nor total order.

The offline FCM parser is a capability separate from token registration, background reception, history and live events. `AlarmTime` remains raw until format and time zone are fixed.

### 5.5 Pairing and provisioning

`PairingRequest` identifies the QR, AP, audio, LAN, manual or invitation method, non-secret data and ephemeral material. The device QR code keeps the four observed fields `apName`, `uid`, `authCode` and `model`, without replacing `authCode` with a UI credential. `PairingProgress` uses stages `connecting`, `sending`, `waiting`, `binding`, `ready`, `failed`, `cancelled` and `permissionRequired`, with manual retry bounded per stage.

Cancellation stops adapter jobs and closes resources, but it does not claim to undo a write already sent. The adapter reports `outcomeUnknown` when the device could have received the configuration. Bind retry is automated only for the proven codes and modes. The generic HTTP retry is never reused for physical provisioning.

### 5.6 Actions and acknowledgements

`ActionCommand` includes client ID, device, channel, type, parameters and retry policy. The ID is used for correlation and deduplication only if the provider allows it, it is not presented as guaranteed idempotency.

Levels of `ActionOutcome`:

| Level | Allowed meaning | What it does not allow claiming |
|---|---|---|
| `LOCAL_ACCEPTED` | The app or fake accepted the request | That it left the phone or reached the device |
| `PROVIDER_ACCEPTED` | The cloud accepted the command | That the device executed it |
| `DEVICE_ACKNOWLEDGED` | The device confirmed reception or execution per contract | That the door physically opened, unless semantics are explicit |
| `PHYSICAL_OBSERVED` | An independent and validated event confirms the effect | Other actions or states not observed |
| `REJECTED` | There is a typed rejection | An unreported physical cause |
| `UNKNOWN_OUTCOME` | Timeout, disconnection or ambiguous response | Definite success or failure |

The UI only says "door open" with `PHYSICAL_OBSERVED` and verified semantics. An opening ack can show "opening command confirmed by device" if that is its proven meaning. Ambiguous actions are not repeated automatically. Refreshing state or checking the device before a new manual order is offered.

### 5.7 Call, preview and P2P

`MediaSessionHandle` is opaque and its cycle includes `preparing`, `connecting`, `playing`, `buffering`, `stopped`, `offline`, `unsupported`, `failed` and `closed`. Controls are negotiated per device and channel: one/four panels, quality, listen, talk, capture and recording.

The port does not expose `quii://`, mapped ports, passwords, JNI or `dataEncodeKey`. The media adapter manages tunnels and secrets. The UI consumes states and frames through an Android render abstraction, not a cloud DTO.

Accepting a call and opening preview are related but different operations. Rejecting, auto-reject at 30 s and event 20 of a call answered on another terminal need call correlation. A local preview never counts as an answered stock call.

### 5.8 Invitations

The model keeps QR code, link, expiry, owner, device, the `preview`, `playback`, `alarm`, `unlock`, `F1` and `callGuard` permissions, plus the limit of 100 when applicable. The link contract carries `InviteCode`, `OwnerId`, `OemId`, `DeviceId`, `From` and `AppId`, and accepts `AppVersion` when it comes. The stock app prints the link without `AppVersion` and with `From=QrInvite`, so that field is optional and its absence does not invalidate the invitation. Creating, editing, revoking, accepting and accepting again are separate operations.

OEM compatibility is not solved by copying the permissive comparison of the APK. An exact rule is fixed after testing with valid, invalid, used, expired and other-OEM invitations. Links and codes are treated as secrets and are not logged.

### 5.8.1 Accounts and members: what the provider does not offer

The captured provider routes are `/auth/user`, `/auth/user/deli`, `/auth/nologin` in duplex mode, `/device/status`, `/device/status/multi`, `/device/status/sub`, `/device/status/unsub` and `/app/version_check.jsp`. None manages accounts, members, roles or user lists of an account. The only thing that touches the account is login and the deletion of the session itself.

In the stock APK, the screens that look like user screens (`activity_share_manage.xml`, titled "Users", and `activity_share_device.xml`, titled "New user") are actually the list of people a device is shared with and the screen that generates the invitation: 120 dp QR code, link, expiry of the "available before" kind, copy and the system share sheet. There is no account creation, no roles other than owner and guest, and no invitations that exist without a device behind them.

That list, however, is a real channel and it is not local: `get-share-group-list` reads it, `generate-nologin-share-invitation` creates one, `update-share-group-config` edits its name and permissions and `delete-share-group` revokes it, all of them on the authenticated duplex endpoint `/auth/user;jus_duplex=up` with the XStream envelope of section 17 of `protocol.md`. `core/cloud` exposes it as the `CloudUserAdministration` port and `UserAuthUserAdministration` is the real implementation; the app uses it for list, create, edit and revoke, and keeps the local catalogue only when the install has no account yet. The commands are owner operations: a guest account sees the device as `from-share=1` and the gateway rejects the read with `100101129` and the edit with `100101126`, which the app surfaces as an authorization error. The contract comes from the decoded APK and has not been captured on the wire yet, because the only account available is a guest one.

### 5.9 Errors and retries

`ProviderError` keeps category, operation, capability, `retryPolicy`, `outcome`, sanitized `providerCode` and `causeSafe`. It does not copy bodies, full URLs or tokens.

Minimum categories: authentication, authorization, platform permission, not found, conflict, limit, network, timeout, parsing, persistence, invalid input, unsupported, unknown, closed session, event gap and unknown physical result.

Policies:

- `SAFE_READ_RETRY`: idempotent reads only, with bounded backoff and jitter.
- `MANUAL_RETRY`: requires a user decision and a new correlation.
- `DO_NOT_RETRY`: invalid input, unsupported or ambiguous physical action.
- `REAUTHENTICATE`: credentials or session no longer valid, without retrying a hidden password.
- `RENEGOTIATE`: capability or regional endpoint changed.
- `RESYNC`: there is a gap or invalid cursor, without pretending continuity.

## 6. Real discovery without leaking endpoints or DTOs

### 6.1 Regional services

`stock-qvcloud` encapsulates bootstrap, `QvLocationManager`, P2P, region, cache and service selection. Its output towards domain is `PreparationState`, not `Map<CloudService, String>`:

1. `idle`: there is not enough configuration yet.
2. `discovering`: resolving services without showing hosts.
3. `ready`: there is a session and usable capabilities.
4. `degraded`: some capability failed, with per-capability detail.
5. `authenticationRequired`: opaque material or a new account is needed.
6. `failed`: typed error and retry policy.

The `fermax.qvcloud.net` bootstrap is static evidence, not a universal fallback. Historical duhow IPs are not used and the update host is not fixed as an OAuth endpoint. The UI never picks a region by URL nor writes paths.

### 6.2 LAN devices

`DiscoveryPort` returns `DiscoveredDevice` with a stable ID if available, name, declared model, announced capabilities, LAN origin and temporal validity. It returns no user, password, CGI URL or raw packets. Selection moves to the pairing flow by candidate ID.

Real discovery needs complete datagrams/callbacks, Android permissions, multicast behaviour, duplicates and timeout. Meanwhile `devices.local_discovery` remains without demonstrated compatibility. The simulated listing is not presented as a real scan.

## 7. Adapters

### 7.1 `stock-qvcloud`, priority

It must progressively cover everything known from the APK, not only OAuth and Shadow.

| Internal component | Responsibility | Activation gate |
|---|---|---|
| Opaque session and OAuth | Registration, login, password/refresh, restoration and single-flight | Request/response contract, errors and authorized run |
| Service discovery | Region and OAuth, Shadow, user, alarm and MQTT URLs | Dynamic scheme and correlation with real services |
| User/device API | List, dynamic credentials, bind and invitations | Full XML/JSON, pagination, duplicates and permissions |
| Shadow | Four current routes and unknown fields | Keep golden tests and test semantics per version |
| Push/events | FCM registration, reception, history, realtime and resync | Runtime evidence, background, order, gaps and deduplication |
| Pairing | QR, AP, audio, LAN, manual and recovery | Permissions, sequences and testing with authorized hardware |
| Media/actions | P2P, CGI, calls, unlock, F1, guard and ack | Verified FOSS alternative and device tests, without copying proprietary binaries |

If an authorized run proves that the MQTT shadow is active, the stock adapter must keep its `tdk/client_api/users/get/`, `tdk/client_api/sys/get/` and `tdk/client_api/sys/update/` topics, QoS 1, 300-second heartbeat, access token as password and bounded reconnection wait of 0 to 10 seconds according to E037-E041. Today it is disabled by default and its runtime is not demonstrated. That eventual internal stock channel is not the future `mqtt` provider.

The reference media transport is native P2P with mapped ports. It is not replaced by SIP, WebSocket or WebRTC by analogy: `libWebrtcAudio.so` does not prove `PeerConnection`. A FOSS implementation must prove interoperability with that contract or declare itself a different profile without certified stock compatibility.

The weaknesses E002-E003, E027-E030, E049 and E072 are not copied: BODY logging, secrets in logs, trust-all, disabled hostname verification or embedded keys. Wire compatibility does not require reproducing those insecure decisions. MD5 digests or legacy schemes are only studied if indispensable and remain confined, documented and tested. TLS is never relaxed so that a test passes.

### 7.2 `mqtt`, future and not implemented

Its goal is to offer another provider after defining a concrete profile. It is not implemented in this phase.

Mandatory prior contract:

- Profile and firmware/broker version, topic namespace, payload and capability schema.
- Bootstrap, TLS, authentication, per-device authorization and rotation.
- QoS, retained, LWT, heartbeat, reconnection, deduplication, replay and resync.
- Separate topics for command, device ack and physical observation.
- Correlation, real idempotency if it exists, command expiry and retry policy.
- Device discovery and per-device negotiation.
- Media outside MQTT unless there is evidence of another channel, MQTT alone does not provide P2P preview.

The APK's MQTT uses OAuth and is disabled by default. The plain MQTT of duhow and that of cmos486 belong to different firmware. None is mixed with stock or with each other. The matrix marks the whole adapter as `not_implemented`.

### 7.3 `self-hosted`, future and not implemented

It requires its own versioned API and an explicit trust model. It must define accounts, enrolment, inventory, authorization, invitations, events, actions, ack, storage, recovery and media. It can use HTTPS and a realtime channel, but the choice is not made nor considered implemented.

The server is not a transparent proxy that automatically turns QVCloud into compatible. If it acts as a stock relay, its limits and credentials are documented as a different profile and per-capability stock evidence is still required. Multi-user isolation, revocation and absence of secrets in logs are prerequisites, not later improvements.

## 8. Lifecycle, threading, Flow, cancellation and persistence

### 8.1 Common lifecycle

States: `closed`, `idle`, `preparing`, `ready`, `degraded`, `authenticationRequired` and `closing`. The provider is single-owner per session, `close()` is idempotent and does not delete secrets. After closing, any operation returns `Closed` except immutable descriptor queries.

Preparing a session does not imply connecting media. Refreshing OAuth does not imply reconnecting P2P. Subscribing to Shadow does not imply receiving events. These resources have separate cycles to avoid false aggregate successes.

### 8.2 Threading and Flow

- Public functions are main-safe and I/O happens on internal injectable dispatchers.
- No global `CoroutineScope` without an owner is used. Each adapter has a supervised scope, closed by `close()`.
- `observe*` uses `callbackFlow` or equivalent with `awaitClose`, listener unregistration and connection close.
- Cancelling the collector cancels subscription and child jobs. `CancellationException` is not ignored nor turned into `Network`.
- Buffers are bounded and their policy explicit. Call events are not silently discarded. Overflow produces `Gap` and resync.
- Session refresh is single-flight. Pairing, call and actions have a mutex or actor per device when concurrency could duplicate effects.
- The UI uses `repeatOnLifecycle` and cancels on exit. The repository keeps no orphaned collections per screen.

### 8.3 Persistence

Secrets, catalog, events, cursors and preferences are separated:

- Encrypted and atomic secrets, never in a common JSON DataStore nor in fixtures.
- Catalog versioned per provider/account, with date and origin. Obsolete data is labelled.
- Persistent cursor and deduplication to resume events. An invalid cursor forces a visible resync.
- Action journal with ID, ack level and unknown result. It serves to reconcile, not to retry automatically.
- Migrations with backup and visible error. A damaged real snapshot is never replaced by demo data.
- Gallery and local files use restricted URIs. The broad FileProvider paths of the APK are not copied without proven need.

## 9. Compatibility per capability

### 9.1 How a capability is claimed

To declare stock compatibility of a capability, four dimensions must be met, except `hardware = not applicable` in purely local logic that is duly justified:

1. **API/wire**: request, response, headers, errors and edge cases checked against evidence and golden tests. A static route alone is not enough for runtime.
2. **Behaviour**: sequence, permissions, states, duplicates, reconnection, cancellation and ack compared in an authorized run.
3. **Security**: strict TLS, protected secrets, authorization and logs reviewed. Trust-all is not copied nor are credentials leaked to imitate the original.
4. **Hardware**: authorized test with the target device and firmware when there is pairing, P2P, call, action, notification or physical effect. Model, firmware, result and limitations are recorded.

The word `implemented_contract` only indicates an offline contract. `local_only` never adds stock compatibility. The details and complete criteria are in the JSON matrix. The table summarizes the 64 capabilities without hiding the undemonstrated ones.

| Capability | Current state | Evidence needed and criterion to claim it |
|---|---|---|
| `core.lifecycle` | `implemented_contract` | Common tests of close, cancellation and resources. Hardware not applicable to pure lifecycle |
| `core.capability_negotiation` | `local_only` | Real negotiation per account/device and required/optional/unsupported/unknown tests |
| `auth.opaque_registration` | `static_evidence_only` | Registration and full response, later login, persistence and authorized run |
| `auth.user_login` | `static_evidence_only` | Request/response, errors, reuse and observed revocation |
| `auth.oauth_password` | `implemented_contract` | Authorized runtime, rotation and real errors, keeping TLS and redaction |
| `auth.oauth_refresh` | `implemented_contract` | Real concurrent refresh, expiry and revocation. Hardware not applicable when there is no device |
| `auth.session_restore` | `implemented_contract` | Encrypted restoration in the real app, migration and deletion. Hardware not applicable |
| `auth.logout` | `unknown` | Revocation contract and effects on account, tokens and devices |
| `discovery.regional_services` | `unsupported` | Regional response and service association at runtime, without invented fallback |
| `devices.list` | `unsupported` | Complete `getDevList`, synchronization, permissions and authorized account |
| `devices.dynamic_credentials` | `static_evidence_only` | Renewal, expiry, isolation and real use of credentials |
| `devices.local_discovery` | `unsupported` | Datagrams/callbacks, permissions, duplicates and a real LAN device |
| `devices.online_status` | `static_evidence_only` | P2P/Shadow semantics, freshness and transitions with a device |
| `pairing.qr` | `local_only` | Camera and image, valid/invalid formats and an authorized physical QR code |
| `pairing.ap_wifi` | `local_only` | AP sequence, security, timeouts and pairing with hardware, without new default credentials |
| `pairing.audio` | `local_only` | Acoustic emission, permissions, volume, timeout and real pairing |
| `pairing.bind` | `local_only` | Bind, retry codes, duplicates and result with device/account |
| `invitations.accept` | `local_only` | Valid, used, expired invitation, re-acceptance and real permissions |
| `invitations.manage` | `local_only` | List, edit, delete, limit and authorization with a real account |
| `invitations.issue` | `local_only` | QR/link, expiry and real cross-scan without leaking secrets |
| `invitations.permissions` | `local_only` | The six permissions and their enforcement in each operation and role |
| `shadow.get` | `implemented_contract` | Real response, unknown fields and semantics per device/firmware |
| `shadow.multi` | `implemented_contract` | Batches, absences, duplicates and real response |
| `shadow.subscribe` | `implemented_contract` | Real ack and separate evidence of what happens next |
| `shadow.unsubscribe` | `implemented_contract` | Real reasons and delimited effects, without calling it logout/physical unbind |
| `events.alarm_parser` | `implemented_offline` | Real sanitized fixtures and date formats. Hardware not applicable to the pure parser |
| `events.push_registration` | `static_evidence_only` | FCM registration/rotation, HMAC and authorized backend without leaking tokens |
| `events.realtime` | `unsupported` | Active channel, QoS, order, duplicates, gaps, replay and resync |
| `events.history` | `local_only` | Alarm API, pages of 15, errors and filters with real data |
| `events.photo` | `local_only` | Real attachment, expiry, permissions and event-photo association |
| `events.delete` | `local_only` | Individual/total deletion, authorization and real synchronization |
| `events.filters` | `static_evidence_only` | All/RINGS/MOTION filters, including their visibility condition |
| `media.preview_single` | `unsupported` | P2P tunnel, render, credentials, states and hardware. Without proprietary binaries |
| `media.preview_multi` | `unsupported` | Four panels, focus, channels and performance with a device |
| `media.stream_controls` | `unsupported` | Real quality, channel, timeout and reconnection |
| `media.listen` | `unsupported` | Incoming audio, permissions, latency and close with hardware |
| `media.talk` | `unsupported` | Two-way audio, microphone, half/full duplex and close |
| `media.snapshot` | `unsupported` | Real frame, storage, permissions and verifiable file |
| `media.record` | `unsupported` | Real video, duration, interruption, file and gallery |
| `media.local_gallery` | `local_only` | Real photos/videos, pager, controls and grouping with test files |
| `media.local_share_save` | `local_only` | Real export/share by URI and minimum FileProvider scope |
| `actions.unlock` | `unsupported` | Command, ack, timeout, permissions and physical effect with authorized hardware |
| `actions.f1` | `unsupported` | Pulse, duration, ack and real relay without ambiguous retries |
| `actions.call_guard` | `unsupported` | Call to the concierge and real state |
| `actions.call_accept` | `unsupported` | Acceptance, correlation and opening of real media |
| `actions.call_reject` | `unsupported` | Rejection and real local/remote close |
| `calls.lifecycle` | `local_only` | Auto-reject 30 s, event 20, background and real duplicates |
| `config.rename` | `local_only` | Local/remote persistence, duplicates and real synchronization |
| `config.passwords` | `local_only` | Device/admin/unlock change, rotation and secure recovery |
| `config.unlock_security` | `local_only` | Quick unlock and real local biometrics, without turning it into an opening ack |
| `config.alarms` | `local_only` | Call/motion, owner/shared and cloud/device persistence |
| `config.wifi` | `local_only` | WiFi change and real reconnection without exposing the password |
| `config.time_sync` | `local_only` | Command and real time state with the device |
| `config.playback_intercom` | `local_only` | Effective FPS, quality, audio mode, F1 and lock number |
| `config.delete_device` | `local_only` | Unbind, local deletion, invitations and real effects |
| `firmware.check` | `local_only` | Real query, version and availability without installing anything |
| `firmware.apply` | `unsupported` | Authentic package, progress, failure and recovery with expressly authorized hardware |
| `app.update_check` | `static_evidence_only` | Real updater response with strict TLS and no automatic install |
| `app.privacy_terms` | `local_only` | Reviewed local content, language and accessibility. Hardware not applicable |
| `app.deep_link` | `static_evidence_only` | Real intent, parameters, navigation and absence of sensitive data |
| `app.diagnostics` | `local_only` | Bounded diagnostics and sanitized logs, without token or hidden server change |
| `app.sound_vibration` | `local_only` | Preferences and real call on an Android device, separate from the Wibox hardware |
| `security.persistence` | `design_required` | Keystore, atomicity, migration, backup and no-leak tests in the real app |
| `events.notifications` | `static_evidence_only` | FCM background/foreground, full-screen intent, permissions and real call |

## 10. How to avoid false successes and feature loss

1. **Explicit provider**: the composition selects `stock-qvcloud`, `mqtt`, `self-hosted` or demo. It never falls back to demo because stock fails.
2. **Results per capability**: a correct OAuth session does not mark list, events, media or actions as available.
3. **Visible Unsupported and unknown**: unknown features are not systematically hidden. It is explained whether negotiation, permission or implementation is missing.
4. **No aggregate booleans**: there is no single `connected = true` for cloud, P2P, push and media. Each subsystem has its own state.
5. **No empty lists as success**: a `getDevList` failure is not turned into zero devices. A failed parser does not generate an empty event.
6. **No fictitious ack**: `code=0`, HTTP 2xx, publish or subscription ack keep their real level. The fake always includes demo origin.
7. **No ambiguous physical retry**: timeout or disconnection produces `UNKNOWN_OUTCOME`, reconciliation and a manual option, not automatic repetition.
8. **No extension loss**: unknown fields are preserved and each stock feature has a capability in the matrix, even if it has no equivalent in other providers.
9. **No UI inferences**: the UI does not deduce URLs, model, capabilities or permissions from IDs, topic names or DTOs.
10. **No profile mixing**: QVCloud, duhow and cmos486 have separate fixtures, credentials, topics and evidence.

## 11. Incremental migration and compatibility

### 11.1 Binary and source compatibility

- `WiboxCloud`, `WiboxCloudFactory`, `TokenStore` and the current models remain during migration. Abstract methods are not added to `WiboxCloud`, because that would break implementers such as `FakeWiboxCloud`.
- `StockQvCloudProvider` wraps the current client and translates to `WiboxProvider`. The temporary inverse adapter can still offer `WiboxCloud` to old consumers only for the operations already supported.
- New capabilities use `CapabilityKey`, not new branches of the legacy enum that could break exhaustive `when`. `ActionOutcome` does not replace `CloudActionAcknowledgement` fields.
- Deprecations start with a warning, never with deletion. Binary removal requires a major version and an explicit review of consumers.
- Internal DTOs are not made public to ease migration. Extensions are added as versioned domain types.

### 11.2 Phases and dependencies

| Phase | Work | Depends on | Verifiable output |
|---|---|---|---|
| F0 | Freeze this matrix, evidence and criteria | Inventories and current contract | 64 capabilities and 38 screens with explicit state |
| F1 | Add the new port, demo adapter and legacy wrapper without changing behaviour | F0 | Common contracts and app building with explicit selection |
| F2 | Migrate repository and ViewModels to neutral models, modernize UI by graphs | F1 | Same 33 non-legacy screens, states and tests without `Demo*` outside the demo adapter |
| F3 | Complete discovery, registration, login, list and stock credentials | F1 and authorized dynamic evidence | Test account and runtime contracts per capability |
| F4 | Complete pairing, invitations, history, push and stock configuration | F3 and permissions/hardware per capability | Specific suites and functional review point by point |
| F5 | Implement P2P/media and physical actions with correct ack | F3, FOSS alternative and authorized hardware | Evidence per device/firmware, without false ack |
| F6 | Design and implement MQTT only after fixing a profile | Stable F2 port and complete MQTT profile | Independent MQTT suite, without taking priority from stock |
| F7 | Design and implement self-hosted | Stable F2 port and own versioned API | Self-hosted suite and multi-user security |
| F8 | Final review and packaging | Implemented capabilities that enter the release | Functional checklist, compatibility, security, errors, navigation, tests, APK and installation on the emulator |

The UI can advance in F2 without waiting for real media, as long as it shows unsupported/unknown and does not turn the demo into evidence. MQTT and self-hosted do not block stock fixes, but they are not announced before F6/F7 either.

### 11.3 Common contract tests

Every implemented provider must pass:

1. Descriptor, capabilities and distinction of required/optional/unsupported/unknown.
2. Restoration, expiry, idempotent close and operation after close.
3. Cancellation of each Flow, unregistration and absence of orphaned coroutines.
4. Devices with missing data, duplicates, unknown fields and per-device capabilities.
5. Duplicate, out-of-order events, gaps, resync and corrupt cursor.
6. Network, timeout, authentication, authorization, parsing and persistence errors without secrets.
7. Rejected actions, timeout and `UNKNOWN_OUTCOME`, checking that there is no physical retry.
8. Ack levels and texts that do not turn acceptance into a physical effect.
9. Persistence, migration, per-account isolation and log redaction.
10. Provider selection and proof that a real failure never activates demo.

### 11.4 Specific suites

- **Stock**: current OAuth/Shadow goldens, user/alarm XML/JSON fixtures, regional discovery, opaque registration, dynamic credentials, AP/audio/LAN pairing, FCM, P2P and CGI. Each new runtime operation requires authorized evidence.
- **Future MQTT**: embedded broker or test container without real credentials, TLS, QoS, retained, LWT, duplicates, replay, unknown topics and correlation.
- **Future self-hosted**: simulated server with API version, migrations, authorization, multi-user, revocation, rate limit and recovery.
- **UI**: ViewModels, navigation, Compose, accessibility, sizes and real captures described in `docs/ui-modernization.md`.
- **Hardware**: protocol per model/firmware, result and limits. It does not run as a side effect of unit tests.

## 12. Final review and APK

The final review must walk the matrix capability by capability and the UI screen by screen. For each point it will record: implemented or not, API/wire evidence, behaviour, security, hardware, linked test and remaining limitation. Undemonstrated capabilities are presented as such in the release.

The later packaging must produce a real APK, not a screenshot or a report that replaces it. The minimum gate will be build and tests, SHA-256 hash of the APK, installation on the emulator with an explicit serial and a smoke walkthrough. The existence of the APK does not change the compatibility state of any capability. This sheet only produces architecture and criteria, it does not compile or certify that APK.
