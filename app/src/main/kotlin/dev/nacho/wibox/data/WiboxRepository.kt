package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.AppUpgradeRequest
import dev.nacho.wibox.core.cloud.CapabilityKey
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.WiboxProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WiboxRepository(
    val provider: WiboxProvider,
    private val operations: AppOperations,
    val actionCoordinator: ActionCoordinator,
    private val restore: suspend () -> Unit = {},
    private val appUpgradeRequest: (() -> AppUpgradeRequest)? = null,
) {
    private val initialization = Mutex()
    private var initialized = false
    val snapshots: StateFlow<RepositorySnapshot> = operations.snapshots
    val snapshot: RepositorySnapshot get() = snapshots.value

    suspend fun prepare(): PreparationState {
        initialization.withLock {
            if (!initialized) {
                restore()
                actionCoordinator.initialize()
                initialized = true
            }
        }
        return provider.prepare()
    }

    suspend fun execute(command: ActionCommand): ActionOutcome = actionCoordinator.execute(command)

    suspend fun refreshDevices() = operations.refreshDevices()
    suspend fun checkAppUpgrade() = provider.maintenance.checkAppUpgrade(
        appUpgradeRequest?.invoke() ?: throw ProviderError.Unsupported(
            "checkAppUpgrade",
            CapabilityKey("app.update_check"),
            "No real app upgrade configuration exists",
        ),
    )
    suspend fun discoverCandidates(): List<DiscoveredRecord> = operations.discoverCandidates()
    suspend fun bind(request: BindRequest) = operations.bind(request)
    suspend fun acceptInvitation(invitation: ParsedInvitation, displayName: String) = operations.acceptInvitation(invitation, displayName)
    suspend fun renameDevice(deviceId: String, displayName: String) = operations.renameDevice(deviceId, displayName)
    suspend fun updateDevice(deviceId: String, transform: (dev.nacho.wibox.core.cloud.DeviceSnapshot) -> dev.nacho.wibox.core.cloud.DeviceSnapshot) =
        operations.updateDevice(deviceId, transform)
    suspend fun deleteDevice(deviceId: String) = operations.deleteDevice(deviceId)
    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) = operations.updatePreferences(transform)
    suspend fun setScenario(scenario: AppScenario) = operations.setScenario(scenario)
    suspend fun captureMedia(deviceId: String, kind: MediaKind) = operations.captureMedia(deviceId, kind)
    suspend fun deleteEvents(ids: Set<String>) = operations.deleteEvents(ids)
    suspend fun deleteMedia(ids: Set<String>) = operations.deleteMedia(ids)
    suspend fun createInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) = operations.createInvitation(deviceId, displayName, permissions)
    suspend fun updateInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) = operations.updateInvitation(id, displayName, permissions)
    suspend fun deleteInvitation(id: String) = operations.deleteInvitation(id)
    suspend fun resetDemo() = operations.resetDemo()
    suspend fun emitIncomingCall(deviceId: String) = operations.emitIncomingCall(deviceId)

    fun close() {
        actionCoordinator.close()
        provider.close()
    }

}
