package dev.nacho.wibox.externalapi

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome as CoreActionOutcome
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DeviceRef as CoreDeviceRef
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderConnectionKind
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.WiboxProvider
import dev.nacho.wibox.externalapi.aidl.ActionOutcome as WireActionOutcome
import dev.nacho.wibox.externalapi.aidl.ActionOutcomeResult
import dev.nacho.wibox.externalapi.aidl.ActionRequest
import dev.nacho.wibox.externalapi.aidl.ApiError
import dev.nacho.wibox.externalapi.aidl.AuthorizationRequest
import dev.nacho.wibox.externalapi.aidl.AuthorizationResult
import dev.nacho.wibox.externalapi.aidl.CapabilityPage
import dev.nacho.wibox.externalapi.aidl.CapabilityPageResult
import dev.nacho.wibox.externalapi.aidl.CapabilityQuery
import dev.nacho.wibox.externalapi.aidl.CancellationResult
import dev.nacho.wibox.externalapi.aidl.ClientHello
import dev.nacho.wibox.externalapi.aidl.DevicePage
import dev.nacho.wibox.externalapi.aidl.DeviceRequest
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionRequest
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionResult
import dev.nacho.wibox.externalapi.aidl.HandshakeResult
import dev.nacho.wibox.externalapi.aidl.IActionCallback
import dev.nacho.wibox.externalapi.aidl.IDeviceCallback
import dev.nacho.wibox.externalapi.aidl.IDevicePageCallback
import dev.nacho.wibox.externalapi.aidl.IShadowBatchCallback
import dev.nacho.wibox.externalapi.aidl.IShadowCallback
import dev.nacho.wibox.externalapi.aidl.IUpgradeCallback
import dev.nacho.wibox.externalapi.aidl.IWiboxEventCallback
import dev.nacho.wibox.externalapi.aidl.IWiboxExternalService
import dev.nacho.wibox.externalapi.aidl.PageCursor
import dev.nacho.wibox.externalapi.aidl.PageRequest
import dev.nacho.wibox.externalapi.aidl.ProviderExtensionValue
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshotResult
import dev.nacho.wibox.externalapi.aidl.ShadowBatch
import dev.nacho.wibox.externalapi.aidl.ShadowBatchQuery
import dev.nacho.wibox.externalapi.aidl.ShadowQuery
import dev.nacho.wibox.externalapi.aidl.UpgradeCheckRequest
import dev.nacho.wibox.externalapi.aidl.WireAckLevel
import dev.nacho.wibox.externalapi.aidl.WireActionType
import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireAvailability
import dev.nacho.wibox.externalapi.aidl.WireCancellationState
import dev.nacho.wibox.externalapi.aidl.WireDataSource
import dev.nacho.wibox.externalapi.aidl.WireExtensionValueType
import dev.nacho.wibox.externalapi.aidl.WireRetryPolicy
import dev.nacho.wibox.externalapi.sdk.WiboxExternalApi
import dev.nacho.wibox.externalapi.server.ApiScopes
import dev.nacho.wibox.externalapi.server.CallerIdentity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Binder entry point of the local external API.
 *
 * Security model: explicit binding only, one stub per connection, handshake
 * required before any other call, authorization re-checked on every
 * transaction from the real calling uid, the single package owned by that
 * uid and the SHA-256 of the current signer set, against the persisted
 * revocable allowlist. Deny by default. Revocation applies to the next
 * call; unsubscribeEvents and cancellation of owned operations stay
 * available for cleanup.
 */
class WiboxExternalApiService : Service() {
    private lateinit var runtime: ExternalApiRuntime

    override fun onCreate() {
        super.onCreate()
        runtime = ExternalApiRuntime(applicationContext)
        runtime.ioScope.launch { runtime.ensurePrepared() }
    }

    override fun onBind(intent: Intent?): IBinder = ConnectionStub(runtime)

    override fun onUnbind(intent: Intent?): Boolean = false

