package dev.nacho.wibox.core.cloud

data class CloudDevice(val id: String)

data class DeviceShadow(
    val device: CloudDevice,
    val state: DeviceState?,
    val path: String?,
    val timestampMillis: Long?,
    val unknownFields: Map<String, String> = emptyMap(),
    val responseUnknownFields: Map<String, String> = emptyMap(),
)

data class DeviceState(
    val f1State: Int?,
    val switchState: SwitchState?,
    val privateModeState: Int?,
    val fisheyeChannelState: Int?,
    val locks: List<LockState>?,
    val alarmZoneMode: Int?,
    val smartRelays: List<SmartRelayState>?,
    val unknownFields: Map<String, String> = emptyMap(),
)

data class SwitchState(
    val channel: Int,
    val index: Int,
    val state: Int,
    val type: Int,
    val unknownFields: Map<String, String> = emptyMap(),
)

data class LockState(
    val channel: Int,
    val lock: Int,
    val state: Int,
    val unknownFields: Map<String, String> = emptyMap(),
)

data class SmartRelayState(
    val code: String,
    val status: Int,
    val unknownFields: Map<String, String> = emptyMap(),
)

enum class ShadowUnsubscribeReason(internal val wireValue: Int) {
    ACCOUNT_LOGOUT(1),
    DEVICE_UNBIND(2),
}

data class ShadowAcknowledgement(
    val data: String?,
    val path: String,
    val timestampMillis: Long,
    val unknownFields: Map<String, String> = emptyMap(),
)
