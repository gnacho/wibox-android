package dev.nacho.wibox.core.cloud

data class DeviceRef(
    val provider: ProviderId,
    val id: String,
)

data class ChannelRef(
    val device: DeviceRef,
    val channel: Int,
)

enum class DeviceRole {
    OWNER,
    SHARED,
    UNKNOWN,
}

enum class DevicePermission {
    PREVIEW,
    PLAYBACK,
    ALARM,
    UNLOCK,
    F1,
    CALL_GUARD,
}

enum class DevicePresence {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

enum class DeviceDataSource {
    CACHE,
    CLOUD,
    LOCAL,
    MERGED,
}

data class DeviceSnapshot(
    val device: DeviceRef,
    val displayName: String?,
    val declaredModel: String?,
    val role: DeviceRole = DeviceRole.UNKNOWN,
    val permissions: Set<DevicePermission> = emptySet(),
    val channels: List<ChannelRef> = emptyList(),
    val presence: DevicePresence = DevicePresence.UNKNOWN,
    val lastObservedAtMillis: Long? = null,
    val firmwareVersion: String? = null,
    val capabilities: List<CapabilitySnapshot> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val source: DeviceDataSource,
    val fresh: Boolean,
)

data class ShadowSnapshot(
    val device: DeviceRef,
    val requestedFields: Set<String>,
    val returnedFields: Set<String>,
    val state: DeviceState?,
    val path: String?,
    val timestampMillis: Long?,
    val sequence: Long? = null,
    val fresh: Boolean,
    val unknownFields: Map<String, String>,
    val responseUnknownFields: Map<String, String>,
)

data class ShadowSubscriptionAck(
    val data: String?,
    val path: String,
    val timestampMillis: Long,
    val unknownFields: Map<String, String>,
)
