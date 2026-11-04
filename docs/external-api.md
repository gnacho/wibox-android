# Local Binder/AIDL API for third parties

## 1. Status and feasibility

It is feasible to expose part of Wibox to other Android apps through a local Binder/AIDL service with explicit binding. It is the preferred route over opening HTTP on loopback or on the network, because Android provides the real caller UID and allows applying authorization on every transaction.

This document is a design ready for a later implementation phase. The service, the allowlist, the AIDL files, the SDK and its tests have not been implemented nor run. No app can consume this API today. The presence of methods in the contract does not change the QVCloud compatibility state.

The API will rely on the future `WiboxProvider` and its optional subports. It will not connect directly to Ktor, network DTOs, `FakeWiboxCloud` or the demo fixtures. `WiboxCloud` and `RealWiboxCloud` can remain encapsulated inside the stock adapter during migration, but they will not be the public boundary for third parties.

Goals:

1. Allow signed integrations different from Wibox without weakening security.
2. Deny by default and require approval and revocation visible to the user.
3. Expose state and capabilities without carrying secrets nor promising physical effects.
4. Keep QVCloud-specific extensions and those of future providers.
5. Offer a main-safe Kotlin SDK with coroutines, Flow and explicit compatibility.

## 2. Checked basis and limits

Local sources reviewed:

- `docs/provider-architecture.md`, proposed architecture and matrix of 64 capabilities.
- `analysis/architecture/provider-matrix.json`, normative source of support and evidence.
- `core/cloud/src/main/kotlin/dev/nacho/wibox/core/cloud/WiboxCloud.kt`, current legacy surface.
- `docs/protocol.md`, offline contracts and `Unsupported` operations.
- `docs/dynamic-analysis.md` and `analysis/dynamic/inventory.json`, limits of the controlled run.
- `app/src/main/AndroidManifest.xml`, which currently declares no external service.

Relevant facts:

- OAuth password, OAuth refresh, restoration and the four Shadow operations have an offline implemented contract. They are not a complete runtime validation.
- `devices.list`, `events.realtime`, physical actions and P2P media are `unsupported` for stock in the current matrix.
- The alarm parser is offline and does not prove push reception nor live events.
- The update query has one request and one HTTP 200 XML response observed in the dynamic analysis. Errors, the complete decision and production TLS behaviour remain open. The matrix keeps `app.update_check` as `static_evidence_only` and this design does not raise that level.
- The repository is currently coupled to the fake in parts of the app. That fake will not be presented as QVCloud.

Therefore, the local handshake, authorization and descriptor infrastructure can be implemented before complete QVCloud. Shadow reads are conditioned to a session and to real provider availability. Stock list, realtime and actions must return `UNSUPPORTED` while their capability is not available. In demo, explicit simulation will be returned and never stock evidence.

## 3. Threat model

| Threat | Mandatory mitigation | Residual assumed |
|---|---|---|
| An app declares another app's package name | The name is checked against the real UID's packages and the registered current signature | A compromised device is outside the model |
| Package spoofing through Intent or Parcelable fields | Explicit binding and identity derivation in the service, never from caller extras | The caller can keep trying, but receives `PACKAGE_UID_MISMATCH` |
| An app with a different signature gains access | SHA-256 of the current certificate set and approval by the user | The signing key must remain protected by its owner |
| Use of an Android `normal` permission | It is not used as a security boundary, because it can be granted without a specific Wibox decision | It can be added only as a declarative filter, never as authorization |
| Misuse of a `signature` permission | It is not required from third parties, because only apps signed with the same key as Wibox satisfy it | Integrations with another signature depend on the allowlist |
| Shared UID | Denied in v1 if the UID contains more than one package name | A future version would need a specific rule and additional tests |
| Signature update or rotation | Same signature keeps access. A new signature suspends the grant and requires reapproval | The signature history is not accepted automatically |
| Reinstall with another UID | The grant contains UID, package and signature. A different UID requires reapproval | It is a conservative and documented decision |
| Revocation while there are callbacks or actions | The grant is checked on every call, invalidated by generation and subscriptions are removed | An action already sent can remain `UNKNOWN_OUTCOME` |
| Binder transaction flood, huge Parcelables or dead callbacks | Limits, rate limiting, asynchronous operations, bounded queues and `linkToDeath` | An authorized caller can consume its own quota |
| Confused deputy between apps or devices | Each request is tied to the connection binder, UID, grant, scopes and allowed devices | The user must review the permissions granted |
| Leak of tokens, cookies, credentials or opaque client IDs | Those fields do not exist in AIDL, SDK models, logs or errors | Necessary device IDs are minimized and redacted in logs |
| Downgrade or version mismatch | Major/minor handshake, method catalog and `VERSION_UNSUPPORTED` | A client must respect the negotiated version |
| Unknown enum after an update | AIDL integers are transmitted and the SDK keeps an `UNKNOWN` value | The app must not make destructive decisions with unknown values |
| False opening due to insufficient ack | Ack levels, visible simulation and absence of ambiguous physical retries | Physical observation is only claimed with validated independent evidence |

