package dev.nacho.wibox.data

enum class CodeParseError {
    EMPTY,
    DEVICE_FIELD_COUNT,
    INVALID_AP_NAME,
    INVALID_UID,
    INVALID_AUTH_CODE,
    INVALID_MODEL,
    INVALID_INVITATION,
    MISSING_INVITATION_FIELD,
    DUPLICATE_INVITATION_FIELD,
    UNSUPPORTED_OEM,
    UNSUPPORTED_APP,
}

class CodeParseException(val reason: CodeParseError) : IllegalArgumentException(reason.name)

data class ParsedDeviceQr(
    val apName: String,
    val uid: String,
    val authCode: String,
    val model: String,
    val ssid: String? = null,
    val mac: String? = null,
)

data class ParsedInvitation(
    val inviteCode: String,
    val ownerId: String,
    val oemId: String,
    val appVersion: String,
    val deviceId: String,
    val from: String,
    val appId: Int,
)

sealed interface ParsedCode {
    data class Device(val value: ParsedDeviceQr) : ParsedCode
    data class Invitation(val value: ParsedInvitation) : ParsedCode
}

object DeviceCodeParser {
    private val uidPattern = Regex("[A-Za-z0-9_-]{4,64}")
    private val authPattern = Regex("[A-Za-z0-9_-]{4,64}")
    private val apPattern = Regex("[A-Za-z0-9_.:-]{1,64}")
    private val modelPattern = Regex("[A-Za-z0-9_.-]{1,32}")
    private val ssidPattern = Regex("[A-Za-z0-9_.:-]{1,32}")
    private val macPattern = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}|[0-9A-Fa-f]{12}")
    // AppVersion is not part of the invitation the stock app prints, so it stays optional.
    private val requiredInvitationFields = listOf(
        "InviteCode",
        "OwnerId",
        "OemId",
        "DeviceId",
        "From",
        "AppId",
    )

    fun parse(raw: String): ParsedCode {
        val input = raw.trim()
        if (input.isEmpty()) throw CodeParseException(CodeParseError.EMPTY)
        return if (input.contains("InviteCode=")) {
            ParsedCode.Invitation(parseInvitation(input))
        } else {
            ParsedCode.Device(parseDeviceQr(input))
        }
    }

    fun parseDeviceQr(raw: String): ParsedDeviceQr {
        val fields = raw.trim().split(Regex("\\s+"))
        return when (fields.size) {
            4 -> {
                val (apName, uid, authCode, model) = fields
                validateDeviceCore(apName, uid, authCode)
                if (!modelPattern.matches(model)) throw CodeParseException(CodeParseError.INVALID_MODEL)
                ParsedDeviceQr(apName, uid, authCode, model)
            }
            5 -> {
                val (apName, uid, authCode, ssid, mac) = fields
                validateDeviceCore(apName, uid, authCode)
                if (!ssidPattern.matches(ssid)) throw CodeParseException(CodeParseError.INVALID_MODEL)
                if (!macPattern.matches(mac)) throw CodeParseException(CodeParseError.INVALID_MODEL)
                ParsedDeviceQr(apName, uid, authCode, model = "", ssid = ssid, mac = mac)
            }
            else -> throw CodeParseException(CodeParseError.DEVICE_FIELD_COUNT)
        }
    }

    private fun validateDeviceCore(apName: String, uid: String, authCode: String) {
        if (!apPattern.matches(apName)) throw CodeParseException(CodeParseError.INVALID_AP_NAME)
        if (!uidPattern.matches(uid)) throw CodeParseException(CodeParseError.INVALID_UID)
        if (!authPattern.matches(authCode)) throw CodeParseException(CodeParseError.INVALID_AUTH_CODE)
    }

    fun parseInvitation(raw: String): ParsedInvitation {
        val withoutFragment = raw.trim()
        val query = withoutFragment.substringAfter('?', withoutFragment)
        if (query.isBlank() || !query.contains('=')) {
            throw CodeParseException(CodeParseError.INVALID_INVITATION)
        }
        val values = linkedMapOf<String, String>()
        query.split('&').forEach { field ->
            if (field.isBlank()) return@forEach
            val key = field.substringBefore('=')
            val rawValue = field.substringAfter('=', "")
            if (key in requiredInvitationFields && key in values) {
                throw CodeParseException(CodeParseError.DUPLICATE_INVITATION_FIELD)
            }
            values[key] = percentDecode(rawValue)
        }
        requiredInvitationFields.forEach { key ->
            if (values[key].isNullOrBlank()) {
                throw CodeParseException(CodeParseError.MISSING_INVITATION_FIELD)
            }
        }
        val oemId = values.getValue("OemId")
        if (oemId != "G0021") throw CodeParseException(CodeParseError.UNSUPPORTED_OEM)
        val appId = values.getValue("AppId").toIntOrNull()
            ?: throw CodeParseException(CodeParseError.INVALID_INVITATION)
        if (appId != 2021) throw CodeParseException(CodeParseError.UNSUPPORTED_APP)
        return ParsedInvitation(
            inviteCode = values.getValue("InviteCode"),
            ownerId = values.getValue("OwnerId"),
            oemId = oemId,
            appVersion = values["AppVersion"].orEmpty(),
            deviceId = values.getValue("DeviceId"),
            from = values.getValue("From"),
            appId = appId,
        )
    }

    private fun percentDecode(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            when (val char = value[index]) {
                '+' -> {
                    bytes.add(' '.code.toByte())
                    index += 1
                }
                '%' -> {
                    if (index + 2 >= value.length) {
                        throw CodeParseException(CodeParseError.INVALID_INVITATION)
                    }
                    val hex = value.substring(index + 1, index + 3)
                    val decoded = hex.toIntOrNull(16)
                        ?: throw CodeParseException(CodeParseError.INVALID_INVITATION)
                    bytes.add(decoded.toByte())
                    index += 3
                }
                else -> {
                    val codePoint = value.codePointAt(index)
                    val encoded = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                    encoded.forEach(bytes::add)
                    index += Character.charCount(codePoint)
                }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}

fun ParsedInvitation.toLocalUrl(baseUrl: String = "https://example.invalid/invite"): String {
    fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val char = unsigned.toChar()
            val unreserved = unsigned in 'A'.code..'Z'.code ||
                unsigned in 'a'.code..'z'.code ||
                unsigned in '0'.code..'9'.code ||
                char in "-_.~"
            if (unreserved) {
                append(char)
            } else {
                append('%')
                append(unsigned.toString(16).padStart(2, '0').uppercase())
            }
        }
    }
    return baseUrl +
        "?InviteCode=${encode(inviteCode)}" +
        "&OwnerId=${encode(ownerId)}" +
        "&OemId=${encode(oemId)}" +
        "&AppVersion=${encode(appVersion)}" +
        "&DeviceId=${encode(deviceId)}" +
        "&From=${encode(from)}" +
        "&AppId=$appId"
}