    private inner class ConnectionStub(private val runtime: ExternalApiRuntime) : IWiboxExternalService.Stub() {
        @Volatile
        private var handshakeIdentity: CallerIdentity.Verified? = null

        private val subscriptions = ConcurrentHashMap<IBinder, Boolean>()

        // Handshake, authorization and identity

        override fun handshake(hello: ClientHello?): HandshakeResult {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return handshakeError(WireApiErrorCode.UNSUPPORTED, "The external API requires Android 9 or later")
            }
            val hello = hello ?: return handshakeError(WireApiErrorCode.INVALID_INPUT, "ClientHello is required")
            val identity = runtime.resolveIdentity(hello.packageName)
            return when (identity) {
                is CallerIdentity.NonApplicationUid ->
                    handshakeError(WireApiErrorCode.NOT_AUTHORIZED, "Wibox does not authorize this caller uid")
                is CallerIdentity.NoSigningInfo ->
                    handshakeError(WireApiErrorCode.NOT_AUTHORIZED, "Signing certificates could not be read")
                is CallerIdentity.SharedUid ->
                    handshakeError(WireApiErrorCode.SHARED_UID_UNSUPPORTED, "Shared uid callers are not supported")
                is CallerIdentity.PackageMismatch ->
                    handshakeError(WireApiErrorCode.PACKAGE_UID_MISMATCH, "Package does not match the calling uid")
                is CallerIdentity.Verified -> {
                    if (hello.clientApiMajor != WiboxExternalApi.WIRE_MAJOR) {
                        handshakeError(WireApiErrorCode.VERSION_UNSUPPORTED, "Wire major version is not supported")
                    } else {
                        handshakeIdentity = identity
                        HandshakeResult(
                            serverApiMajor = WiboxExternalApi.WIRE_MAJOR,
                            serverApiMinor = WiboxExternalApi.WIRE_MINOR,
                            negotiatedApiMinor = minOf(hello.clientApiMinor, WiboxExternalApi.WIRE_MINOR),
                            authorization = runtime.engine.snapshot(identity),
                            error = null,
                        )
                    }
                }
            }
        }

        override fun requestAuthorization(request: AuthorizationRequest?): AuthorizationResult {
            val identity = handshakeIdentity
                ?: return AuthorizationResult(authorization = null, error = handshakeRequiredError())
            val request = request ?: return AuthorizationResult(authorization = null, error = invalidInput("AuthorizationRequest is required"))
            val outcome = runtime.engine.requestAuthorization(
                identity = identity,
                displayName = request.displayName,
                scopes = request.requestedScopes,
                deviceIds = request.requestedDeviceIds,
            )
            return when {
                outcome.snapshot != null -> AuthorizationResult(authorization = outcome.snapshot, error = null)
                outcome.errorCode == WireApiErrorCode.RATE_LIMITED -> AuthorizationResult(
                    authorization = null,
                    error = ExternalApiMappers.apiError(
                        WireApiErrorCode.RATE_LIMITED,
                        WireRetryPolicy.DO_NOT_RETRY,
                        outcome.safeMessage ?: "Too many pending authorization requests",
                        retryAfterMs = outcome.retryAfterMs,
                    ),
                )
                else -> AuthorizationResult(
                    authorization = null,
                    error = ExternalApiMappers.apiError(
                        outcome.errorCode ?: WireApiErrorCode.INTERNAL,
                        WireRetryPolicy.DO_NOT_RETRY,
                        outcome.safeMessage ?: "Authorization request failed",
                    ),
                )
            }
        }

        override fun getAuthorizationState(): AuthorizationResult {
            val identity = handshakeIdentity
                ?: return AuthorizationResult(authorization = null, error = handshakeRequiredError())
            return AuthorizationResult(authorization = runtime.engine.snapshot(identity), error = null)
        }

        // Local reads: only state already materialized, no provider I/O on the Binder thread

        override fun getProviderSnapshot(): ProviderSnapshotResult {
            val (identity, rejection) = preflight(ApiScopes.PROVIDER_READ, deviceId = null, meterRead = true)
                ?: return ProviderSnapshotResult(snapshot = null, error = handshakeRequiredError())
            if (rejection != null) return ProviderSnapshotResult(snapshot = null, error = rejection)
            val preparation = runtime.preparation
            if (preparation != PreparationState.READY) {
                return ProviderSnapshotResult(
                    snapshot = null,
                    error = ExternalApiMappers.apiError(
                        WireApiErrorCode.PROVIDER_NOT_READY,
                        WireRetryPolicy.RENEGOTIATE,
                        "The provider is not prepared yet",
                    ),
                )
            }
            return ProviderSnapshotResult(
                snapshot = ExternalApiMappers.providerSnapshot(
                    descriptor = runtime.provider().descriptor,
                    preparation = preparation,
                    nowEpochMs = System.currentTimeMillis(),
                ),
                error = null,
            )
        }

