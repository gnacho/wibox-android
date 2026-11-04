# Wibox provider port implementation

## 1. Scope and certainty

This sheet implements F0/F1: a provider-neutral port and a `stock-qvcloud` adapter over the existing `WiboxCloud`. It does not replace the legacy API, does not add new protocol and does not certify full cloud compatibility. All evidence comes from the already recorded offline contracts and from attempt3 for app upgrade. No network, hardware or credentials were used.

The F0 matrix remains `analysis/architecture/provider-matrix.json`. The `ProviderCapabilityCatalog.stockQvCloud` catalog keeps its 64 IDs, support, status, references and limitations. The available operation is evaluated separately so that the presence of a method does not become a claim of compatibility.

## 2. Real public API

The whole public API lives in `dev.nacho.wibox.core.cloud` and has no Ktor, wire DTOs or Android types.

| Class or contract | Responsibility |
|---|---|
| `WiboxProvider` | Descriptor, catalog, subports, preparation and close |
| `WiboxProviderFactory` | Explicit creation of the stock adapter. It offers no MQTT or self-hosted factories |
| `ProviderDescriptor` | `ProviderId`, profile version, connection and declarative QVCloud extensions |
| `ProviderCapabilityCatalog` | Immutable catalog of the 64 capabilities |
| `CapabilitySnapshot` | F0 support, scope, optional device/channel, revalidation, origin, declared evidence, operation and operation evidence |
| `SessionPort` | Authenticate, restore and refresh. `logout` remains `Unsupported` |
| `SessionHandle` | Opaque identity without tokens or password. `toString` redacts identifiers |
| `CredentialMaterial` | Ephemeral material for authentication. `toString` redacts identity and secret |
| `DevicePort` | Single and multi Shadow, subscription and unsubscription |
| `DeviceSnapshot` | Device with role, permissions, channels, presence, origin and freshness. It is not used as an empty success list |
| `ShadowSnapshot` | Requested and returned fields, state, timestamp, freshness and preserved unknowns |
| `EventPort` | Offline FCM parser and realtime boundary |
| `EventEnvelope` | Event with origin, raw time, unknown fields, cursor and duplicates |
| `EventStreamItem` | Events, connection, reconnection, resync and gaps for future proven channels |
| `ActionPort` | Action boundary. Currently returns `Unsupported` before executing |
| `ActionOutcome` | Correlation and levels LOCAL_ACCEPTED, PROVIDER_ACCEPTED, DEVICE_ACKNOWLEDGED, PHYSICAL_OBSERVED, REJECTED and UNKNOWN_OUTCOME |
| `MaintenancePort` | App update query |
| `ProviderError` | Typed errors with operation, capability, retry, code and sanitized cause |

`ProviderId.MQTT`, `ProviderId.SELF_HOSTED` and `ProviderConnectionKind` only identify future designs. There are no adapters for those IDs and no implemented capabilities associated with them. `ProviderId.DEMO` does not enable any fallback.

## 3. Three axes that are not confused

Each `CapabilitySnapshot` separates:

1. `support`: declarative F0 obligation, with required, optional, unsupported or unknown.
2. `declaredEvidence` and `declaredOrigin`: status and origin recorded in the F0 matrix.
3. `operation`, `operationEvidence` and `operationOrigin`: operation actually exposed and the evidence that supports it.

The current rule only changes the operation evidence of `app.update_check`: the matrix keeps `static_evidence_only`, while the adapter presents `IMPLEMENTED_CONTRACT` with `DYNAMIC_CAPTURE` by `DYN3-HTTP-UPGRADE-001`. OAuth, Shadow, pairing, list, realtime, media and actions are not raised to runtime behaviour.

`limitations` keeps the four current dimensions of each entry. There is no `hardware_verified` property, nor an automatic conversion of offline tests into physical proof.

## 4. Implemented operations and evidence

