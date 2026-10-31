package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.CloudEvent
import dev.nacho.wibox.core.cloud.CloudEventType

internal object AlarmEventParser {
    private val knownFields = setOf(
        "AlarmId", "DevUmid", "SensorChNo", "CameraName",
        "NotReadCount", "AlarmEvent", "AlarmTime",
    )

    fun parse(payload: Map<String, String>): CloudEvent {
        val operation = "parseAlarmEvent"
        val eventCode = payload["AlarmEvent"]?.toIntOrNull() ?: throw CloudError.Parse(operation)
        val unread = payload["NotReadCount"]?.toIntOrNull() ?: throw CloudError.Parse(operation)
        val type = when (eventCode) {
            2 -> CloudEventType.MOTION
            19 -> CloudEventType.INCOMING_CALL
            20 -> CloudEventType.CALL_ANSWERED
            else -> throw CloudError.Unsupported(operation, "Alarm event code $eventCode is not documented")
        }
        return CloudEvent(
            type = type,
            alarmId = payload["AlarmId"],
            deviceId = payload["DevUmid"],
            channel = payload["SensorChNo"]?.toIntOrNull() ?: -1,
            cameraName = payload["CameraName"],
            unreadCount = unread,
            alarmTime = payload["AlarmTime"],
            unknownFields = payload.filterKeys { it !in knownFields },
        )
    }
}
