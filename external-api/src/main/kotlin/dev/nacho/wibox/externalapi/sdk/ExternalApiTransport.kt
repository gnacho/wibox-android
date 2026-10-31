package dev.nacho.wibox.externalapi.sdk

import android.os.BadParcelableException
import android.os.DeadObjectException
import android.os.RemoteException
import dev.nacho.wibox.externalapi.aidl.ActionOutcomeResult as WireActionOutcomeResult
import dev.nacho.wibox.externalapi.aidl.ActionRequest as WireActionRequest
import dev.nacho.wibox.externalapi.aidl.ActionOutcome as WireActionOutcome
import dev.nacho.wibox.externalapi.aidl.ApiError as WireApiError
import dev.nacho.wibox.externalapi.aidl.AuthorizationRequest as WireAuthorizationRequest
import dev.nacho.wibox.externalapi.aidl.AuthorizationResult as WireAuthorizationResult
import dev.nacho.wibox.externalapi.aidl.AuthorizationSnapshot as WireAuthorizationSnapshot
import dev.nacho.wibox.externalapi.aidl.CapabilityPageResult as WireCapabilityPageResult
import dev.nacho.wibox.externalapi.aidl.CapabilityQuery as WireCapabilityQuery
import dev.nacho.wibox.externalapi.aidl.CancellationResult as WireCancellationResult
import dev.nacho.wibox.externalapi.aidl.ClientHello as WireClientHello
import dev.nacho.wibox.externalapi.aidl.DevicePage as WireDevicePage
import dev.nacho.wibox.externalapi.aidl.DeviceRequest as WireDeviceRequest
import dev.nacho.wibox.externalapi.aidl.DeviceSnapshot as WireDeviceSnapshot
import dev.nacho.wibox.externalapi.aidl.EventBatch as WireEventBatch
import dev.nacho.wibox.externalapi.aidl.EventConnectionState as WireEventConnectionState
import dev.nacho.wibox.externalapi.aidl.EventGap as WireEventGap
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionRequest as WireEventSubscriptionRequest
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionSnapshot as WireEventSubscriptionSnapshot
import dev.nacho.wibox.externalapi.aidl.HandshakeResult as WireHandshakeResult
import dev.nacho.wibox.externalapi.aidl.PageRequest as WirePageRequest
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshotResult as WireProviderSnapshotResult
import dev.nacho.wibox.externalapi.aidl.ShadowBatch as WireShadowBatch
import dev.nacho.wibox.externalapi.aidl.ShadowBatchQuery as WireShadowBatchQuery
import dev.nacho.wibox.externalapi.aidl.ShadowQuery as WireShadowQuery
import dev.nacho.wibox.externalapi.aidl.ShadowSnapshot as WireShadowSnapshot
import dev.nacho.wibox.externalapi.aidl.UpgradeCheckRequest as WireUpgradeCheckRequest
import dev.nacho.wibox.externalapi.aidl.UpgradeInfo as WireUpgradeInfo
import dev.nacho.wibox.externalapi.aidl.WireAckLevel
import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireRetryPolicy
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal interface ExternalApiTransport : Closeable {
    suspend fun handshake(hello: WireClientHello): WireHandshakeResult
    suspend fun requestAuthorization(request: WireAuthorizationRequest): WireAuthorizationResult
    suspend fun authorization(): WireAuthorizationResult
    suspend fun providerSnapshot(): WireProviderSnapshotResult
    suspend fun capabilities(query: WireCapabilityQuery): WireCapabilityPageResult
    suspend fun devices(page: WirePageRequest): WireDevicePage
    suspend fun device(request: WireDeviceRequest): WireDeviceSnapshot
    suspend fun shadow(query: WireShadowQuery): WireShadowSnapshot
    suspend fun shadows(query: WireShadowBatchQuery): WireShadowBatch
    fun events(request: WireEventSubscriptionRequest): Flow<WireEventUpdate>
    suspend fun performAction(request: WireActionRequest): WireActionOutcome
    suspend fun cancelOperation(requestId: String): WireCancellationResult
    suspend fun actionOutcome(requestId: String): WireActionOutcomeResult
    suspend fun checkAppUpgrade(request: WireUpgradeCheckRequest): WireUpgradeInfo
}

internal sealed interface WireEventUpdate {
    data class Subscription(val snapshot: WireEventSubscriptionSnapshot) : WireEventUpdate
    data class Events(val batch: WireEventBatch) : WireEventUpdate
    data class ConnectionChanged(val state: WireEventConnectionState) : WireEventUpdate
    data class Gap(val gap: WireEventGap) : WireEventUpdate
    data class AuthorizationChanged(val state: WireAuthorizationSnapshot) : WireEventUpdate
}

internal class WireApiException(val error: WireApiError) : Exception(error.safeMessage ?: "Wibox API error")

internal fun wireError(
    code: Int,
    retryPolicy: Int,
    message: String,
    outcome: Int? = null,
): WireApiError = WireApiError(
    code = code,
    retryPolicy = retryPolicy,
    capabilityKey = null,
    outcome = outcome,
    safeMessage = message,
    retryAfterMs = null,
    providerCode = null,
)

internal fun serviceDiedError(action: Boolean = false): WireApiException = WireApiException(
    wireError(
        code = WireApiErrorCode.SERVICE_DIED,
        retryPolicy = WireRetryPolicy.MANUAL_RETRY,
        message = "Wibox service disconnected",
        outcome = if (action) WireAckLevel.UNKNOWN_OUTCOME else null,
    ),
)

internal fun cancelledError(): WireApiException = WireApiException(
    wireError(WireApiErrorCode.CANCELLED, WireRetryPolicy.DO_NOT_RETRY, "Wibox client is closed"),
)

internal fun Throwable.asWireApiException(action: Boolean = false): WireApiException {
    if (this is WireApiException) return this
    if (this is CancellationException) throw this
    return when (this) {
        is DeadObjectException, is RemoteException -> serviceDiedError(action)
        is SecurityException -> WireApiException(
            wireError(WireApiErrorCode.NOT_AUTHORIZED, WireRetryPolicy.DO_NOT_RETRY, "Wibox access was denied"),
        )
        is BadParcelableException, is IllegalArgumentException -> WireApiException(
            wireError(WireApiErrorCode.INVALID_INPUT, WireRetryPolicy.DO_NOT_RETRY, "Wibox rejected the request"),
        )
        else -> WireApiException(
            wireError(WireApiErrorCode.INTERNAL, WireRetryPolicy.DO_NOT_RETRY, "Wibox API call failed"),
        )
    }
}

internal fun WireApiException.toSdkException(): WiboxApiException = WiboxApiException(error.toSdk())

internal fun Throwable.toSdkException(action: Boolean = false): WiboxApiException =
    asWireApiException(action).toSdkException()