Second pass made on this design: package impersonation, shared UID, rotation, revocation, Intent hijacking, callbacks, overflow, cancellation, action repetition, downgrade, Parcelable compatibility and secret filtering were reviewed. The mitigations are expressed as implementation requirements, not as already proven defences.

## 4. Service and explicit binding

Proposed names, not yet present in the manifest:

- App package: `dev.nacho.wibox`.
- Service: `dev.nacho.wibox.externalapi.WiboxExternalApiService`.
- Action: `dev.nacho.wibox.externalapi.BIND`.
- Interface: `dev.nacho.wibox.externalapi.aidl.IWiboxExternalService`.

Conceptual fragment, not applied:

```xml
<service
    android:name=".externalapi.WiboxExternalApiService"
    android:exported="true"
    android:process="">
    <intent-filter>
        <action android:name="dev.nacho.wibox.externalapi.BIND" />
    </intent-filter>
</service>
```

`android:permission` is not proposed as an access control. An empty `android:process` represents the main process, not an attribute that should be copied literally. The implementation phase must omit that attribute unless another process is justified.

Rules:

1. Only binding by explicit component is accepted. The SDK fixes `ComponentName`, it never depends on resolving an implicit action.
2. `onBind` returns a lightweight Binder instance, different per connection. It does not initialize providers nor show UI.
3. The first valid operation is `handshake`. The rest return `HANDSHAKE_REQUIRED`.
4. Each method validates `Binder.getCallingUid()` again against the UID associated with that connection.
5. The service does not open HTTP sockets for the local integration and the consumer does not need the `INTERNET` permission to use Binder.
6. On Android 11 or later, the consuming app may need `<queries><package android:name="dev.nacho.wibox" /></queries>` for package visibility.
7. The external API v1 requires Android API 28 or higher to query `SigningInfo` consistently. On lower versions the handshake returns `UNSUPPORTED` unless a specific fallback is designed and tested.

## 5. Identity, authorization and allowlist

### 5.1 Identity derived on the server

In `handshake`, and again when the package changes or the cache is invalidated, the service will do the following:

1. Get the UID with `Binder.getCallingUid()`.
2. Reject isolated UIDs and those not belonging to normal apps according to the platform policy.
3. Resolve `PackageManager.getPackagesForUid(uid)`.
4. Require exactly one package in that UID and that it matches `ClientHello.packageName`. More than one package implies `SHARED_UID_UNSUPPORTED`.
5. Read `PackageInfo.signingInfo` with `GET_SIGNING_CERTIFICATES` and compute SHA-256 of each current signing certificate.
6. Normalize the hashes as lowercase hex without separators, sort them and compare the whole set. It is not enough for one to match if there are several signers.
7. Look for an active entry with the exact tuple `(uid, packageName, currentSignerSetSha256)`.
8. Authorize only the approved scopes and devices. The absence of an entry denies.

Identity and grant generation are captured during the Binder transaction and attached to the queued work. A background coroutine must not call `Binder.getCallingUid()` to reconstruct the caller, because there it is no longer in the context of the original transaction.

An empty granted device list means access to zero devices. There are no wildcards nor automatic inheritance to new devices. The user must widen the grant explicitly.

The certificate is not sent by the client. The service obtains it from `PackageManager`. The `ClientHello` package name is a claim to check, not proof.

### 5.2 Registration and approval

`requestAuthorization` allows registering a request with a visible name, scopes and desired devices. It does not grant access. The identity used is the one derived from the UID and signature, not the visible name provided by the app.

Approval is performed inside Wibox Settings. An external app cannot approve its own request, fake the dialog nor raise scopes. Wibox will show package, SHA-256 hashes, scopes, devices and state. Pending requests will be limited to avoid spam.

States:

- `NOT_REQUESTED`
- `PENDING`
- `AUTHORIZED`
- `DENIED`
- `REVOKED`
- `SIGNATURE_CHANGED`
- `UID_CHANGED`
- `SHARED_UID_UNSUPPORTED`

A revoked app can create a new request, but it stays `PENDING` and without access until a new user decision.

### 5.3 Minimum scopes

