# Local API implementation for other apps

This document describes the real implementation of the Binder/AIDL service exposed by Wibox to other apps. The reference design is `docs/external-api.md` and the frozen contract is `analysis/external-api/api-contract.json`. The automated verification lives in `analysis/external-api-runtime/verify_external_api_runtime.py`.

## What was implemented

- `dev.nacho.wibox.externalapi.WiboxExternalApiService` service in the `app` module, which returns an instance of `IWiboxExternalService.Stub` per connection. Registered in `app/src/main/AndroidManifest.xml` as an `exported="true"` service with the `dev.nacho.wibox.externalapi.BIND` action and without an `android:permission` attribute: authorization is decided per call, not by a declarative permission.
- Pure authorization engine in the `external-api` module, package `dev.nacho.wibox.externalapi.server`: identity resolution, allowlist, revocation and pending request limits. It is Kotlin without Android, covered by unit tests.
- Allowlist persistence in the app with `SharedPreferencesAuthorizationStore`, versioned schema (v1) and atomic write with `commit()`. It stores no secrets: uid, package, digest of the signer set, scopes, devices, generation, display name and state.
- Service runtime (`ExternalApiRuntime`) with per-uid burst limits (`TokenBucket`), a per-app and global budget of one-shot operations, and a bounded action journal for deduplication, cancellation and result queries.
- App-specific mappers from `core.cloud` to the Wire parcelables (`ExternalApiMappers`), with honest labelling: origin and `simulated=true` when the provider is demo.
- Existing Kotlin SDK in `external-api/src/main/kotlin/.../sdk/` with no contract changes.
- Tests: 24 unit tests in `external-api` (full authorization and signer hash) and 3 instrumented tests in `app/src/androidTest` that exercise the real Binder service.

## How the service authorizes

On every Binder transaction the service derives the caller identity, it never accepts it from the client:

1. It gets the real uid with `Binder.getCallingUid()` inside the transaction. Background coroutines receive that captured identity, they do not ask again.
2. It rejects uids below `FIRST_APPLICATION_UID` and the isolated uid range (99000..99999).
3. It resolves the packages of the uid with `PackageManager.getPackagesForUid(uid)`. More than one package ends in `SHARED_UID_UNSUPPORTED`.
4. The package claimed in `ClientHello` is checked against the one derived from the uid. A mismatch ends in `PACKAGE_UID_MISMATCH`.
5. It reads the current signing certificates with `PackageInfo.signingInfo` and `GET_SIGNING_CERTIFICATES` (`apkContentsSigners`), computes SHA-256 in lowercase hex without separators for each certificate, sorts the set and computes the final digest of the sorted set. Two different signer sets always produce two different digests.
6. It looks for an active entry with the exact tuple (uid, package, signer digest) in the allowlist. The absence of an entry denies: denial by default.
7. It checks the requested scope and, if the operation is bound to a device, that the device is in the granted list. An empty list means access to zero devices.

The first valid operation of each connection is `handshake`, the rest return `HANDSHAKE_REQUIRED`. The handshake negotiates `minor = min(client, server)` with major 1 and returns the current authorization state without granting anything. Below Android 9 the handshake returns `UNSUPPORTED`, because `SigningInfo` does not exist before API 28.

## How it is revoked

Revocation, approval and denial are user decisions from Wibox Settings and are materialized in the engine: `approve`, `deny` and `revoke` over the tuple (uid, package, digest). `revoke` increments the grant generation and marks the `REVOKED` state. Since the service revalidates the tuple on every call, revocation applies to the next call with no need for notifications. `unsubscribeEvents` and the cancellation of own operations remain available for cleanup. The signature history does not authorize by itself: a signature change produces `SIGNATURE_CHANGED` and requires reapproval, just as a uid change produces `UID_CHANGED`.

An unauthorized app can call `requestAuthorization` and stays `PENDING` with its requested scopes and devices. No path of the API grants access by itself: approval only exists in the engine, invoked from Settings. The pending request limits are 3 per package and 50 global, excess responds `RATE_LIMITED`.

