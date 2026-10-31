package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.CloudDevice
import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.DeviceShadow
import dev.nacho.wibox.core.cloud.DeviceState
import dev.nacho.wibox.core.cloud.LockState
import dev.nacho.wibox.core.cloud.ShadowAcknowledgement
import dev.nacho.wibox.core.cloud.SmartRelayState
import dev.nacho.wibox.core.cloud.SwitchState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class DeviceFieldDto(val thingId: String, val fieldNames: List<String>)

@Serializable
internal data class ShadowSubscribeRequestDto(
    val appId: String,
    val clientId: String,
    val subData: List<DeviceFieldDto>,
)

@Serializable
internal data class DeviceUnsubscribeFieldDto(
    val thingId: String,
    val doType: Int,
    val fieldNames: List<String>,
)

@Serializable
internal data class ShadowUnsubscribeRequestDto(
    val appId: String,
    val clientId: String,
    val subData: List<DeviceUnsubscribeFieldDto>,
)

@Serializable
internal data class ShadowStatusResponseDto(
    val code: Int? = null,
    val data: ShadowDataDto? = null,
    val path: String? = null,
    val timestamp: Long? = null,
)

@Serializable
internal data class ShadowMultiStatusResponseDto(
    val code: Int? = null,
    val data: List<ShadowDataDto>? = null,
    val path: String? = null,
    val timestamp: Long? = null,
)

@Serializable
internal data class ShadowDataDto(
    val fields: ShadowFieldsDto? = null,
    val thingId: String? = null,
)

@Serializable
internal data class ShadowFieldsDto(val Device_state: DeviceStateDto? = null)

@Serializable
internal data class DeviceStateDto(
    val Alarm_zone_mode: Int? = null,
    val F1_state: Int? = null,
    val Fisheye_chn_state: Int? = null,
    val Lock_state: List<LockStateDto>? = null,
    val Private_mode_state: Int? = null,
    val Smart_relay: List<SmartRelayStateDto>? = null,
    val Switch_state: SwitchStateDto? = null,
)

@Serializable
internal data class LockStateDto(
    val channel: Int? = null,
    val lock: Int? = null,
    val state: Int? = null,
)

@Serializable
internal data class SwitchStateDto(
    val channel: Int? = null,
    val index: Int? = null,
    val state: Int? = null,
    val type: Int? = null,
)

@Serializable
internal data class SmartRelayStateDto(
    val code: String? = null,
    val status: Int? = null,
)

@Serializable
internal data class ShadowAcknowledgementDto(
    val code: Int? = null,
    val data: String? = null,
    val path: String? = null,
    val timestamp: Long? = null,
)

internal object ShadowWire {
    private val RESPONSE_FIELDS = setOf("code", "data", "errTip", "msg", "path", "timestamp")

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun status(body: String, operation: String): DeviceShadow {
        val dto = decode<ShadowStatusResponseDto>(body, operation)
        checkCode(dto.code, operation)
        val root = root(body, operation)
        val data = dto.data ?: throw CloudError.Parse(operation)
        return deviceShadow(
            data,
            dto.path,
            dto.timestamp,
            root["data"] as? JsonObject,
            root.unknown(RESPONSE_FIELDS),
            operation,
        )
    }

    fun multiStatus(body: String, operation: String): List<DeviceShadow> {
        val dto = decode<ShadowMultiStatusResponseDto>(body, operation)
        checkCode(dto.code, operation)
        val root = root(body, operation)
        val rawData = root["data"] as? JsonArray
        val data = dto.data ?: throw CloudError.Parse(operation)
        if (rawData == null || rawData.size != data.size) throw CloudError.Parse(operation)
        return data.mapIndexed { index, item ->
            deviceShadow(
                item,
                dto.path,
                dto.timestamp,
                rawData[index] as? JsonObject,
                root.unknown(RESPONSE_FIELDS),
                operation,
            )
        }
    }