- `provider.read`
- `capabilities.read`
- `devices.read`
- `shadow.read`
- `events.read`
- `actions.unlock`
- `actions.f1`
- `actions.call_guard`
- `actions.call_control`
- `app.upgrade_check`

Authorizing a scope does not imply that the provider supports the operation. Identity, grant, scope, device, capability and session state are always checked. `required` in the matrix describes design, not runtime availability.

### 5.4 Update, rotation and revocation

- Update with the same UID, package and signer set: keeps the grant.
- Signature change: `SIGNATURE_CHANGED`, callbacks closed and mandatory reapproval. The certificate history does not authorize by itself.
- UID change: `UID_CHANGED` and reapproval.
- Revocation: the grant generation is incremented, later calls return `AUTHORIZATION_REVOKED` and callbacks are removed. The notification is best effort and never replaces the per-call check. As an exception without data exposure, `unsubscribeEvents` and the cancellation of operations belonging to that same connection are allowed to guarantee cleanup.
- An action revoked before being sent is cancelled. If it could already have left towards the provider, its result is `UNKNOWN_OUTCOME` until reconciled, never guaranteed physical cancellation.

The allowlist does not need to contain secrets. It will be persisted with a versioned schema and atomic writes. OAuth tokens, cookies, passwords, `clientId`, `dataEncodeKey`, QR codes and media keys are not stored.

## 6. Versioning and compatibility

There are two related but different versions:

1. AIDL wire `major.minor`, initially `1.0`.
2. SDK SemVer, initially `1.0.0` when a first real version is published.

Wire rules:

- Different major: `VERSION_UNSUPPORTED`.
- Same major: `minor = min(clientMinor, serverMinor)` is negotiated.
- A new minor only adds methods, capabilities, new Parcelable types or enum values.
- Existing AIDL methods do not change signature, order or semantics.
- Fields are not added nor reordered in an existing Parcelable within the same major. A new structural field uses a `V2` type and, when a signature changes, a new method. This avoids breaking V1 readers even without relying on Stable AIDL extensions.
- An incompatible change uses new `V2` types and a new major interface. A V1 Parcelable is not reinterpreted.
- The server keeps V1 readers during the documented migration window.
- A client with a higher minor only calls methods present in the negotiated version. The server also validates and returns `UNSUPPORTED` or `VERSION_UNSUPPORTED`, without relying on the SDK.
- Capabilities are queried at runtime. Their mere presence in the contract does not indicate availability.

The SDK will use SemVer: major for Kotlin API breakage, minor for compatible addition and patch for fixes. Deprecations will include a replacement and planned removal version. `WiboxClient` will not be an interface intended for implementation by consumers. Adding an abstract method that breaks implementers requires major, while a minor can only use a compatible binary addition. A binary compatibility check of the SDK will be added. No artifact will be published as part of this sheet.

## 7. Conceptual AIDL contract

The following code defines the proposed shape. Each Parcelable would live in its own AIDL file and would have validation at both ends. One-shot callbacks are `oneway` so that the service is not blocked by the consumer. One-way delivery is still subject to bounded queues and budgets.

```aidl
package dev.nacho.wibox.externalapi.aidl;

interface IWiboxExternalService {
    HandshakeResult handshake(in ClientHello hello);
    AuthorizationResult requestAuthorization(in AuthorizationRequest request);
    AuthorizationResult getAuthorizationState();

    ProviderSnapshotResult getProviderSnapshot();
    CapabilityPageResult getCapabilities(in CapabilityQuery query);

    void listDevices(in PageRequest request, in IDevicePageCallback callback);
    void getDevice(in DeviceRequest request, in IDeviceCallback callback);
    void getDeviceShadow(in ShadowQuery request, in IShadowCallback callback);
    void getDeviceShadows(in ShadowBatchQuery request, in IShadowBatchCallback callback);

    EventSubscriptionResult subscribeEvents(
        in EventSubscriptionRequest request,
        in IWiboxEventCallback callback
    );
    void unsubscribeEvents(in IWiboxEventCallback callback);

    void performAction(in ActionRequest request, in IActionCallback callback);
    CancellationResult cancelOperation(String requestId);
    ActionOutcomeResult getActionOutcome(String requestId);

    void checkAppUpgrade(
        in UpgradeCheckRequest request,
        in IUpgradeCallback callback
    );
}
```

Callbacks:

