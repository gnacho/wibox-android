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
    /**
     * @param deviceDirectory account channel that answers the device list. The
     *        stock cloud serves it over userauth, outside this HTTP client.
     * @param actionTransport transport of the physical actions (unlock, F1), which
     *        travel inside a live session.
     * @param alarmPayloads push payloads that carry alarm events.
     */
    @JvmStatic
    fun create(
        config: WiboxCloudConfig,
        tokenStore: TokenStore,
        clock: CloudClock = CloudClock { System.currentTimeMillis() },
        deviceDirectory: CloudDeviceDirectory? = null,
        actionTransport: CloudActionTransport? = null,
        alarmPayloads: CloudAlarmPayloads? = null,
    ): WiboxCloud = RealWiboxCloud.createDefault(
        config = config,
        tokenStore = tokenStore,
        clock = clock,
        deviceDirectory = deviceDirectory,
        actionTransport = actionTransport,
        alarmPayloads = alarmPayloads,
    )
}