        override fun getCapabilities(query: CapabilityQuery?): CapabilityPageResult {
            val (identity, rejection) = preflight(ApiScopes.CAPABILITIES_READ, deviceId = null, meterRead = true)
                ?: return CapabilityPageResult(page = null, error = handshakeRequiredError())
            if (rejection != null) return CapabilityPageResult(page = null, error = rejection)
            val query = query ?: return CapabilityPageResult(page = null, error = invalidInput("CapabilityQuery is required"))
            val provider = runtime.provider()
            val filtered = provider.capabilities
                .asSequence()
                .filter { query.deviceId == null || it.device?.id == query.deviceId }
                .filter { namespaceFilter(it.key.id, query.namespace) }
                .toList()
            val offset = (query.page.cursor?.offset ?: 0).coerceIn(0, filtered.size)
            val limit = query.page.limit.coerceIn(1, PAGE_LIMIT)
            val pageItems = filtered.drop(offset).take(limit)
            val now = System.currentTimeMillis()
            val nextCursor = if (offset + pageItems.size < filtered.size) {
                PageCursor(generation = generationOf(query.page.cursor), offset = offset + pageItems.size)
            } else {
                null
            }
            return CapabilityPageResult(
                page = CapabilityPage(
                    items = pageItems.map { ExternalApiMappers.capability(it, provider.descriptor, now) },
                    nextCursor = nextCursor,
                    observedAtEpochMs = now,
                ),
                error = null,
            )
        }

        // Async device and shadow reads

        override fun listDevices(request: PageRequest?, callback: IDevicePageCallback?) {
            if (callback == null) return
            val (identity, rejection) = preflight(ApiScopes.DEVICES_READ, deviceId = null, meterRead = true)
                ?: run { callback.onError(handshakeRequiredError()); return }
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            val request = request ?: run { callback.onError(invalidInput("PageRequest is required")); return }
            runOneShot(identity, callback.asBinder(), callback::onError) {
                val provider = runtime.provider()
                val granted = runtime.engine.snapshot(identity).grantedDeviceIds
                val devices = provider.devices.listDevices().filter { it.device.id in granted }
                val offset = (request.cursor?.offset ?: 0).coerceIn(0, devices.size)
                val items = devices.drop(offset).take(request.limit.coerceIn(1, PAGE_LIMIT))
                val now = System.currentTimeMillis()
                val nextCursor = if (offset + items.size < devices.size) {
                    PageCursor(generation = generationOf(request.cursor), offset = offset + items.size)
                } else {
                    null
                }
                callback.onSuccess(
                    DevicePage(
                        items = items.map { ExternalApiMappers.device(it, provider.descriptor, now) },
                        nextCursor = nextCursor,
                        source = WireDataSource.LOCAL,
                        observedAtEpochMs = now,
                    ),
                )
            }
        }

        override fun getDevice(request: DeviceRequest?, callback: IDeviceCallback?) {
            if (callback == null) return
            val deviceId = request?.device?.deviceId
            val (identity, rejection) = preflight(ApiScopes.DEVICES_READ, deviceId, meterRead = true)
                ?: run { callback.onError(handshakeRequiredError()); return }
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            if (request == null || deviceId == null) {
                callback.onError(invalidInput("DeviceRequest is required"))
                return
            }
            runOneShot(identity, callback.asBinder(), callback::onError) {
                val provider = runtime.provider()
                val device = provider.devices.listDevices().firstOrNull { it.device.id == deviceId }
                    ?: throw ProviderError.NotFound("getDevice")
                callback.onSuccess(ExternalApiMappers.device(device, provider.descriptor, System.currentTimeMillis()))
            }
        }

