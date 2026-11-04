package dev.nacho.wibox.core.cloud

/** Resultado de aceptar una invitación de compartición (accept-nologin-share-invitation). */
data class ShareAcceptance(
    val account: String?,
    val accountId: String?,
    val name: String?,
    val dataEncodeKey: String?,
    val dynamicPassword: String?,
    val passwordExpired: String?,
    val powers: String?,
)

/** Error del canal userauth: result != 0 en el envelope de respuesta. */
class UserAuthException(
    val command: String,
    val result: Int,
    val responseContent: Map<String, String> = emptyMap(),
    message: String = "userauth command '$command' failed with result=$result",
) : Exception(message)

/** El comando no recibio respuesta por el canal down dentro del timeout. */
class UserAuthTimeoutException(
    val command: String,
    message: String = "userauth command '$command' timed out without down response",
) : Exception(message)

/** Datos de una invitación QR de compartición (extraídos del QR / DeviceCodeParser). */
data class ShareInvitation(
    val deviceId: String,
    val inviteCode: String,
    val ownerId: String,
    val oemId: String = "G0021",
    val appId: Int = 2021,
    val from: String = "QrInvite",
)

data class UserAuthLogin(
    val accountId: String?,
    val session: String? = null,
)

data class ShareGroup(
    val id: String,
    val name: String,
    val powers: String,
    val shareCode: String,
    val expireTime: String,
)

data class ShareGroupCreation(
    val group: ShareGroup,
    val invitePage: String,
    val expireMinutes: Int?,
)

/** Dispositivo de la lista del cloud (get-device-list, contrato capturado en el flow mitm). */
data class UserAuthDevice(
    val id: String,
    val name: String,
    val model: String,
    val type: String,
    val channelNum: Int,
    val fromShare: Boolean,
    val powers: String,
)

/** Credenciales dinamicas del device emitidas por get-device-token (DevDynamicPwdResp). */
data class DeviceTokenData(
    val authCode: String,
    val dataEncodeKey: String,
    val dynamicPassword: String,
    val passwordExpired: String,
    val transparentBasedata: String,
)