    fun acknowledgement(body: String, operation: String): ShadowAcknowledgement {
        val dto = decode<ShadowAcknowledgementDto>(body, operation)
        checkCode(dto.code, operation)
        val path = dto.path ?: throw CloudError.Parse(operation)
        val timestamp = dto.timestamp ?: throw CloudError.Parse(operation)
        val known = setOf("code", "data", "errTip", "msg", "path", "timestamp")
        return ShadowAcknowledgement(dto.data, path, timestamp, root(body, operation).unknown(known))
    }

    private fun deviceShadow(
        data: ShadowDataDto,
        path: String?,
        timestamp: Long?,
        rawData: JsonObject?,
        responseUnknownFields: Map<String, String>,
        operation: String,
    ): DeviceShadow {
        val thingId = data.thingId?.takeIf { it.isNotBlank() } ?: throw CloudError.Parse(operation)
        val fields = data.fields ?: throw CloudError.Parse(operation)
        val rawFields = rawData?.get("fields") as? JsonObject
        val rawState = rawFields?.get("Device_state") as? JsonObject
        val unknown = buildMap {
            putAll(rawData.unknown(setOf("thingId", "fields")))
            rawFields.unknown(setOf("Device_state")).forEach { (key, value) ->
                put("fields.$key", value)
            }
        }
        return DeviceShadow(
            device = CloudDevice(thingId),
            state = fields.Device_state?.toDomain(rawState, operation),
            path = path,
            timestampMillis = timestamp,
            unknownFields = unknown,
            responseUnknownFields = responseUnknownFields,
        )
    }

    private fun DeviceStateDto.toDomain(raw: JsonObject?, operation: String): DeviceState {
        val rawSwitch = raw?.get("Switch_state") as? JsonObject
        val rawLocks = raw?.get("Lock_state") as? JsonArray
        val rawRelays = raw?.get("Smart_relay") as? JsonArray
        return DeviceState(
            f1State = F1_state,
            switchState = Switch_state?.let { switch ->
                SwitchState(
                    switch.channel ?: throw CloudError.Parse(operation),
                    switch.index ?: throw CloudError.Parse(operation),
                    switch.state ?: throw CloudError.Parse(operation),
                    switch.type ?: throw CloudError.Parse(operation),
                    rawSwitch.unknown(setOf("channel", "index", "state", "type")),
                )
            },
            privateModeState = Private_mode_state,
            fisheyeChannelState = Fisheye_chn_state,
            locks = Lock_state?.mapIndexed { index, lock ->
                LockState(
                    lock.channel ?: throw CloudError.Parse(operation),
                    lock.lock ?: throw CloudError.Parse(operation),
                    lock.state ?: throw CloudError.Parse(operation),
                    (rawLocks?.getOrNull(index) as? JsonObject).unknown(setOf("channel", "lock", "state")),
                )
            },
            alarmZoneMode = Alarm_zone_mode,
            smartRelays = Smart_relay?.mapIndexed { index, relay ->
                SmartRelayState(
                    relay.code ?: throw CloudError.Parse(operation),
                    relay.status ?: throw CloudError.Parse(operation),
                    (rawRelays?.getOrNull(index) as? JsonObject).unknown(setOf("code", "status")),
                )
            },
            unknownFields = raw.unknown(
                setOf(
                    "Alarm_zone_mode", "F1_state", "Fisheye_chn_state", "Lock_state",
                    "Private_mode_state", "Smart_relay", "Switch_state",
                ),
            ),
        )
    }

    private fun checkCode(code: Int?, operation: String) {
        when (code) {
            null -> throw CloudError.Parse(operation)
            0 -> Unit
            else -> throw CloudError.Provider(operation, code)
        }
    }

    private inline fun <reified T> decode(body: String, operation: String): T = try {
        json.decodeFromString<T>(body)
    } catch (_: Exception) {
        throw CloudError.Parse(operation)
    }

    private fun root(body: String, operation: String): JsonObject = try {
        json.parseToJsonElement(body) as? JsonObject ?: throw CloudError.Parse(operation)
    } catch (_: Exception) {
        throw CloudError.Parse(operation)
    }

    private fun JsonObject?.unknown(known: Set<String>): Map<String, String> =
        this.orEmpty().filterKeys { it !in known }.mapValues { it.value.toString() }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}
