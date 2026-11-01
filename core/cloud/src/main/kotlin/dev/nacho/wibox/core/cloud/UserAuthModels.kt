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

/** Datos de una invitación QR de compartición (extraídos del QR / DeviceCodeParser). */
data class ShareInvitation(
    val deviceId: String,
    val inviteCode: String,
    val ownerId: String,
    val oemId: String = "G0021",
    val appId: Int = 2021,
    val from: String = "QrInvite",
)
