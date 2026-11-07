package dev.nacho.wibox.core.cloud

enum class CloudEventType {
    MOTION,
    INCOMING_CALL,
    CALL_ANSWERED,
}

data class CloudEvent(
    val type: CloudEventType,
    val alarmId: String?,
    val deviceId: String?,
    val channel: Int,
    val cameraName: String?,
    val unreadCount: Int,
    val alarmTime: String?,
    val unknownFields: Map<String, String> = emptyMap(),
)

enum class CloudCapability {
    OAUTH_PASSWORD,
    OAUTH_REFRESH,
    SHADOW_HTTP,
    OFFLINE_ALARM_PARSING,
    SERVICE_DISCOVERY,
    DEVICE_LIST,
    USER_MANAGEMENT,
    REALTIME_EVENTS,
    MEDIA_P2P,
    PHYSICAL_ACTIONS,
    APP_UPGRADE_CHECK,
}

data class CloudCapabilities(val supported: Set<CloudCapability>) {
    fun supports(capability: CloudCapability): Boolean = capability in supported
}

enum class CloudService {
    OAUTH,
    SHADOW_HTTP,
    MQTT,
    USER_AUTH,
    ALARM,
}

enum class CloudAction {
    UNLOCK,
    F1,
    CALL_GUARD,
    START_CALL,
    STOP_CALL,
}

data class CloudActionAcknowledgement(
    val providerCode: Int,
    val data: String?,
)