```aidl
oneway interface IDevicePageCallback {
    void onSuccess(in DevicePage result);
    void onError(in ApiError error);
}

oneway interface IDeviceCallback {
    void onSuccess(in DeviceSnapshot result);
    void onError(in ApiError error);
}

oneway interface IShadowCallback {
    void onSuccess(in ShadowSnapshot result);
    void onError(in ApiError error);
}

oneway interface IShadowBatchCallback {
    void onSuccess(in ShadowBatch result);
    void onError(in ApiError error);
}

oneway interface IActionCallback {
    void onOutcome(in ActionOutcome result);
    void onError(in ApiError error);
}

oneway interface IUpgradeCallback {
    void onSuccess(in UpgradeInfo result);
    void onError(in ApiError error);
}

oneway interface IWiboxEventCallback {
    void onEvents(in EventBatch batch);
    void onConnectionChanged(in EventConnectionState state);
    void onGap(in EventGap gap);
    void onAuthorizationChanged(in AuthorizationSnapshot state);
}
```

Network operations and actions are asynchronous. Synchronous methods only read already materialized local state and never wait for network, P2P or physical work. Each one-shot callback is removed in a `finally` after completing, failing, cancelling or the Binder dying.

## 8. Public models and absence of secrets

The following names are part of the JSON contract. AIDL types carry immutable data and primitives, bounded lists and other Parcelables. No generic `Bundle`, unlimited free JSON or Java serialization is used.

### 8.1 Connection, session and provider

- `ClientHello`: `packageName`, `clientApiMajor`, `clientApiMinor`, `clientSdkVersion`.
- `HandshakeResult`: negotiated version, authorization state, optional `ApiError`.
- `AuthorizationRequest`: untrusted visible name, scopes and requested device IDs.
- `AuthorizationSnapshot`: state, granted scopes, granted devices and grant generation.
- `ProviderSnapshot`: `providerId`, `profileVersion`, `connectionKind`, preparation state, `SessionSummary`, `simulated` and `observedAt`.
- `SessionSummary`: only `state`, `sessionGeneration` and optional `expiresAtEpochMs`. It does not contain opaque account, user, password, tokens, cookies, endpoints or `clientId`.
- `ApiError`: code, retry policy, capability, optional outcome and safe message. It never copies bodies, full URLs, headers or transport exceptions.

Session states: `UNAVAILABLE`, `AUTHENTICATION_REQUIRED`, `PREPARING`, `READY`, `DEGRADED`, `CLOSED`, `UNKNOWN`.

The API does not offer login, password grant, refresh, logout or registration to third parties. The session is managed by Wibox and the user. `REAUTHENTICATE` means opening Wibox and resolving it there, not asking the consuming app for credentials.

### 8.2 Capabilities and devices

- `CapabilityQuery`: page, optional device filter and namespace.
- `CapabilitySnapshot`: stable key, scope, support, availability, evidence, origin, profile, `observedAt`, `revalidateAfter`, limitations and typed extensions.
- `DeviceRef`: provider, device ID and optional channel.
- `DeviceSnapshot`: reference, name, declared model, role, presence, last observation, origin, freshness, `simulated` and device capabilities.
- `DevicePage`: items, optional next cursor, origin and freshness.

Presence: `ONLINE`, `OFFLINE`, `UNKNOWN`. A missing Shadow is never turned into `OFFLINE`.

External availability: `AVAILABLE`, `UNSUPPORTED`, `UNKNOWN`, `DEMO_ONLY`. The evidence keeps the matrix values and is transmitted separately. `AVAILABLE` requires a prepared runtime provider, not only static evidence.

### 8.3 Shadow and QVCloud extensions

- `ShadowQuery`: request ID, `DeviceRef`, bounded requested fields and freshness mode.
- `ShadowBatchQuery`: request ID and between 1 and 20 individual queries.
- `ShadowSnapshot`: device ID, typed fields, timestamp, logical path, optional sequence, freshness, origin and descriptors of unknown fields.
- `StockShadowFields`: nullable integer values for `F1_state`, `Switch_state`, `Private_mode_state`, `Fisheye_chn_state`, `Lock_state`, `Alarm_zone_mode`, `Smart_relay` and `Device_state`.
- `UnknownFieldDescriptor`: name and observed type, without an arbitrary value in v1.

No arbitrary JSON map is exposed. Unknown fields keep their existence and type, but their value is only added after a review of semantics, size and secrets. Future extensions use namespaced keys, for example `stock.qvcloud.shadow.f1_state`, and `ProviderExtensionValue` values of boolean, integer, decimal or validated short string type.

This avoids two mistakes: reducing stock to a common minimum and leaking sensitive content through a generic DTO.

### 8.4 Events

