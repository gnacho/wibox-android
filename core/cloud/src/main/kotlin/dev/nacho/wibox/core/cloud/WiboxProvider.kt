package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.StockQvCloudProvider
import java.io.Closeable
import kotlinx.coroutines.flow.Flow

interface WiboxProvider : Closeable {
    val descriptor: ProviderDescriptor
    val capabilities: List<CapabilitySnapshot>
    val session: SessionPort
    val devices: DevicePort
    val events: EventPort
    val actions: ActionPort
    val maintenance: MaintenancePort

    suspend fun prepare(): PreparationState
}

interface SessionPort {
    suspend fun authenticate(material: CredentialMaterial): SessionHandle
    suspend fun restoreSession(): SessionHandle?
    suspend fun refreshSession(): SessionHandle
    suspend fun logout() {
        throw ProviderError.Unsupported("logout", CapabilityKey("auth.logout"))
    }
}

interface DevicePort {
    suspend fun getShadow(deviceId: String): ShadowSnapshot
    suspend fun getShadows(deviceIds: List<String>): List<ShadowSnapshot>
    suspend fun subscribeShadow(deviceIds: List<String>): ShadowSubscriptionAck
    suspend fun unsubscribeShadow(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowSubscriptionAck

    suspend fun listDevices(): List<DeviceSnapshot> {
        throw ProviderError.Unsupported("listDevices", CapabilityKey("devices.list"))
    }

    suspend fun discoverRegionalServices() {
        throw ProviderError.Unsupported("discoverRegionalServices", CapabilityKey("discovery.regional_services"))
    }
}

interface EventPort {
    fun parseAlarm(payload: Map<String, String>): EventEnvelope
    fun observeEvents(): Flow<EventStreamItem>
}

interface ActionPort {
    suspend fun execute(command: ActionCommand): ActionOutcome
}

interface MaintenancePort {
    suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo
}

object WiboxProviderFactory {
    @JvmStatic
    fun stockQvCloud(
        cloud: WiboxCloud,
        sessionContext: ProviderSessionContext,
        closeClientWhenClosed: Boolean = true,
    ): WiboxProvider = StockQvCloudProvider(cloud, sessionContext, closeClientWhenClosed)
}
