package dev.nacho.wibox.externalapi.sdk

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WiboxClient internal constructor(
    private val transport: ExternalApiTransport,
    private val packageName: String,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val handshakeComplete = AtomicBoolean(false)
    private val handshakeMutex = Mutex()

    suspend fun handshake(): Handshake = handshakeMutex.withLock {
        ensureOpen()
        val result = call { transport.handshake(clientHello().toWire()) }
        result.error?.let { throw WireApiException(it).toSdkException() }
        if (
            result.serverApiMajor != WiboxExternalApi.WIRE_MAJOR ||
            result.serverApiMinor < 0 ||
            result.negotiatedApiMinor != minOf(WiboxExternalApi.WIRE_MINOR, result.serverApiMinor)
        ) {
            throw sdkError(
                ApiErrorCode.VERSION_UNSUPPORTED,
                RetryPolicy.RENEGOTIATE,
                "Wibox wire version is incompatible",
            )
        }
        handshakeComplete.set(true)
        result.toSdk()
    }

    suspend fun requestAuthorization(request: AccessRequest): Authorization {
        ensureReady()
        val result = call { transport.requestAuthorization(request.toWire()) }
        return payload(result.authorization, result.error).toSdk()
    }

    suspend fun authorization(): Authorization {
        ensureReady()
        val result = call { transport.authorization() }
        return payload(result.authorization, result.error).toSdk()
    }

    suspend fun providerSnapshot(): ProviderSnapshot {
        ensureReady()
        val result = call { transport.providerSnapshot() }
        return payload(result.snapshot, result.error).toSdk()
    }

    suspend fun capabilities(query: CapabilityQuery): CapabilityPage {
        ensureReady()
        val result = call { transport.capabilities(query.toWire()) }
        return payload(result.page, result.error).toSdk()
    }

    suspend fun devices(page: PageRequest): DevicePage {
        ensureReady()
        return call { transport.devices(page.toWire()) }.toSdk()
    }

    suspend fun device(request: DeviceRequest): DeviceSnapshot {
        ensureReady()
        return call { transport.device(request.toWire()) }.toSdk()
    }

    suspend fun shadow(query: ShadowQuery): ShadowSnapshot {
        ensureReady()
        return call { transport.shadow(query.toWire()) }.toSdk()
    }

    suspend fun shadows(query: ShadowBatchQuery): ShadowBatch {
        ensureReady()
        return call { transport.shadows(query.toWire()) }.toSdk()
    }

    fun events(request: EventSubscriptionRequest): Flow<WiboxEventUpdate> {
        ensureReady()
        return transport.events(request.toWire()).map { update ->
            when (update) {
                is WireEventUpdate.Subscription -> WiboxEventUpdate.Subscription(update.snapshot.toSdk())
                is WireEventUpdate.Events -> WiboxEventUpdate.Events(update.batch.toSdk())
                is WireEventUpdate.ConnectionChanged -> WiboxEventUpdate.ConnectionChanged(update.state.toSdk())
                is WireEventUpdate.Gap -> WiboxEventUpdate.Gap(update.gap.toSdk())
                is WireEventUpdate.AuthorizationChanged -> WiboxEventUpdate.AuthorizationChanged(update.state.toSdk())
            }
        }.catch { error -> throw error.toSdkException() }
    }

    suspend fun performAction(request: ActionRequest): ActionOutcome {
        ensureReady()
        return try {
            call(action = true) { transport.performAction(request.toWire()) }.toSdk()
        } catch (error: CancellationException) {
            throw error
        }
    }

    suspend fun cancelOperation(requestId: String): CancellationResult {
        ensureReady()
        val result = call { transport.cancelOperation(requestId) }
        result.error?.let { throw WireApiException(it).toSdkException() }
        return result.toSdk()
    }

    suspend fun actionOutcome(requestId: String): ActionOutcome {
        ensureReady()
        val result = call { transport.actionOutcome(requestId) }
        return payload(result.outcome, result.error).toSdk()
    }

    suspend fun checkAppUpgrade(request: UpgradeCheckRequest): UpgradeInfo {
        ensureReady()
        return call { transport.checkAppUpgrade(request.toWire()) }.toSdk()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) transport.close()
    }

    private fun clientHello() = ClientHello(
        packageName = packageName,
        clientApiMajor = WiboxExternalApi.WIRE_MAJOR,
        clientApiMinor = WiboxExternalApi.WIRE_MINOR,
        clientSdkVersion = WiboxExternalApi.SDK_VERSION,
    )

    private suspend fun <T> call(action: Boolean = false, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw error.toSdkException(action)
    }

    private fun <T> payload(value: T?, error: dev.nacho.wibox.externalapi.aidl.ApiError?): T {
        error?.let { throw WireApiException(it).toSdkException() }
        return value ?: throw sdkError(ApiErrorCode.INTERNAL, RetryPolicy.DO_NOT_RETRY, "Wibox returned an invalid result")
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw sdkError(ApiErrorCode.CANCELLED, RetryPolicy.DO_NOT_RETRY, "Wibox client is closed")
        }
    }

    private fun ensureReady() {
        ensureOpen()
        if (!handshakeComplete.get()) {
            throw sdkError(
                ApiErrorCode.HANDSHAKE_REQUIRED,
                RetryPolicy.RENEGOTIATE,
                "Handshake is required before this call",
            )
        }
    }
}