| Neutral operation | Legacy delegation | Evidence and scope | Behaviour test |
|---|---|---|---|
| `prepare` | `restoreSession` | Offline restoration. READY only with a session and an open provider | Preparation with/without session and concurrent close |
| `session.authenticate` | `authenticate` | E029, E033, offline OAuth password contract. No invented opaque registration | Opaque session and redacted secrets |
| `session.restoreSession` | `restoreSession` | E034, E036, offline restoration | Restoration and preparation |
| `session.refreshSession` | `refreshSession` | E033 to E036, legacy single-flight | REFRESHED state and same opaque handle |
| `devices.getShadow` | `getDeviceShadow` | E041, offline contract | Snapshot and unknown fields |
| `devices.getShadows` | `getDeviceShadows` | E041, offline contract | Keeps the list without filling in state |
| `devices.subscribeShadow` | `subscribeDeviceShadows` | E041, HTTP ack only. Does not prove later MQTT | Typed ack, no realtime promised |
| `devices.unsubscribeShadow` | `unsubscribeDeviceShadows` | E041, reasons 1 and 2 without implicit logout/physical unbind | Ack and timestamp preserved |
| `events.parseAlarm` | `parseAlarmEvent` | E056, E057, offline parser 2/19/20 | FCM envelope and unknowns |
| `maintenance.checkAppUpgrade` | `cloud.checkAppUpgrade(request)` | E050 and attempt3, GET + HTTP 200 XML. Does not recalculate HTTP or parse XML in the adapter | A single delegation, identical result and mapped errors |
| `close` | `close` conditioned by ownership | Offline lifecycle, idempotent close | A single close owned by the adapter and subsequent operation rejected |

`ProviderErrorMapper` keeps authentication, authorization, HTTP, network, timeout, parsing, persistence, invalid input, Unsupported, Closed and business codes without copying URLs, bodies or tokens. `CancellationException` propagates as cancellation, not as `Network`.

## 5. Full F0/F1 matrix

The `operation` column describes the evidence of the exposed operation, not total compatibility or hardware.

| Capability | F0 support | F0 evidence | F1 operation |
|---|---|---|---|
| `core.lifecycle` | required | implemented_contract | implemented_contract |
| `core.capability_negotiation` | required | local_only | local_only |
| `auth.opaque_registration` | required | static_evidence_only | static_evidence_only |
| `auth.user_login` | required | static_evidence_only | static_evidence_only |
| `auth.oauth_password` | required | implemented_contract | implemented_contract |
| `auth.oauth_refresh` | required | implemented_contract | implemented_contract |
| `auth.session_restore` | required | implemented_contract | implemented_contract |
| `auth.logout` | unknown | unknown | unknown |
| `discovery.regional_services` | required | unsupported | unsupported |
| `devices.list` | required | unsupported | unsupported |
| `devices.dynamic_credentials` | required | static_evidence_only | static_evidence_only |
| `devices.local_discovery` | required | unsupported | unsupported |
| `devices.online_status` | required | static_evidence_only | static_evidence_only |
| `pairing.qr` | required | local_only | local_only |
| `pairing.ap_wifi` | required | local_only | local_only |
| `pairing.audio` | required | local_only | local_only |
| `pairing.bind` | required | local_only | local_only |
| `invitations.accept` | required | local_only | local_only |
| `invitations.manage` | required | local_only | local_only |
| `invitations.issue` | required | local_only | local_only |
| `invitations.permissions` | required | local_only | local_only |
| `shadow.get` | required | implemented_contract | implemented_contract |
| `shadow.multi` | required | implemented_contract | implemented_contract |
| `shadow.subscribe` | required | implemented_contract | implemented_contract |
| `shadow.unsubscribe` | required | implemented_contract | implemented_contract |
| `events.alarm_parser` | required | implemented_offline | implemented_offline |
| `events.push_registration` | required | static_evidence_only | static_evidence_only |
| `events.realtime` | required | unsupported | unsupported |
| `events.history` | required | local_only | local_only |
| `events.photo` | required | local_only | local_only |
| `events.delete` | required | local_only | local_only |
| `events.filters` | optional | static_evidence_only | static_evidence_only |
| `media.preview_single` | required | unsupported | unsupported |
| `media.preview_multi` | required | unsupported | unsupported |
| `media.stream_controls` | required | unsupported | unsupported |
| `media.listen` | required | unsupported | unsupported |
| `media.talk` | required | unsupported | unsupported |
| `media.snapshot` | required | unsupported | unsupported |
| `media.record` | required | unsupported | unsupported |
| `media.local_gallery` | optional | local_only | local_only |
| `media.local_share_save` | optional | local_only | local_only |
| `actions.unlock` | required | unsupported | unsupported |
| `actions.f1` | required | unsupported | unsupported |
| `actions.call_guard` | required | unsupported | unsupported |
| `actions.call_accept` | required | unsupported | unsupported |
| `actions.call_reject` | required | unsupported | unsupported |
| `calls.lifecycle` | required | local_only | local_only |
| `config.rename` | required | local_only | local_only |
| `config.passwords` | required | local_only | local_only |
| `config.unlock_security` | required | local_only | local_only |
| `config.alarms` | required | local_only | local_only |
| `config.wifi` | required | local_only | local_only |
| `config.time_sync` | required | local_only | local_only |
| `config.playback_intercom` | required | local_only | local_only |
| `config.delete_device` | required | local_only | local_only |
| `firmware.check` | required | local_only | local_only |
| `firmware.apply` | required | unsupported | unsupported |
| `app.update_check` | optional | static_evidence_only | implemented_contract with dynamic capture |
| `app.privacy_terms` | required | local_only | local_only |
| `app.deep_link` | optional | static_evidence_only | static_evidence_only |
| `app.diagnostics` | optional | local_only | local_only |
| `app.sound_vibration` | required | local_only | local_only |
| `security.persistence` | required | design_required | design_required |
| `events.notifications` | required | static_evidence_only | static_evidence_only |