        override fun getDeviceShadow(request: ShadowQuery?, callback: IShadowCallback?) {
            if (callback == null) return
            val deviceId = request?.device?.deviceId
            val (identity, rejection) = preflight(ApiScopes.SHADOW_READ, deviceId, meterRead = true)
                ?: run { callback.onError(handshakeRequiredError()); return }
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            if (request == null || deviceId == null) {
                callback.onError(invalidInput("ShadowQuery is required"))
                return
            }
            runOneShot(identity, callback.asBinder(), callback::onError) {
                val provider = runtime.provider()
                val shadow = provider.devices.getShadow(deviceId)
                callback.onSuccess(ExternalApiMappers.shadow(shadow, provider.descriptor, System.currentTimeMillis()))
            }
        }

        override fun getDeviceShadows(request: ShadowBatchQuery?, callback: IShadowBatchCallback?) {
            if (callback == null) return
            val identity = handshakeIdentity
                ?: run { callback.onError(handshakeRequiredError()); return }
            val request = request ?: run { callback.onError(invalidInput("ShadowBatchQuery is required")); return }
            val deviceIds = request.queries.mapNotNull { it.device.deviceId }.distinct()
            // One ungranted device rejects the whole batch; errors never become partial success.
            val ungranted = deviceIds.firstOrNull { deviceId ->
                runtime.engine.checkAccess(identity, ApiScopes.SHADOW_READ, deviceId).errorCode != null
            }
            if (ungranted != null) {
                callback.onError(notAuthorized(denyMessage(WireApiErrorCode.NOT_AUTHORIZED)))
                return
            }
            val rejection = meterRead()
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            runOneShot(identity, callback.asBinder(), callback::onError) {
                val provider = runtime.provider()
                val shadows = deviceIds.mapNotNull { deviceId ->
                    try {
                        ExternalApiMappers.shadow(provider.devices.getShadow(deviceId), provider.descriptor, System.currentTimeMillis())
                    } catch (error: ProviderError.NotFound) {
                        null
                    }
                }
                val found = shadows.map { it.device.deviceId }.toSet()
                callback.onSuccess(ShadowBatch(items = shadows, missingDeviceIds = deviceIds.filterNot { it in found }))
            }
        }

        // Events

        override fun subscribeEvents(request: EventSubscriptionRequest?, callback: IWiboxEventCallback?): EventSubscriptionResult {
            val (identity, rejection) = preflight(ApiScopes.EVENTS_READ, deviceId = null, meterRead = false)
                ?: return EventSubscriptionResult(snapshot = null, error = handshakeRequiredError())
            if (rejection != null) return EventSubscriptionResult(snapshot = null, error = rejection)
            if (subscriptions.size >= SUBSCRIPTIONS_PER_APP) {
                return EventSubscriptionResult(
                    snapshot = null,
                    error = ExternalApiMappers.apiError(
                        WireApiErrorCode.BUSY,
                        WireRetryPolicy.DO_NOT_RETRY,
                        "Too many event subscriptions for this app",
                    ),
                )
            }
            val gate = realtimeGate(runtime.provider())
            if (gate != null) return EventSubscriptionResult(snapshot = null, error = gate)
            // No provider exposes realtime in this build, so this line is unreachable today.
            if (callback != null) subscriptions[callback.asBinder()] = true
            return EventSubscriptionResult(
                snapshot = dev.nacho.wibox.externalapi.aidl.EventSubscriptionSnapshot(
                    state = dev.nacho.wibox.externalapi.aidl.EventConnectionState(
                        state = dev.nacho.wibox.externalapi.aidl.WireEventStreamState.CLOSED,
                        observedAtEpochMs = System.currentTimeMillis(),
                    ),
                    resumeCursor = null,
                    replayFromCache = false,
                    liveSupported = false,
                ),
                error = null,
            )
        }

        override fun unsubscribeEvents(callback: IWiboxEventCallback?) {
            val binder = callback?.asBinder() ?: return
            subscriptions.remove(binder)
        }

        // Actions

