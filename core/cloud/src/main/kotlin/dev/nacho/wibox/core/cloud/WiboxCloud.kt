package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.RealWiboxCloud
import java.io.Closeable
import kotlinx.coroutines.flow.Flow

interface WiboxCloud : Closeable {
    val capabilities: CloudCapabilities
    suspend fun authenticate(credentials: CloudCredentials): CloudSession
    suspend fun restoreSession(): CloudSession?
    suspend fun refreshSession(): CloudSession
    suspend fun getDeviceShadow(deviceId: String): DeviceShadow
    suspend fun getDeviceShadows(deviceIds: List<String>): List<DeviceShadow>
    suspend fun subscribeDeviceShadows(deviceIds: List<String>): ShadowAcknowledgement
    suspend fun unsubscribeDeviceShadows(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowAcknowledgement
    fun parseAlarmEvent(payload: Map<String, String>): CloudEvent
    fun observeEvents(): Flow<CloudEvent>
    suspend fun listDevices(): List<CloudDevice>
    suspend fun discoverServices(): Map<CloudService, String>
    suspend fun performAction(deviceId: String, action: CloudAction): CloudActionAcknowledgement
    suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo =
        throw CloudError.Unsupported("checkAppUpgrade", "This client does not implement app upgrade")
}

object WiboxCloudFactory {
    @JvmStatic
    fun create(
        config: WiboxCloudConfig,
        tokenStore: TokenStore,
        clock: CloudClock = CloudClock { System.currentTimeMillis() },
    ): WiboxCloud = RealWiboxCloud.createDefault(config, tokenStore, clock)
}
