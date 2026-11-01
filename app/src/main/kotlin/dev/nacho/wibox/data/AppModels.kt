package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.EventEnvelope
import dev.nacho.wibox.core.cloud.ProviderError

enum class AppScenario {
    NORMAL,
    LOADING,
    EMPTY,
    ERROR,
    OFFLINE,
    UNSUPPORTED,
    PERMISSION,
    ACTION_PENDING,
    ACTION_FAILURE,
}

enum class MediaKind {
    PHOTO,
    VIDEO,
}

data class MediaRecord(
    val id: String,
    val deviceId: String,
    val kind: MediaKind,
    val timestampMillis: Long,
    val durationSeconds: Int = 0,
    val synthetic: Boolean = true,
)

data class InvitationRecord(
    val id: String,
    val deviceId: String,
    val displayName: String,
    val code: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val permissions: Set<DevicePermission> = DevicePermission.entries.toSet(),
)

enum class VideoMode {
    HIGH,
    LOW,
    PICTURE,
}

enum class TalkMode {
    FULL_DUPLEX,
    HOLD_TO_TALK,
}

data class AppPreferences(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val quickUnlock: Boolean = false,
    val biometricUnlock: Boolean = false,
    val termsAccepted: Boolean = false,
    val androidHintDismissed: Boolean = false,
    val scenario: AppScenario = AppScenario.NORMAL,
)

data class RepositorySnapshot(
    val devices: List<DeviceSnapshot> = emptyList(),
    val events: List<EventEnvelope> = emptyList(),
    val media: List<MediaRecord> = emptyList(),
    val invitations: List<InvitationRecord> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
)

data class DiscoveredRecord(val id: String, val declaredModel: String)

data class BindRequest(
    val uid: String,
    val displayName: String,
    val declaredModel: String,
    val shared: Boolean = false,
)

data class ActionJournalEntry(
    val command: ActionCommand,
    val outcome: ActionOutcome?,
    val pending: Boolean,
    val error: ProviderError? = null,
)

interface AppOperations {
    val snapshots: kotlinx.coroutines.flow.StateFlow<RepositorySnapshot>
    suspend fun replaceSnapshot(snapshot: RepositorySnapshot)
    suspend fun refreshDevices()
    suspend fun discoverCandidates(): List<DiscoveredRecord>
    suspend fun bind(request: BindRequest): DeviceSnapshot
    suspend fun acceptInvitation(invitation: ParsedInvitation, displayName: String): DeviceSnapshot
    suspend fun renameDevice(deviceId: String, displayName: String): DeviceSnapshot
    suspend fun updateDevice(deviceId: String, transform: (DeviceSnapshot) -> DeviceSnapshot): DeviceSnapshot
    suspend fun deleteDevice(deviceId: String)
    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences)
    suspend fun setScenario(scenario: AppScenario)
    suspend fun captureMedia(deviceId: String, kind: MediaKind): MediaRecord
    suspend fun deleteEvents(ids: Set<String>)
    suspend fun deleteMedia(ids: Set<String>)
    suspend fun createInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord
    suspend fun updateInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord
    suspend fun deleteInvitation(id: String)
    suspend fun resetDemo()
    suspend fun emitIncomingCall(deviceId: String)
}