        override fun performAction(request: ActionRequest?, callback: IActionCallback?) {
            if (callback == null) return
            val deviceId = request?.device?.deviceId
            val actionType = request?.type ?: 0
            val scope = ApiScopes.forAction(actionType)
            val (identity, rejection) = preflight(scope, deviceId, meterRead = false)
                ?: run { callback.onError(handshakeRequiredError()); return }
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            if (request == null || deviceId == null) {
                callback.onError(invalidInput("ActionRequest is required"))
                return
            }
            if (scope == null) {
                callback.onError(invalidInput("Unknown action type"))
                return
            }
            if (request.expiresAtEpochMs in 1 until System.currentTimeMillis()) {
                callback.onError(invalidInput("Action request expired"))
                return
            }
            val parameters = request.parameters.associate { it.namespacedKey to parameterValueString(it.value) }
            val digest = runtime.actionPayloadDigest(actionType, deviceId, parameters)
            val existing = runtime.journal[request.requestId]
            if (existing != null) {
                if (existing.uid != identity.uid || existing.packageName != identity.packageName) {
                    callback.onError(notAuthorized(denyMessage(WireApiErrorCode.NOT_AUTHORIZED)))
                    return
                }
                if (existing.payloadDigest != digest) {
                    callback.onError(
                        ExternalApiMappers.apiError(
                            WireApiErrorCode.CONFLICT,
                            WireRetryPolicy.DO_NOT_RETRY,
                            "Request id was already used with a different payload",
                        ),
                    )
                    return
                }
                // Duplicate request id: deliver the pending operation or the recorded outcome, never re-execute.
                deliverCallback { callback.onOutcome(actionOutcomeWire(existing)) }
                return
            }
            val retryAfter = runtime.actionLimiter.tryAcquire()
            if (retryAfter != null) {
                callback.onError(
                    ExternalApiMappers.apiError(
                        WireApiErrorCode.RATE_LIMITED,
                        WireRetryPolicy.DO_NOT_RETRY,
                        "Action rate limit exceeded",
                        retryAfterMs = retryAfter,
                    ),
                )
                return
            }
            if (!runtime.acquireOneShotSlot(identity)) {
                callback.onError(
                    ExternalApiMappers.apiError(WireApiErrorCode.BUSY, WireRetryPolicy.DO_NOT_RETRY, "Too many pending operations"),
                )
                return
            }
            val entry = ExternalApiRuntime.JournalEntry(
                uid = identity.uid,
                packageName = identity.packageName,
                requestId = request.requestId,
                payloadDigest = digest,
                wireActionType = actionType,
                deviceId = deviceId,
                completion = CompletableDeferred(),
                state = ExternalApiRuntime.JournalState.QUEUED,
                outcome = null,
            )
            runtime.journal[request.requestId] = entry
            runtime.compactJournal()
            val job = runtime.ioScope.launch {
                try {
                    if (entry.state == ExternalApiRuntime.JournalState.CANCELLED) {
                        throw CancellationException("Cancelled before dispatch")
                    }
                    entry.state = ExternalApiRuntime.JournalState.DISPATCHED
                    val provider = runtime.provider()
                    val coreType = coreActionType(actionType)
                        ?: throw ProviderError.InvalidInput("performAction")
                    val outcome = provider.actions.execute(
                        ActionCommand(
                            commandId = request.requestId,
                            device = CoreDeviceRef(provider.descriptor.id, deviceId),
                            channel = request.device.channel ?: 0,
                            type = coreType,
                            parameters = parameters,
                        ),
                    )
                    entry.outcome = outcome
                    entry.state = ExternalApiRuntime.JournalState.DONE
                    entry.completion.complete(outcome)
                    deliverCallback {
                        callback.onOutcome(
                            ExternalApiMappers.actionOutcome(request.requestId, actionType, outcome, provider.descriptor, System.currentTimeMillis()),
                        )
                    }
                } catch (error: CancellationException) {
                    val dispatched = entry.state == ExternalApiRuntime.JournalState.DISPATCHED
                    if (!dispatched) entry.state = ExternalApiRuntime.JournalState.CANCELLED
                    deliverCallback {
                        callback.onError(
                            if (dispatched) {
                                ExternalApiMappers.apiError(
                                    WireApiErrorCode.OUTCOME_UNKNOWN,
                                    WireRetryPolicy.DO_NOT_RETRY,
                                    "Action was interrupted and its outcome is unknown",
                                )
                            } else {
                                ExternalApiMappers.apiError(
                                    WireApiErrorCode.CANCELLED,
                                    WireRetryPolicy.DO_NOT_RETRY,
                                    "Action was cancelled before dispatch",
                                )
                            },
                        )
                    }
                } catch (error: Throwable) {
                    deliverCallback { callback.onError(mapFailure(error)) }
                } finally {
                    runtime.releaseOneShotSlot(identity)
                }
            }
            linkToCallback(callback.asBinder(), job)
        }

