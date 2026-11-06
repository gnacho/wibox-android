package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RbUdpReorderBufferTest {

    private val payloadSize = 100

    private fun bytes(tag: Int): ByteArray = ByteArray(payloadSize) { tag.toByte() }

    private fun RbUdpReorderBuffer.deliver(localId: Int, tag: Int, nowMs: Long = 0L): List<Int> =
        offer(localId, bytes(tag), nowMs).map { it[0].toInt() }

    @Test
    fun `in order packets are delivered immediately`() {
        val buffer = RbUdpReorderBuffer()
        assertEquals(listOf(1), buffer.deliver(0, 1))
        assertEquals(listOf(2), buffer.deliver(payloadSize, 2))
        assertEquals(listOf(3), buffer.deliver(payloadSize * 2, 3))
        assertEquals(payloadSize * 3, buffer.nextRemoteId)
        assertEquals(0, buffer.reordered)
        assertEquals(0, buffer.gapsSkipped)
    }

    @Test
    fun `an out of order packet is delivered once the gap is filled`() {
        val buffer = RbUdpReorderBuffer()
        assertEquals(listOf(1), buffer.deliver(0, 1))
        // Llega el tercero antes que el segundo.
        assertTrue(buffer.deliver(payloadSize * 2, 3).isEmpty())
        assertEquals(payloadSize, buffer.nextRemoteId)
        assertEquals(1, buffer.reordered)
        // Al llegar el segundo se entrega el segundo y despues el tercero retenido.
        assertEquals(listOf(2, 3), buffer.deliver(payloadSize, 2))
        assertEquals(payloadSize * 3, buffer.nextRemoteId)
    }

    @Test
    fun `a packet that never arrives is skipped after the timeout`() {
        var now = 1_000L
        val buffer = RbUdpReorderBuffer(gapTimeoutMs = 200L, clockMs = { now })
        assertEquals(listOf(1), buffer.deliver(0, 1, now))
        assertTrue(buffer.deliver(payloadSize * 2, 3, now).isEmpty())
        // Todavia dentro del plazo: no se salta el hueco.
        now += 150
        assertTrue(buffer.pollGaps(now).isEmpty())
        assertEquals(payloadSize, buffer.nextRemoteId)
        // Pasado el plazo se salta y se entrega lo retenido.
        now += 100
        assertEquals(listOf(3), buffer.pollGaps(now).map { it[0].toInt() })
        assertEquals(1, buffer.gapsSkipped)
        assertEquals(payloadSize * 3, buffer.nextRemoteId)
    }

    @Test
    fun `a lost packet is also skipped when the next packet arrives late`() {
        var now = 0L
        val buffer = RbUdpReorderBuffer(gapTimeoutMs = 200L, clockMs = { now })
        assertEquals(listOf(1), buffer.deliver(0, 1, now))
        assertTrue(buffer.deliver(payloadSize * 2, 3, now).isEmpty())
        now += 500
        // El cuarto paquete llega pasado el plazo: se suelta el tercero y luego el cuarto.
        assertEquals(listOf(3, 4), buffer.deliver(payloadSize * 3, 4, now))
        assertEquals(payloadSize * 4, buffer.nextRemoteId)
        assertEquals(1, buffer.gapsSkipped)
    }

    @Test
    fun `duplicates do not duplicate bytes`() {
        val buffer = RbUdpReorderBuffer()
        assertEquals(listOf(1), buffer.deliver(0, 1))
        assertTrue(buffer.deliver(0, 1).isEmpty())
        assertEquals(1, buffer.duplicates)
        assertEquals(payloadSize, buffer.nextRemoteId)
        // Duplicado de un paquete que aun esta retenido.
        assertTrue(buffer.deliver(payloadSize * 2, 3).isEmpty())
        assertTrue(buffer.deliver(payloadSize * 2, 3).isEmpty())
        assertEquals(2, buffer.duplicates)
        assertEquals(listOf(2, 3), buffer.deliver(payloadSize, 2))
    }

    @Test
    fun `a gap too large to hold resyncs on the new packet`() {
        val buffer = RbUdpReorderBuffer(maxGapBytes = 4 * payloadSize)
        assertEquals(listOf(1), buffer.deliver(0, 1))
        assertEquals(listOf(9), buffer.deliver(payloadSize * 20, 9))
        assertEquals(1, buffer.gapsSkipped)
        assertEquals(payloadSize * 21, buffer.nextRemoteId)
    }

    @Test
    fun `a full queue resyncs instead of holding forever`() {
        val buffer = RbUdpReorderBuffer(maxHoldPackets = 2, gapTimeoutMs = 10_000L)
        assertEquals(listOf(1), buffer.deliver(0, 1))
        assertTrue(buffer.deliver(payloadSize * 2, 3).isEmpty())
        assertTrue(buffer.deliver(payloadSize * 3, 4).isEmpty())
        // Tercer adelantado con la cola llena: se suelta el hueco y se sigue aqui.
        assertEquals(listOf(5), buffer.deliver(payloadSize * 4, 5))
        assertEquals(1, buffer.gapsSkipped)
        assertEquals(payloadSize * 5, buffer.nextRemoteId)
    }

    @Test
    fun `the reassembled stream has no gap when the delay resolves`() {
        val buffer = RbUdpReorderBuffer()
        val chunks = (0 until 6).map { bytes(it) }
        val order = listOf(0, 2, 1, 4, 3, 5)
        val out = ArrayList<Byte>()
        for (index in order) buffer.offer(index * payloadSize, chunks[index], 0L).forEach { out.addAll(it.toList()) }
        assertContentEquals(chunks.flatMap { it.toList() }.toByteArray(), out.toByteArray())
        assertEquals(payloadSize * 6, buffer.nextRemoteId)
    }

    @Test
    fun `reset starts a new session`() {
        val buffer = RbUdpReorderBuffer()
        buffer.deliver(0, 1)
        buffer.deliver(payloadSize * 2, 3)
        buffer.reset()
        assertEquals(null, buffer.nextRemoteId)
        assertEquals(0, buffer.reordered)
        assertEquals(0, buffer.gapsSkipped)
        assertTrue(buffer.offer(0, bytes(7), 0L).isNotEmpty())
    }
}