- `EventCursor`: stream generation and sequence. It is only position, not a credential nor bearer token.
- `EventSubscriptionRequest`: optional cursor, filter and maximum batch size.
- `EventFilter`: up to 20 devices, channels and bounded types.
- `EventEnvelope`: provider event ID, type, device, channel, raw time, optional parsed time, local reception, origin, duplicate, cursor, `simulated`, reviewed extensions and attachments by metadata without content.
- `EventBatch`: up to 10 events and a total serialized budget of 48 KiB.
- `EventGap`: last valid cursor, optional resumption cursor, cause, detected, `resyncRequired` and recoverability.
- `EventConnectionState`: `CONNECTED`, `RECONNECTING`, `RESYNC_REQUIRED`, `CLOSED`, `UNSUPPORTED`.

Photos, audio, video or broad URIs are not carried over Binder. A future attachment will need a separate channel with minimal URI grant, authorization and expiry. The v1 API only includes already reviewed non-secret metadata.

### 8.5 Actions

- `ActionRequest`: request ID provided by the SDK, `DeviceRef`, type, typed parameters and local expiry.
- `ActionOutcome`: request ID, action, device, ack level, origin, `simulated`, timestamp, sanitized provider code and optional observation event ID.
- `CancellationResult`: `CANCELLED_BEFORE_DISPATCH`, `CANCELLATION_REQUESTED`, `ALREADY_COMPLETED`, `NOT_FOUND`, `OUTCOME_UNKNOWN`.
- `UpgradeCheckRequest`: request ID and bounded BCP 47 language. Wibox provides its version and configuration internally.
- `UpgradeInfo`: state `UP_TO_DATE`, `AVAILABLE`, `UNKNOWN`, current version, optional available version, priority, sanitized description and `checkedAt`. It does not include cookie, `clientId`, download URL or installation.

The request ID correlates and deduplicates only within the authorized identity. It is not a token and does not grant access. `getActionOutcome` requires that `(uid, packageName, requestId)` matches the owner of the action.

## 9. Typed errors and retry

v1 codes:

- `NOT_AUTHORIZED`, `AUTHORIZATION_PENDING`, `AUTHORIZATION_REVOKED`, `AUTHORIZATION_DENIED`
- `PACKAGE_UID_MISMATCH`, `SIGNATURE_CHANGED`, `UID_CHANGED`, `SHARED_UID_UNSUPPORTED`
- `HANDSHAKE_REQUIRED`, `VERSION_UNSUPPORTED`
- `UNSUPPORTED`, `CAPABILITY_UNKNOWN`, `SESSION_REQUIRED`, `PROVIDER_NOT_READY`
- `NOT_FOUND`, `CONFLICT`, `INVALID_INPUT`, `PAYLOAD_TOO_LARGE`, `RATE_LIMITED`, `BUSY`
- `CANCELLED`, `SERVICE_DIED`, `CALLBACK_DEAD`
- `AUTHENTICATION`, `PROVIDER_AUTHORIZATION`, `NETWORK`, `TIMEOUT`, `PARSE`, `PERSISTENCE`
- `EVENT_GAP`, `OUTCOME_UNKNOWN`, `ACTION_REJECTED`, `INTERNAL`

Policies:

- `DO_NOT_RETRY`: invalid input, denied authorization, `UNSUPPORTED` or ambiguous action.
- `SAFE_READ_RETRY`: idempotent read only, with backoff, jitter and limit. The SDK does not enable it by default.
- `MANUAL_RETRY`: new decision and new request ID.
- `REAUTHENTICATE`: the user resolves the session in Wibox.
- `RENEGOTIATE`: repeat handshake or capabilities.
- `RESYNC`: invalid cursor or gap, without pretending continuity.

`UNSUPPORTED` and `CAPABILITY_UNKNOWN` are not collapsed. `UNKNOWN_OUTCOME` is neither a definitive failure nor a success.

## 10. API capability matrix

The JSON contract reproduces the 64 entries of `provider-matrix.json` with `stock_support` and `stock_status` without raising them. External classification:

- `contract_local`: designed local infrastructure, without a claim about QVCloud.
- `contract_runtime_gated`: a proposed method or model exists, but it will only be usable if the real provider offers the capability and the session is prepared.
- `contract_demo_only`: it can only produce labelled demo data, without stock compatibility.
- `unsupported_now`: stock returns `UNSUPPORTED` in v1. A generic method can exist for another provider or for demo.
- `not_exposed`: there is no external v1 method, for security, scope or lack of public usefulness.

Surface summary:

| Capability or group | Proposed API | Situation the SDK must show |
|---|---|---|
| `core.lifecycle` | handshake, close and local cancellation | Local design, pending implementation |
| `core.capability_negotiation` | `getCapabilities` | Local snapshot with evidence and freshness |
| Current session | `getProviderSnapshot` | Non-secret summary only |
| `shadow.get`, `shadow.multi` | `getDeviceShadow`, `getDeviceShadows` | Offline contract, availability conditioned to QVCloud and session |
| `devices.online_status` | `DeviceSnapshot` field | Semantics and freshness still conditioned |
| `devices.list` | `listDevices`, `getDevice` | `UNSUPPORTED` for current stock, demo only if labelled |
| `events.realtime` | `subscribeEvents` | `UNSUPPORTED` for current stock, mandatory gaps when it exists |
| Stock actions | `performAction`, reconciliation | Currently `UNSUPPORTED`, without physical retry |
| `app.update_check` | `checkAppUpgrade` | Implementation limited to the available observation, errors and TLS still open |
| OAuth, registration, login and logout | Not exposed | Wibox keeps credentials and session |
| Alarm parser | Not exposed | Arbitrary third-party payloads are not accepted |
| Pairing and invitations | Not exposed in v1 | They require ephemeral secrets and their own user flows |
| Configuration and firmware | Not exposed in v1 | `firmware.apply` remains `UNSUPPORTED` |
| Media, P2P, calls and gallery | No Binder transport | `UNSUPPORTED` for stock, another secure channel in the future |

The detailed JSON matrix is normative for the 64 capabilities. If the repo matrix changes, the verifier requires updating the contract instead of silently accepting a raise.

## 11. Events, gaps and resumption

1. The service checks `events.read`, allowed devices and `events.realtime` before subscribing.
2. If stock does not offer realtime, it returns `EventSubscriptionResult(error=UNSUPPORTED)`. It does not return an empty stream as if it were connected.
3. Each connection supports at most 4 subscriptions and each batch has at most 10 events.
4. Cursors contain generation and sequence, not secrets. Deduplication uses provider, internal account and event ID without exposing the account.
5. QoS 1, retained and reconnections do not guarantee total order nor exactly once. The model keeps duplicates and raw time.
6. When a queue overflows, the pending batches of that subscription are discarded and a single `RESYNC_REQUIRED` with `EventGap` is emitted. The gap is not hidden.
7. If there is no history to recover the gap, `recoverability=UNAVAILABLE`. Events are not invented.
8. The death of the callback removes the subscription. The SDK can resubscribe manually from the last cursor, never repeating actions as part of that recovery.
9. In demo, each event carries `simulated=true` and `origin=DEMO`.

## 12. Actions and ack levels

Allowed levels:

| Level | Meaning |
|---|---|
| `LOCAL_ACCEPTED` | Wibox or the demo provider accepted the request locally |
| `PROVIDER_ACCEPTED` | The cloud service accepted the command |
| `DEVICE_ACKNOWLEDGED` | The device confirmed reception or execution according to a known contract |
| `PHYSICAL_OBSERVED` | A validated independent observation confirms the effect |
| `UNKNOWN_OUTCOME` | Timeout, disconnection, late cancellation or ambiguous response |
| `REJECTED` | There is a typed and safe rejection |

Rules:

1. There is no success without a real ack. HTTP 2xx alone does not prove a physical effect.
2. `LOCAL_ACCEPTED` does not imply sending. `PROVIDER_ACCEPTED` does not imply reception by the device. `DEVICE_ACKNOWLEDGED` does not imply an open door unless semantics are explicit.
3. Only `PHYSICAL_OBSERVED`, with `simulated=false` and non-demo origin, allows the SDK to expose a confirmed physical effect.
4. No ambiguous physical action is retried automatically. The user must reconcile and create a new order manually.
5. A request ID duplicated by the same identity returns the pending operation or the already recorded outcome. It never executes the action again. A request ID reused with another payload returns `CONFLICT`.
6. Cancellation before dispatch prevents sending. After dispatch it returns `CANCELLATION_REQUESTED` and the result can be `UNKNOWN_OUTCOME`.
7. Actions are serialized per device when concurrency could duplicate effects. Scopes, invitation permissions and capability are applied in the service.
8. In demo, at most `LOCAL_ACCEPTED` is returned, always with `simulated=true` and `origin=DEMO`. It is never presented as QVCloud.
9. If the client process dies, Wibox keeps a bounded journal for reconciliation. If Wibox dies after a possible dispatch, the SDK returns `SERVICE_DIED` with `outcome=UNKNOWN_OUTCOME` and `MANUAL_RETRY`.

Media and P2P are not a by-product of this API. Accepting a call, preview, audio and video remain `UNSUPPORTED` while the matrix says so.

## 13. Limits, threading and lifecycle

Proposed budgets:

