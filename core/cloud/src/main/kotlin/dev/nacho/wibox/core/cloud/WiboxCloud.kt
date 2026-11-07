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

    /**
     * People the device is shared with (userauth share groups). The stock provider has
     * no accounts of its own, so this list is what its "Users" screen shows. Clients
     * without the account channel wired answer Unsupported instead of an empty list
     * that would read like "nobody has access".
     */
    suspend fun listUsers(deviceId: String): List<CloudUserInvitation> =
        throw CloudError.Unsupported("listUsers", "This client does not manage device users")

    suspend fun createUserInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): CloudUserInvitation =
        throw CloudError.Unsupported("createUserInvitation", "This client does not manage device users")

    suspend fun updateUserInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) {
        throw CloudError.Unsupported("updateUserInvitation", "This client does not manage device users")
    }

    suspend fun revokeUserInvitation(id: String) {
        throw CloudError.Unsupported("revokeUserInvitation", "This client does not manage device users")
    }
}

object WiboxCloudFactory {
    /**
     * @param deviceDirectory account channel that answers the device list. The
     *        stock cloud serves it over userauth, outside this HTTP client.
     * @param actionTransport transport of the physical actions (unlock, F1), which
     *        travel inside a live session.
     * @param alarmPayloads push payloads that carry alarm events.
     * @param userAdministration account channel that manages the people a device is
     *        shared with (userauth share groups), which lives outside this HTTP client.
     */
    @JvmStatic
    fun create(
        config: WiboxCloudConfig,
        tokenStore: TokenStore,
        clock: CloudClock = CloudClock { System.currentTimeMillis() },
        deviceDirectory: CloudDeviceDirectory? = null,
        actionTransport: CloudActionTransport? = null,
        alarmPayloads: CloudAlarmPayloads? = null,
        userAdministration: CloudUserAdministration? = null,
    ): WiboxCloud = RealWiboxCloud.createDefault(
        config = config,
        tokenStore = tokenStore,
        clock = clock,
        deviceDirectory = deviceDirectory,
        actionTransport = actionTransport,
        alarmPayloads = alarmPayloads,
        userAdministration = userAdministration,
    )
}