        override fun cancelOperation(requestId: String?): CancellationResult {
            val identity = handshakeIdentity
                ?: return CancellationResult(requestId = requestId?.take(64) ?: "", state = WireCancellationState.NOT_FOUND, error = handshakeRequiredError())
            val requestId = requestId?.take(64)
                ?: return CancellationResult("", WireCancellationState.NOT_FOUND, invalidInput("requestId is required"))
            val entry = runtime.journal[requestId]
            return when {
                entry == null || entry.uid != identity.uid || entry.packageName != identity.packageName ->
                    CancellationResult(requestId, WireCancellationState.NOT_FOUND, null)
                entry.state == ExternalApiRuntime.JournalState.DONE ->
                    CancellationResult(requestId, WireCancellationState.ALREADY_COMPLETED, null)
                entry.state == ExternalApiRuntime.JournalState.CANCELLED ->
                    CancellationResult(requestId, WireCancellationState.CANCELLED_BEFORE_DISPATCH, null)
                entry.state == ExternalApiRuntime.JournalState.QUEUED -> {
                    entry.state = ExternalApiRuntime.JournalState.CANCELLED
                    entry.completion.cancel()
                    CancellationResult(requestId, WireCancellationState.CANCELLED_BEFORE_DISPATCH, null)
                }
                else -> CancellationResult(requestId, WireCancellationState.CANCELLATION_REQUESTED, null)
            }
        }

        override fun getActionOutcome(requestId: String?): ActionOutcomeResult {
            val identity = handshakeIdentity
                ?: return ActionOutcomeResult(outcome = null, error = handshakeRequiredError())
            val requestId = requestId?.take(64)
                ?: return ActionOutcomeResult(outcome = null, error = invalidInput("requestId is required"))
            val entry = runtime.journal[requestId]
            return when {
                entry == null || entry.uid != identity.uid || entry.packageName != identity.packageName ->
                    ActionOutcomeResult(outcome = null, error = notFound("Unknown request id"))
                entry.state == ExternalApiRuntime.JournalState.DONE && entry.outcome != null ->
                    ActionOutcomeResult(outcome = actionOutcomeWire(entry), error = null)
                entry.state == ExternalApiRuntime.JournalState.CANCELLED ->
                    ActionOutcomeResult(
                        outcome = null,
                        error = ExternalApiMappers.apiError(
                            WireApiErrorCode.OUTCOME_UNKNOWN,
                            WireRetryPolicy.DO_NOT_RETRY,
                            "Action was cancelled and its outcome is unknown",
                        ),
                    )
                else ->
                    ActionOutcomeResult(
                        outcome = null,
                        error = ExternalApiMappers.apiError(
                            WireApiErrorCode.BUSY,
                            WireRetryPolicy.SAFE_READ_RETRY,
                            "Action is still in flight",
                        ),
                    )
            }
        }

        // Upgrade check

        override fun checkAppUpgrade(request: UpgradeCheckRequest?, callback: IUpgradeCallback?) {
            if (callback == null) return
            val (identity, rejection) = preflight(ApiScopes.APP_UPGRADE_CHECK, deviceId = null, meterRead = true)
                ?: run { callback.onError(handshakeRequiredError()); return }
            if (rejection != null) {
                callback.onError(rejection)
                return
            }
            if (request == null) {
                callback.onError(invalidInput("UpgradeCheckRequest is required"))
                return
            }
            runOneShot(identity, callback.asBinder(), callback::onError) {
                // Wibox supplies its own version and configuration internally; the caller cannot inject them.
                val info = runtime.repository().checkAppUpgrade()
                callback.onSuccess(ExternalApiMappers.upgradeInfo(info, System.currentTimeMillis()))
            }
        }

        // Helpers