| Resource | v1 limit |
|---|---:|
| Request Parcelable | 32 KiB serialized |
| Response or callback Parcelable | 64 KiB serialized |
| Device page | 20 |
| Capability page | 50 |
| Shadow batch | 20 queries |
| Event batch | 10 events and 48 KiB |
| Device ID | 128 UTF-8 characters |
| Visible name | 128 characters |
| Extension string | 1024 UTF-8 bytes |
| Pending one-shot operations | 20 per app, 100 global |
| Event subscriptions | 4 per app |
| Queue per subscription | 100 events or 64 KiB, whichever comes first |
| Reads | 30 per minute and burst of 10 per UID |
| Actions | 6 per minute and burst of 2 per UID, plus per-device limits |
| Pending authorization requests | 3 per package and 50 global |

The limits are validated before calling the provider and when serializing. A list that exceeds the budget is paginated or returns `PAYLOAD_TOO_LARGE`, never silently truncated. The rate limit returns `RATE_LIMITED` with a bounded `retryAfterMs`. The values are design defaults and must be tested.

Threading and resources:

- The service schedules I/O on a supervised scope with the IO dispatcher. It never runs network or P2P on the main thread nor keeps a Binder thread waiting for a physical action.
- The SDK is main-safe. Synchronous local queries are called from `Dispatchers.IO` and asynchronous ones use `suspendCancellableCoroutine`.
- There is no `GlobalScope`. Closing the client cancels work, unregisters callbacks and unbinds the service.
- Cancelling a read cancels its coroutine and callback. Cancelling an action follows the semantics of the previous section.
- Each callback uses `linkToDeath`. Records are indexed by `callback.asBinder()` and removed on completion, unsubscribe, revocation, unbind or death. The SDK keeps a strong reference to the callback until the terminal result or close. A subscription that fails during registration does not retain the callback.
- There are no static callbacks nor references to `Activity` or UI `Context`. `ApplicationContext` is used.
- The SDK treats `onServiceDisconnected` and death recipient as abnormal close. Flows end with a typed error or allow explicit event resubscription.
- Frames, buffers, `Surface`, `ParcelFileDescriptor` or video are not passed through this API.

## 14. Proposed Kotlin SDK

Future artifact:

```text
dev.nacho.wibox:external-api-sdk:1.0.0
```

Optional low-level module for consumers that only need AIDL:

```text
dev.nacho.wibox:external-api-aidl:1.0.0
```

None is published now. The SDK will include immutable Kotlin models, tolerant mapping of unknown enums, a connection wrapper, coroutines and Flow. It will not expose Ktor, DTOs, tokens or demo classes.

Proposed high-level API:

```kotlin
interface WiboxClient : Closeable {
    suspend fun handshake(): Handshake
    suspend fun requestAuthorization(request: AccessRequest): Authorization
    suspend fun authorization(): Authorization
    suspend fun providerSnapshot(): ProviderSnapshot
    suspend fun capabilities(query: CapabilityQuery): CapabilityPage
    suspend fun devices(page: PageRequest): DevicePage
    suspend fun device(request: DeviceRequest): DeviceSnapshot
    suspend fun shadow(query: ShadowQuery): ShadowSnapshot
    suspend fun shadows(query: ShadowBatchQuery): ShadowBatch
    fun events(request: EventSubscriptionRequest): Flow<WiboxEventUpdate>
    suspend fun performAction(request: ActionRequest): ActionOutcome
    suspend fun actionOutcome(requestId: String): ActionOutcome
    suspend fun checkAppUpgrade(request: UpgradeCheckRequest): UpgradeInfo
}
```

Minimal example. The component names are encapsulated by the SDK and the authorization is approved in Wibox:

```kotlin
val client = WiboxClientFactory.connect(applicationContext)

try {
    val handshake = client.handshake()
    check(handshake.negotiatedMajor == 1)

    val authorization = client.requestAuthorization(
        AccessRequest(
            displayName = "My integration",
            scopes = setOf("provider.read", "capabilities.read", "devices.read"),
        )
    )

    if (authorization.state != AuthorizationState.AUTHORIZED) {
        // The user must review the request in Wibox Settings.
        return
    }

    val provider = client.providerSnapshot()
    val capabilities = client.capabilities(CapabilityQuery.firstPage())
    val devices = try {
        client.devices(PageRequest.firstPage())
    } catch (error: WiboxApiException) {
        if (error.code == ApiErrorCode.UNSUPPORTED) {
            DevicePage.unavailable(error)
        } else {
            throw error
        }
    }
} finally {
    client.close()
}
```

`DevicePage.unavailable` is a UI representation of the example, not an empty success list. The SDK does not retry actions nor hide `UNSUPPORTED`.

