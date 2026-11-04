package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.quii.QuiiEnvelope
import dev.nacho.wibox.core.cloud.quii.TcpQuiiTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class TcpQuiiTransportTest {

    @Test
    fun `lee setup en claro y envelopes cifrados encadenados`() = runBlocking {
        val key = ByteArray(32) { (it + 1).toByte() }
        val keepalive = QuiiEnvelope.buildKeepalive(key)
        // El dispositivo responde al setup con 32 bytes en claro: tipo 0xA9, status 0,
        // crypto 2, sha 1. No lleva cuerpo ni va cifrado.
        val setupReply = ByteArray(32).also {
            it[0] = 0xA9.toByte()
            it[10] = 2
            it[11] = 1
        }
        val server = ServerSocket(0)
        val done = CountDownLatch(1)
        var writtenByClient = ByteArray(0)

        val acceptor = thread {
            server.accept().use { socket ->
                socket.getOutputStream().write(setupReply + keepalive + keepalive)
                socket.getOutputStream().flush()
                val buffer = ByteArray(keepalive.size)
                var read = 0
                while (read < buffer.size) {
                    val count = socket.getInputStream().read(buffer, read, buffer.size - read)
                    if (count < 0) break
                    read += count
                }
                writtenByClient = buffer
                done.await(5, TimeUnit.SECONDS)
            }
        }

        val transport = TcpQuiiTransport("127.0.0.1", server.localPort, key)
        transport.connect(2_000)
        val received = mutableListOf<ByteArray>()
        // No usar take(): el productor bloqueado en read no se cancela y el
        // collector esperaria para siempre. Se recoge en un job y se cierra.
        val collector = launch(Dispatchers.IO) {
            runCatching { transport.incoming().collect { received.add(it) } }
        }
        withTimeout(5_000) {
            while (received.size < 3) delay(20)
        }
        transport.send(keepalive)
        transport.close()
        collector.cancel()
        done.countDown()
        acceptor.join(3_000)
        server.close()

        assertEquals(3, received.size)
        assertArrayEquals(setupReply, received[0])
        assertArrayEquals(keepalive, received[1])
        assertArrayEquals(keepalive, received[2])
        assertArrayEquals(keepalive, writtenByClient)
    }
}