        /**
         * Handshake + grant + optional read rate limit, resolved inside the
         * Binder transaction. Returns null when the handshake has not
         * happened yet; otherwise the verified identity and an ApiError when
         * the call is rejected.
         */
        private fun preflight(scope: String?, deviceId: String?, meterRead: Boolean): Pair<CallerIdentity.Verified, ApiError?>? {
            val identity = handshakeIdentity ?: return null
            val errorCode = runtime.engine.checkAccess(identity, scope, deviceId).errorCode
            if (errorCode != null) return identity to notAuthorized(denyMessage(errorCode))
            if (meterRead) {
                val rejection = meterRead()
                if (rejection != null) return identity to rejection
            }
            return identity to null
        }

        private fun meterRead(): ApiError? {
            val retryAfter = runtime.readLimiter.tryAcquire() ?: return null
            return ExternalApiMappers.apiError(
                WireApiErrorCode.RATE_LIMITED,
                WireRetryPolicy.DO_NOT_RETRY,
                "Read rate limit exceeded",
                retryAfterMs = retryAfter,
            )
        }

        private fun runOneShot(
            identity: CallerIdentity.Verified,
            binder: IBinder,
            onError: (ApiError) -> Unit,
            block: suspend () -> Unit,
        ) {
            if (!runtime.acquireOneShotSlot(identity)) {
                onError(
                    ExternalApiMappers.apiError(WireApiErrorCode.BUSY, WireRetryPolicy.DO_NOT_RETRY, "Too many pending operations"),
                )
                return
            }
            val job = runtime.ioScope.launch {
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    deliverCallback { onError(mapFailure(error)) }
                } finally {
                    runtime.releaseOneShotSlot(identity)
                }
            }
            linkToCallback(binder, job)
        }

        private fun linkToCallback(binder: IBinder, job: Job) {
            val recipient = IBinder.DeathRecipient { job.cancel() }
            var linked = false
            try {
                binder.linkToDeath(recipient, 0)
                linked = true
            } catch (_: RemoteException) {
                job.cancel()
            }
            if (linked) {
                job.invokeOnCompletion { runCatching { binder.unlinkToDeath(recipient, 0) } }
            }
        }

        private fun deliverCallback(block: () -> Unit) {
            try {
                block()
            } catch (_: RemoteException) {
                // Callback died: the one-shot record is already terminal.
            }
        }

        private fun mapFailure(error: Throwable): ApiError = when (error) {
            is ProviderError -> ExternalApiMappers.providerError(error)
            else -> ExternalApiMappers.apiError(
                WireApiErrorCode.INTERNAL,
                WireRetryPolicy.DO_NOT_RETRY,
                "Wibox could not complete the operation",
            )
        }

        /**
         * Real-time events: no provider in this build exposes a cursor-accurate
         * realtime stream, so the gate rejects with UNSUPPORTED (or
         * CAPABILITY_UNKNOWN when the provider has no row) instead of faking
         * a connected stream.
         */
        private fun realtimeGate(provider: WiboxProvider): ApiError? {
            val capability = provider.capabilities.firstOrNull { it.key.id == "events.realtime" }
            val availability = capability?.let { ExternalApiMappers.availability(it, provider.descriptor) }
            return when {
                capability == null || availability == WireAvailability.UNKNOWN -> ExternalApiMappers.apiError(
                    WireApiErrorCode.CAPABILITY_UNKNOWN,
                    WireRetryPolicy.DO_NOT_RETRY,
                    "Real-time events capability is unknown for the current provider",
                    capabilityKey = "events.realtime",
                )
                availability == WireAvailability.AVAILABLE -> null
                else -> ExternalApiMappers.apiError(
                    WireApiErrorCode.UNSUPPORTED,
                    WireRetryPolicy.DO_NOT_RETRY,
                    "Real-time event streaming is not available on the current provider",
                    capabilityKey = "events.realtime",
                )
            }
        }

        private fun actionOutcomeWire(entry: ExternalApiRuntime.JournalEntry): WireActionOutcome {
            val descriptor = runtime.provider().descriptor
            val outcome: CoreActionOutcome? = entry.outcome
            return if (outcome != null) {
                ExternalApiMappers.actionOutcome(entry.requestId, entry.wireActionType, outcome, descriptor, System.currentTimeMillis())
            } else {
                WireActionOutcome(
                    requestId = entry.requestId,
                    actionType = entry.wireActionType,
                    device = dev.nacho.wibox.externalapi.aidl.DeviceRef(
                        providerId = descriptor.id.id,
                        deviceId = entry.deviceId,
                        channel = null,
                    ),
                    ackLevel = WireAckLevel.UNKNOWN_OUTCOME,
                    origin = ExternalApiMappers.providerOrigin(descriptor.connectionKind),
                    simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
                    occurredAtEpochMs = System.currentTimeMillis(),
                    providerCode = null,
                    observationEventId = null,
                )
            }
        }

        private fun coreActionType(wireType: Int): ActionType? = when (wireType) {
            WireActionType.UNLOCK -> ActionType.UNLOCK
            WireActionType.F1 -> ActionType.F1
            WireActionType.CALL_GUARD -> ActionType.CALL_GUARD
            WireActionType.CALL_ACCEPT -> ActionType.CALL_ACCEPT
            WireActionType.CALL_REJECT -> ActionType.CALL_REJECT
            WireActionType.START_CALL -> ActionType.START_CALL
            WireActionType.STOP_CALL -> ActionType.STOP_CALL
            else -> null
        }

        private fun parameterValueString(value: ProviderExtensionValue): String = when (value.type) {
            WireExtensionValueType.BOOLEAN -> value.booleanValue?.toString().orEmpty()
            WireExtensionValueType.INTEGER -> value.integerValue?.toString().orEmpty()
            WireExtensionValueType.DECIMAL -> value.decimalValue?.toString().orEmpty()
            WireExtensionValueType.STRING -> value.stringValue?.take(256).orEmpty()
            else -> ""
        }

        private fun generationOf(cursor: PageCursor?): Long = cursor?.generation ?: PAGE_GENERATION

        private fun namespaceFilter(keyId: String, namespace: String?): Boolean =
            namespace == null || keyId.startsWith(namespace)

        private fun handshakeRequiredError(): ApiError = ExternalApiMappers.apiError(
            WireApiErrorCode.HANDSHAKE_REQUIRED,
            WireRetryPolicy.RENEGOTIATE,
            "Handshake is required before this call",
        )

        private fun handshakeError(code: Int, message: String): HandshakeResult =
            HandshakeResult(
                serverApiMajor = WiboxExternalApi.WIRE_MAJOR,
                serverApiMinor = WiboxExternalApi.WIRE_MINOR,
                negotiatedApiMinor = 0,
                authorization = null,
                error = ExternalApiMappers.apiError(code, WireRetryPolicy.DO_NOT_RETRY, message),
            )

        private fun invalidInput(message: String): ApiError =
            ExternalApiMappers.apiError(WireApiErrorCode.INVALID_INPUT, WireRetryPolicy.DO_NOT_RETRY, message)

        private fun notAuthorized(message: String): ApiError =
            ExternalApiMappers.apiError(WireApiErrorCode.NOT_AUTHORIZED, WireRetryPolicy.DO_NOT_RETRY, message)

        private fun notFound(message: String): ApiError =
            ExternalApiMappers.apiError(WireApiErrorCode.NOT_FOUND, WireRetryPolicy.DO_NOT_RETRY, message)

        private fun denyMessage(errorCode: Int): String = when (errorCode) {
            WireApiErrorCode.AUTHORIZATION_PENDING -> "Wibox authorization is pending user approval"
            WireApiErrorCode.AUTHORIZATION_DENIED -> "Wibox access was denied by the user"
            WireApiErrorCode.AUTHORIZATION_REVOKED -> "Wibox access was revoked"
            WireApiErrorCode.SHARED_UID_UNSUPPORTED -> "Shared uid callers are not supported"
            WireApiErrorCode.PACKAGE_UID_MISMATCH -> "Package does not match the calling uid"
            WireApiErrorCode.SIGNATURE_CHANGED -> "App signature changed and requires re-approval"
            WireApiErrorCode.UID_CHANGED -> "App uid changed and requires re-approval"
            else -> "Wibox access has not been granted to this app"
        }
    }

    private companion object {
        const val PAGE_LIMIT = 20
        const val PAGE_GENERATION = 1L
        const val SUBSCRIPTIONS_PER_APP = 4
    }
}
