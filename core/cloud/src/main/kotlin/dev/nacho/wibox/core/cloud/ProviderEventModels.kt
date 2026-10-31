package dev.nacho.wibox.core.cloud

enum class ProviderEventType {
    MOTION,
    INCOMING_CALL,
    CALL_ANSWERED,
    UNKNOWN,
}

enum class EventSource {
    FCM,
    MQTT,
    HTTP,
    LOCAL,
}

data class EventAttachmentRef(val id: String)

data class EventCursor(
    val provider: ProviderId,
    val value: String,
) {
    override fun toString(): String = "EventCursor(provider=$provider, value=<redacted>)"
}

data class EventEnvelope(
    val eventId: String?,
    val provider: ProviderId,
    val type: ProviderEventType,
    val device: DeviceRef?,
    val channel: Int,
    val cameraName: String?,
    val unreadCount: Int,
    val occurredAtRaw: String?,
    val receivedAtMillis: Long?,
    val attachments: List<EventAttachmentRef>,
    val unknownFields: Map<String, String>,
    val cursor: EventCursor?,
    val source: EventSource,
    val duplicate: Boolean,
)

enum class EventGapReason {
    OVERFLOW,
    CURSOR_INVALID,
    DISCONNECTED,
    RESYNC_REQUIRED,
}

sealed interface EventStreamItem {
    data class Connected(val provider: ProviderId) : EventStreamItem
    data class Reconnecting(val provider: ProviderId) : EventStreamItem
    data class ResyncRequired(val cursor: EventCursor?) : EventStreamItem
    data class Gap(val cursor: EventCursor?, val reason: EventGapReason) : EventStreamItem
    data class Envelope(val event: EventEnvelope) : EventStreamItem
}