Opaque registration, `userLogin`, regional/LAN discovery, list, dynamic credentials, pairing, invitations, history, push, media, actions, configuration and firmware do not have a new operation just because they appear in the catalog.

## 6. Actions and acknowledgements

`ActionCommand` holds correlation, device, channel, type, redacted parameters and retry policy. `ActionPort.execute` does not call `performAction` while the capability is `unsupported`, so a fake cannot turn the operation into a success or trigger retries.

`ActionOutcome.canClaimPhysicalEffect()` is only true with `PHYSICAL_OBSERVED`, a present observation and no ambiguous result. `LOCAL_ACCEPTED`, `PROVIDER_ACCEPTED` and `DEVICE_ACKNOWLEDGED` never translate by themselves into an open door. The adapter does not generate the high levels because there is no device ack or physical evidence.

`UNKNOWN_OUTCOME` uses `DO_NOT_RETRY`. An ambiguous physical action is not retried and success is not reported from a transport response.

## 7. Ownership, concurrency and close

`WiboxProviderFactory.stockQvCloud` receives an existing `WiboxCloud` and a `ProviderSessionContext`. The opaque identifier is generated inside the adapter and stays stable across authentication, restoration and refresh. Legacy tokens do not cross the port.

`closeClientWhenClosed=true` is the default: the provider owns the client and closes it exactly once. With `false`, the caller keeps ownership and can keep using the legacy client. In both cases the provider is closed for new operations. Descriptor and catalog are immutable and remain available.

`prepare` serializes preparations with a mutex and checks the state again after restoring. That way it does not return READY if a concurrent close happens. There is no additional global scope: the owning client keeps its resources and its refresh single-flight.

`observeEvents` does not emit an empty success stream. An unexpected emission from a fake becomes `Unsupported`, an empty completion as well, and cancellation propagates. The gap and cursor models exist for future proven implementations, not as evidence of current realtime.

## 8. App and UI migration

The app can keep `WiboxCloud`, `WiboxCloudFactory`, `TokenStore`, models and current fakes. The adapter wraps that API without modifying its methods or raising its capabilities. Migration consists of injecting `WiboxProvider` into the repository and consuming `SessionHandle`, `ShadowSnapshot`, `EventEnvelope`, `ActionOutcome` and `ProviderError` instead of QVCloud types or UI fixtures.

ViewModels must not know `AppUpgradeRequest.baseUrl`, topics, tokens or transport. Composition that holds secret or specific configuration stays outside the UI. The UI decides presentation from `CapabilitySnapshot`, distinguishing unsupported and unknown. A stock error never selects demo.

`ActionOutcomeText` can only announce a physical effect when `canClaimPhysicalEffect()` is true. `EventStreamItem.Gap` and `ResyncRequired` are shown as loss of continuity, not as events.

## 9. Future Binder consumption

A Binder API must live in another layer and translate the neutral models into versioned Parcelable/AIDL contracts. It must not export `WiboxCloud`, `CloudSession`, Ktor, URLs, DTOs or `CredentialMaterial`.

Suspend calls are adapted to commands with a correlation identifier. `Flow<EventStreamItem>` is projected to callbacks that keep event, gap, resync and cursor. Closing the Binder client cancels the collection and, depending on ownership, closes the provider once. Secrets stay in the owning process and never travel in snapshots, logs or callbacks.

## 10. Future providers and limits

MQTT and self-hosted only have reserved IDs. There are no adapter classes, brokers, topics, alternative cloud or announced support. The static MQTT of the APK remains disabled and is not turned into the future MQTT provider.

These limitations persist: registration and login without full request/response, discovery without a regional response, list without a contract, realtime without a proven channel, media without a FOSS alternative, actions without ack and unproven hardware. OAuth and Shadow remain offline contracts. App upgrade only covers the observed success response, without availability decision, error contract or full compatibility.

## 11. Verification

```bash
python3 core/cloud/verify_provider_port.py
python3 core/cloud/verify_upgrade_contract.py
python3 core/cloud/verify_protocol.py
python3 core/cloud/verify_no_secrets.py
./gradlew :core:cloud:test --rerun-tasks
```

`verification.txt` records outputs, test counts and measured limits.
