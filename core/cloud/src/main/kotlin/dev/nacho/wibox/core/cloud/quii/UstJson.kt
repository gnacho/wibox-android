package dev.nacho.wibox.core.cloud.quii

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Helpers JSON compactos para los mensajes UST (broker MQTT): el formato de la casa
 * es JSON compacto con claves kebab-case tal cual se leen en quii-helper
 * (protocols/ust/message_builder.py + protocols/p2p/models).
 */
object UstJson {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    /**
     * Serializa de forma compacta un Map (String, Any?) aceptando String/Number/Boolean/
     * Map/List; orden de insercion conservado (LinkedHashMap).
     */
    fun dump(map: Map<String, Any?>): String = toElement(map).toString()

    fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), toElement(v)) }
        }
        is List<*> -> buildJsonArray { value.forEach { add(toElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }
}

/** Extensiones de lectura tolerante sobre JsonElement. */
fun JsonElement?.jsonObjectOrNull(): JsonObject? =
    (this as? JsonObject)?.takeIf { it.isNotEmpty() } ?: (this as? JsonObject)

fun JsonElement?.jsonStringOrNull(): String? = when (this) {
    is JsonPrimitive -> if (isString || content.isNotEmpty()) content else null
    else -> null
}

fun JsonElement?.jsonIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.jsonLongOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

fun JsonElement?.jsonListOrNull(): List<String>? =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }

/**
 * Algunos brokers envuelven el payload en {"d": "<json string>"} o similar; unwrap
 * un nivel si el JsonObject tiene una sola clave String que parsea como objeto.
 */
fun extractInnerJson(obj: JsonObject): JsonObject {
    if (obj.containsKey("header")) return obj
    for ((_, v) in obj) {
        val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        if (s.trimStart().startsWith("{")) {
            return try {
                UstJson.json.parseToJsonElement(s).jsonObject
            } catch (_: Exception) {
                obj
            }
        }
    }
    return obj
}

/** Wrappers de nombre usados por UstMqtt.kt. */
fun parseJson(text: String): JsonObject? = try {
    UstJson.json.parseToJsonElement(text) as? JsonObject
} catch (_: Exception) {
    null
}

fun dumpJson(map: Map<String, Any?>): String = UstJson.dump(map)

/**
 * Factory desde el content JSON de la respuesta p2pconnect (claves exactas del
 * codec/json_codec.py: devid/session-flag/resp-session-id/nettype/netsubtype/
 * pub-ip/pub-udpport/loc-udpport/loc-ip/utd-pub-ip/utd-pub-udpport).
 */
fun p2pConnectInfoFrom(obj: JsonObject): P2PConnectInfo {
    val result = obj["result"] as? JsonObject
    val code = result?.get("code") ?: obj["result"]
    return P2PConnectInfo(
        result = code?.jsonIntOrNull() ?: code?.jsonStringOrNull()?.toIntOrNull() ?: 0,
        resultMessage = (result?.get("message") ?: obj["result-message"])?.jsonStringOrNull(),
        deviceId = obj["devid"]?.jsonStringOrNull().orEmpty(),
        sessionFlag = obj["session-flag"]?.jsonStringOrNull(),
        responseSessionId = obj["resp-session-id"]?.jsonLongOrNull() ?: 0L,
        nettype = obj["nettype"]?.jsonIntOrNull() ?: 0,
        netSubtype = obj["netsubtype"]?.jsonIntOrNull() ?: 0,
        publicIp = obj["pub-ip"]?.jsonStringOrNull(),
        publicUdpPort = obj["pub-udpport"]?.jsonIntOrNull() ?: 0,
        localUdpPort = obj["loc-udpport"]?.jsonIntOrNull() ?: 0,
        localIps = obj["loc-ip"]?.jsonListOrNull().orEmpty(),
        utdPublicIp = obj["utd-pub-ip"]?.jsonStringOrNull(),
        utdPublicUdpPort = obj["utd-pub-udpport"]?.jsonIntOrNull() ?: 0,
    )
}

/** Hay targets de probe disponibles para este p2pconnect (peers/selection.py has_probe_targets). */
fun P2PConnectInfo.hasProbeTargets(): Boolean = P2PProbe.iterTargets(this).isNotEmpty()