The SDK validates the explicit component and uses its `packageName` only as a claim that Wibox checks. It does not compute nor send a signature as proof. Wibox obtains the real signature from the system.

## 15. Implementation plan and future ownership

| Phase | Work | Future owning modules | Exit gate |
|---|---|---|---|
| E0 | Freeze contract, threat model and matrix | `docs`, `analysis/external-api` | Positive and negative verifier, security review |
| E1 | AIDL, Parcelables, errors, limits and handshake | `external-api:aidl` | Unit tests of round trip, limits and version |
| E2 | Binder service and lifecycle | `app/externalapi`, future `core:external-api` | Explicit binding, denial by default, cancellation and death |
| E3 | Identity, allowlist and revocation | `core:external-auth`, Android storage | Tests of UID, package, signature, scopes and rotation |
| E4 | Adapter over `WiboxProvider` | `core:provider-api`, `provider:stock-qvcloud`, `provider:demo` | No Ktor or DTOs in AIDL, capabilities per device |
| E5 | Reads, events and asynchronous actions | `core:external-api`, provider subports | Gaps, ack, journal and zero ambiguous retries |
| E6 | Kotlin SDK and models | `external-api:sdk` | Main-safe API, Flow, compatibility and sample |
| E7 | Authorized apps settings | `app/settings` | Registration, approval, revocation, scopes, signature and devices |
| E8 | Suite with two test apps | `external-api:test-client-a`, `external-api:test-client-b` | Different signatures, instrumentation and absence of secrets |

The integration must not skip E1-E4 by exposing the fake directly. The authorization UI can be developed in parallel with store mocks, but it is not connected to production without the identity tests.

## 16. Mandatory future tests

None of these tests has been run in this sheet.

### Unit tests

1. Parcelable round trip of all models, nulls, unknown enums, UTF-8 and exact limits.
2. Rejection of oversized lists, strings and transactions without silent truncation.
3. Handshake major/minor, unavailable method, downgrade and V1/V2 migration.
4. Authorization by UID/package/signature tuple, scopes, devices, grant generation and revocation.
5. Package spoof, wrong cert, multiple signature, shared UID, isolated UID, rotation and reinstall.
6. Rate limit per UID and global, saturation and recovery.
7. Typed errors and retry policies.
8. Actions with each ack, absence of ack, timeout, cancellation before and after dispatch, duplicate request ID with the same and different payload and zero ambiguous retries.
9. Demo always simulated and without fallback from stock.
10. Scan of transactions, errors and logs for tokens, cookies, credentials, `clientId`, secrets and raw payloads.

### Instrumentation with two apps of different signatures

1. App A authorized and app B unauthorized, checking data and scope isolation.
2. Approval, denial, revocation and reapproval from Settings.
3. App B impersonating A's package name and test with a wrong certificate.
4. Shared UID, UID change, update with same signature and rotation cases.
5. Explicit binding, implicit intent rejected by the SDK and wrong component without leak.
6. One-shot callbacks, subscriptions, gaps, overflow, unsubscription, Binder death and callback leaks.
7. Cancellation of reads and actions, client death and Wibox death with `UNKNOWN_OUTCOME` when applicable.
8. Actions without ack and verification that there is no automatic physical retry.
9. Compatibility between previous/new SDK minor and previous/new server.
10. `logcat` and lab Parcel captures without secrets or real account data.

Hardware tests, when necessary, will be a separate and expressly authorized phase. They are not a side effect of this suite.

## 17. v1 non-goals

- HTTP, REST, WebSocket API, LAN server or remote access.
- Authorizing third parties only by package name or by a `normal` permission.
- Using a custom signature permission as if it admitted apps of another signature.
- Sharing credentials, tokens, cookies, `clientId`, device secrets or media keys.
- Login, pairing, invitations, password changes or sensitive configuration from third parties.
- Video, audio, P2P, preview, recording or calls over Binder.
- Guaranteeing exactly-once delivery, total order or absence of gaps.
- Retrying ambiguous physical actions or claiming success without ack.
- Turning demo into QVCloud nor `local_only` into stock compatibility.
- Publishing the SDK, creating a remote repository or declaring the service available.

## 18. Availability criterion

The short answer is yes, a secure local API with Binder/AIDL is feasible. The first useful delivery should be limited to handshake, authorization, provider, capabilities and the reads the provider can back. Shadow depends on session and QVCloud runtime. Stock list, realtime and actions remain `UNSUPPORTED` while the matrix says so. Demo can be used for development with visible simulation. Media needs evidence and a different channel, not a big Parcelable.

The implementation can only be announced when the service, the allowlist, the SDK and the two-signature suite exist, with measured evidence. Until then the state is `design-only`.
