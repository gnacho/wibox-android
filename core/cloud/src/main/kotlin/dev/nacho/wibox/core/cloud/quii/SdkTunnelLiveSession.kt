package dev.nacho.wibox.core.cloud.quii

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.net.ConnectException

/**
 * Live session whose transport is the vendor P2P SDK instead of our own
 * UST/MQTT/KCP stack. The SDK exposes the device media port on localhost, so
 * the doorbell protocol is the same one we already speak.
 */
class SdkTunnelLiveSession(
    private val transport: TcpQuiiTransport,
    val live: QuiiLiveSession,
    private val unlockPassword: String?,
    private val scope: CoroutineScope,
) {

    val frames: Flow<MediaFrame> get() = live.frames

    /** Motivo de cuelgue del timbre (1 = canal ocupado). */
    val hangUpReason: kotlinx.coroutines.flow.StateFlow<Int?> get() = live.hangUpReason

    suspend fun unlockDoor(
        lockNumber: Int = 1,
        unlockChannel: Int = 1,
        passwordOverride: String? = null,
        passwordSha256: Boolean = true,
    ): Int {
        val rawPassword = passwordOverride?.takeIf { it.isNotEmpty() }
            ?: unlockPassword?.takeIf { it.isNotEmpty() }
            ?: throw ConnectException("contrasena de apertura no configurada")
        return unlockDoorThrough(live, rawPassword, passwordSha256, lockNumber, unlockChannel)
    }

    suspend fun close() {
        runCatching { live.close() }
        runCatching { transport.close() }
    }

    /**
     * Cierre en seco, sin suspender: suelta el socket ya. Si no se llama, el
     * timbre sigue viendo la conexion viva y responde BUSY a cualquier PLAY.
     */
    fun closeNow() {
        runCatching { transport.close() }
    }
}

internal suspend fun unlockDoorThrough(
    session: QuiiLiveSession,
    rawPassword: String,
    passwordSha256: Boolean,
    lockNumber: Int,
    unlockChannel: Int,
): Int {
    val effectivePassword = if (passwordSha256) QuiiCrypto.encodeDevicePassword(rawPassword) else rawPassword
    val password = effectivePassword.toByteArray(Charsets.UTF_8)
    val body = ByteArray(16 + password.size)
    body[0] = lockNumber.toByte()
    body[1] = 0
    body[2] = unlockChannel.toByte()
    body[3] = if (passwordSha256) 1 else 0
    password.copyInto(body, 16)
    val response = session.sendTransparent(body, command = 4)
    println("WIBOX-UNLOCK RESP ${response.joinToString("") { "%02x".format(it) }}")
    if (response.size < 2) return -1
    if (response[0].toInt() == 0) return 0
    return if (response[1].toInt() == 2) -10029 else -1
}
