package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.quii.QuiiCrypto
import dev.nacho.wibox.core.cloud.quii.QuiiEnvelope
import dev.nacho.wibox.core.cloud.quii.QuiiLiveConfig
import dev.nacho.wibox.core.cloud.quii.QuiiLiveSession
import dev.nacho.wibox.core.cloud.quii.QuiiTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QuiiLiveSessionTest {
    @Test
    fun transparentCommandDoesNotLoseImmediateResponse() = runBlocking {
        val key = ByteArray(32) { it.toByte() }
        val transport = ImmediateUnlockTransport(key)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = QuiiLiveSession(
            config = QuiiLiveConfig(
                username = "adminapp",
                password = "unused",
                channel = 0,
                stream = 0,
                cryptoKey = key,
            ),
            transport = transport,
            scope = scope,
        )
        try {
            assertEquals(QuiiLiveSession.OpenResult.Success, session.open())
            val response = session.sendTransparent(byteArrayOf(1, 0, 0, 1) + ByteArray(140), command = 4)
            assertArrayEquals(byteArrayOf(0, 0), response)
        } finally {
            session.close()
            scope.cancel()
        }
    }
}

private class ImmediateUnlockTransport(private val key: ByteArray) : QuiiTransport {
    private val packets = Channel<ByteArray>(Channel.UNLIMITED)

    override suspend fun sendSetup(): Boolean {
        val setup = ByteArray(32)
        setup[0] = QuiiEnvelope.TYPE_SETUP.toByte()
        setup[9] = 0
        setup[10] = 2
        setup[11] = 1
        packets.trySend(setup)
        return true
    }

    override suspend fun sendPlay(): Boolean = true

    override suspend fun sendKeepalive(): Boolean = true

    override suspend fun send(packet: ByteArray) {
        // El timbre real devuelve el numero de orden en b13 del paquete de respuesta.
        // El header va cifrado entero, hay que descifrarlo para leerlo.
        val order = if (packet.size >= 32) {
            QuiiCrypto.aesCbcDecrypt(key, packet.copyOfRange(0, 32))[13].toInt() and 0xFF
        } else {
            0
        }
        packets.trySend(
            QuiiEnvelope.encryptPacket(
                packetType = 0xFE,
                body = byteArrayOf(0, 0),
                key = key,
                b13 = order,
                b14 = 0,
                flag15 = 0,
                flag16 = 0,
            ),
        )
        delay(100)
    }

    override fun incoming(): Flow<ByteArray> = packets.receiveAsFlow()

    override fun close() {
        packets.close()
    }
}