## Real limits applied

- Request 32 KiB and response 64 KiB, applied by the `BoundedParcelable` budget. The event batch is bounded to 10 events and 48 KiB by its own parcelable.
- Pages: devices and capabilities limited to 20 items per request (the `PageRequest` parcelable limits `limit` to 20 and the response pages validate their maximum). Shadow batch of 20 queries. Device IDs of 128 UTF-8 bytes. No silent truncation.
- Pending one-shot operations: 20 per app and 100 global, excess responds `BUSY`.
- Event subscriptions: 4 per app, checked at registration. Today it is unreachable because realtime is not available, but the path exists for future providers.
- Reads: 30 per minute with a burst of 10 per uid. Actions: 6 per minute with a burst of 2 per uid. Excess responds `RATE_LIMITED` with a bounded `retryAfterMs`.
- A duplicate action request ID from the same identity returns the pending operation or the recorded result, it never re-executes. Reuse with another payload returns `CONFLICT`. Action results remain in a journal bounded to 256 entries with cleanup of the finished ones.

## What remains Unsupported and why

- `subscribeEvents`: returns `UNSUPPORTED` (or `CAPABILITY_UNKNOWN` if the provider has no row in the catalog). No provider in the repo offers a realtime stream with exact cursors today, and returning a fake connected stream would break the honesty of gaps and resumption. The catalog marks `events.realtime` as `unsupported`.
- `checkAppUpgrade` in demo: the repository throws `ProviderError.Unsupported` because there is no real upgrade configuration in the local demo. It is mapped to `UNSUPPORTED` without inventing versions.
- `listDevices` and `getDevice` in a stock provider: they delegate to the devices port, whose default behaviour throws `ProviderError.Unsupported` which is mapped to `UNSUPPORTED`. In demo they work with `simulated=true` labelling and `DEMO` origin, as the contract allows (`unsupported_now` with demo simulation allowed).
- Media, P2P, calls over Binder, login, pairing and configuration: not exposed in v1, no external method.

Shadows (`getDeviceShadow` and `getDeviceShadows`) delegate to the devices port. In demo they return the local state labelled as simulated. Unknown fields are described by name and observed type, never by their value.

## How to test it

Verification commands, all run against the `emulator-5558` emulator when applicable:

```bash
# Runtime verifier (G1)
python3 analysis/external-api-runtime/verify_external_api_runtime.py

# Unit tests, lint and build (G2)
./gradlew :external-api:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Instrumented tests, including denial by default (G3)
ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest
```

The `ExternalApiDenyByDefaultTest` instrumented test binds to the service with the app's own signature and no allowlist entry, and checks that handshake works without a grant, that `requestAuthorization` stays `PENDING` without access, that `getDevice`, `getDeviceShadow` and `performAction` return `NOT_AUTHORIZED`, and that every call without handshake returns `HANDSHAKE_REQUIRED`.

For a real external integration: the consuming app binds to the explicit component `dev.nacho.wibox.externalapi.WiboxExternalApiService`, performs the handshake, calls `requestAuthorization` and waits for approval in Wibox Settings. Programmatic approval from the engine (`approve`/`revoke`) does not have its own screen yet: it is piece E7 of the design implementation plan.

## Deviations from the original design

- The app declares `tools:overrideLibrary="dev.nacho.wibox.externalapi"` to keep its `minSdk 26` alongside the library with `minSdk 28`. The design requires API 28 for `SigningInfo`, the service covers it by returning `UNSUPPORTED` in the handshake below Android 9.
- `app/build.gradle.kts` adds `implementation(project(":external-api"))`: the service needs the Stub classes of the AIDL contract and the Wire types of the module.
- The authorization engine lives in `external-api/src/main/kotlin/.../server/` instead of a future `core:external-auth` module: it keeps the piece testable without Android and without creating a new module in this sheet.

## Status

Service, revocable allowlist, denial by default, limits, action journal and tests with two signatures: implemented and verified with measured evidence. The Settings UI to approve requests and the suite with two test apps with different real signatures are left for phases E7 and E8 of the design.
