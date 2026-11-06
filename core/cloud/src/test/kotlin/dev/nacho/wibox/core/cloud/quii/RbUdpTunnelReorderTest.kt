package dev.nacho.wibox.core.cloud.quii

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Comprueba el cableado de la cola de reordenacion dentro del tunel: los paquetes
 * del carril de datos se entregan al reensamblador en el orden del offset remoto.
 */
class RbUdpTunnelReorderTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tunnel = RbUdpTunnel(
        socket = DatagramSocket(0, InetAddress.getLoopbackAddress()),
        // El tunel manda los ACK aqui; el puerto discard absorbe los datagramas.
        peer = InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
        p2pSessionFlag = "test",
    )

    private fun dataPacket(localId: Int, chunk: ByteArray, word4: Int = 0x1111, word8: Int = 0x2222): ByteArray {
        val raw = ByteArray(0x1C + chunk.size)
        writeU32Le(raw, 0x00, RbUdpConstants.MARKER.toInt())
        writeU32Le(raw, 0x04, word4)
        writeU32Le(raw, 0x08, word8)
        writeU32Le(raw, 0x0C, localId)
        writeU32Le(raw, 0x10, 0x3333)
        writeU32Le(raw, 0x14, 0x0500)
        System.arraycopy(chunk, 0, raw, 0x1C, chunk.size)
        return raw
    }

    private fun kcpFrame(payload: ByteArray, seq: Int): ByteArray = RbUdpPackets.buildRbData(
        payload = payload,
        srcId = RbUdpConstants.SRC_ID_BASE,
        destId = 0x0B030000,
        seq = seq,
    )

    private fun writeU32Le(dst: ByteArray, off: Int, v: Int) {
        for (i in 0 until 4) dst[off + i] = ((v shr (8 * i)) and 0xFF).toByte()
    }

    @Test
    fun `reordered data packets reach the quii stream in offset order`() = runBlocking {
        val collected = mutableListOf<ByteArray>()
        val collector = scope.launch(Dispatchers.Unconfined) {
            tunnel.quiiIncoming.collect { collected.add(it) }
        }
        val payloads = listOf(
            ByteArray(20) { 0xA1.toByte() },
            ByteArray(20) { 0xB2.toByte() },
            ByteArray(20) { 0xC3.toByte() },
        )
        val frames = payloads.mapIndexed { index, payload -> kcpFrame(payload, seq = index + 2) }
        // Cada paquete lleva un frame KCP completo y avanza el offset con su tamano.
        val packetIds = ArrayList<Int>()
        var offset = 0
        for (frame in frames) {
            packetIds.add(offset)
            offset += frame.size
        }

        // Llegan el primero y el tercero, y despues el segundo.
        tunnel.handleDataPayload(dataPacket(packetIds[0], frames[0]))
        tunnel.handleDataPayload(dataPacket(packetIds[2], frames[2]))
        assertEquals(1, collected.size)
        tunnel.handleDataPayload(dataPacket(packetIds[1], frames[1]))
        // Un duplicado del primero no vuelve a entregar bytes.
        tunnel.handleDataPayload(dataPacket(packetIds[0], frames[0]))
        collector.cancel()
        scope.cancel()

        assertEquals(3, collected.size)
        assertContentEquals(payloads[0], collected[0])
        assertContentEquals(payloads[1], collected[1])
        assertContentEquals(payloads[2], collected[2])
    }

    @Test
    fun `a packet held too long is released on the timeout flush`() = runBlocking {
        val collected = mutableListOf<ByteArray>()
        val collector = scope.launch(Dispatchers.Unconfined) {
            tunnel.quiiIncoming.collect { collected.add(it) }
        }
        val payload = ByteArray(20) { 0xD4.toByte() }
        val frame = kcpFrame(payload, seq = 7)
        tunnel.handleDataPayload(dataPacket(0, frame))
        // El hueco [frame.size, 5 * frame.size) nunca llega.
        tunnel.handleDataPayload(dataPacket(5 * frame.size, frame))
        assertEquals(1, collected.size)
        // Antes del plazo el retenido sigue en la cola.
        tunnel.flushExpiredStreamData()
        assertEquals(1, collected.size)
        Thread.sleep(250L)
        tunnel.flushExpiredStreamData()
        collector.cancel()
        scope.cancel()

        assertEquals(2, collected.size)
        assertContentEquals(payload, collected[1])
    }
}
