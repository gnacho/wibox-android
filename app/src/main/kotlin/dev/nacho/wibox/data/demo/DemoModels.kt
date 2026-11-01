package dev.nacho.wibox.data.demo

import kotlinx.serialization.Serializable
import dev.nacho.wibox.data.VideoMode
import dev.nacho.wibox.data.TalkMode

@Serializable
enum class DemoScenario {
    NORMAL,
    LOADING,
    EMPTY,
    ERROR,
    UNSUPPORTED,
    OFFLINE,
    PERMISSION,
    ACTION_PENDING,
    ACTION_FAILURE,
}

@Serializable
enum class DemoEventType {
    MOTION,
    INCOMING_CALL,
    CALL_ANSWERED,
}

@Serializable
enum class DemoMediaType {
    PHOTO,
    VIDEO,
}

@Serializable
data class DemoPermissions(
    val preview: Boolean = true,
    val playback: Boolean = true,
    val alarms: Boolean = true,
    val unlock: Boolean = true,
    val f1: Boolean = true,
    val callGuard: Boolean = true,
)

@Serializable
data class DemoDevice(
    val id: String,
    val name: String,
    val model: String,
    val online: Boolean,
    val shared: Boolean = false,
    val permissions: DemoPermissions = DemoPermissions(),
    val firmwareCurrent: String = "2.2-local",
    val firmwareLatest: String = "2.2-local",
    val callAlerts: Boolean = true,
    val motionAlerts: Boolean = true,
    val f1Seconds: Int = 3,
    val videoMode: VideoMode = VideoMode.HIGH,
    val talkMode: TalkMode = TalkMode.FULL_DUPLEX,
)

@Serializable
data class DemoEvent(
    val id: String,
    val deviceId: String,
    val type: DemoEventType,
    val timestampMillis: Long,
    val channel: Int = 1,
    val mediaId: String? = null,
)

@Serializable
data class DemoMedia(
    val id: String,
    val deviceId: String,
    val type: DemoMediaType,
    val timestampMillis: Long,
    val durationSeconds: Int = 0,
)

@Serializable
data class DemoInvitation(
    val id: String,
    val deviceId: String,
    val name: String,
    val code: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val permissions: DemoPermissions = DemoPermissions(),
)

@Serializable
data class DemoSettings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val quickUnlock: Boolean = false,
    val biometricUnlock: Boolean = false,
    val termsAccepted: Boolean = false,
    val androidHintDismissed: Boolean = false,
    val scenario: DemoScenario = DemoScenario.NORMAL,
)

@Serializable
data class DemoSnapshot(
    val schemaVersion: Int = 1,
    val devices: List<DemoDevice> = emptyList(),
    val events: List<DemoEvent> = emptyList(),
    val media: List<DemoMedia> = emptyList(),
    val invitations: List<DemoInvitation> = emptyList(),
    val settings: DemoSettings = DemoSettings(),
)

object DemoFixtures {
    const val BASE_TIME: Long = 1_793_462_400_000

    fun initial(): DemoSnapshot {
        val frontDoor = DemoDevice(
            id = "DEMO7410001",
            name = "Front door",
            model = "7410",
            online = true,
        )
        val garage = DemoDevice(
            id = "DEMO9440002",
            name = "Garage",
            model = "9440",
            online = false,
            shared = true,
            permissions = DemoPermissions(unlock = false, f1 = false),
        )
        val media = listOf(
            DemoMedia("media-001", frontDoor.id, DemoMediaType.PHOTO, BASE_TIME - 3_600_000),
            DemoMedia("media-002", frontDoor.id, DemoMediaType.VIDEO, BASE_TIME - 7_200_000, 12),
            DemoMedia("media-003", garage.id, DemoMediaType.PHOTO, BASE_TIME - 86_400_000),
            DemoMedia("media-004", frontDoor.id, DemoMediaType.PHOTO, BASE_TIME - 90_000_000),
            DemoMedia("media-005", frontDoor.id, DemoMediaType.VIDEO, BASE_TIME - 172_800_000, 8),
            DemoMedia("media-006", garage.id, DemoMediaType.PHOTO, BASE_TIME - 180_000_000),
        )
        val events = listOf(
            DemoEvent("event-001", frontDoor.id, DemoEventType.INCOMING_CALL, BASE_TIME - 3_600_000, mediaId = "media-001"),
            DemoEvent("event-002", frontDoor.id, DemoEventType.MOTION, BASE_TIME - 7_200_000, mediaId = "media-002"),
            DemoEvent("event-003", garage.id, DemoEventType.CALL_ANSWERED, BASE_TIME - 86_400_000, mediaId = "media-003"),
            DemoEvent("event-004", frontDoor.id, DemoEventType.MOTION, BASE_TIME - 90_000_000, mediaId = "media-004"),
            DemoEvent("event-005", frontDoor.id, DemoEventType.INCOMING_CALL, BASE_TIME - 172_800_000, mediaId = "media-005"),
            DemoEvent("event-006", garage.id, DemoEventType.MOTION, BASE_TIME - 180_000_000, mediaId = "media-006"),
        )
        return DemoSnapshot(
            devices = listOf(frontDoor, garage),
            events = events,
            media = media,
            settings = DemoSettings(termsAccepted = true, androidHintDismissed = true),
        )
    }
}
