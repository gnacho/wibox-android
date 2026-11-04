package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RbUdpPacketsTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hexed(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `native checksum matches python vector`() {
        assertEquals(0xFFFF, RbUdpPackets.nativeChecksum16(ByteArray(28)))
        val sample = ByteArray(28)
        sample[4] = 0x11
        assertEquals(0xFFEE, RbUdpPackets.nativeChecksum16(sample))
    }

    @Test
    fun `control packet matches python builder`() {
        val packet = RbUdpPackets.buildControl(
            word4 = 0,
            word8 = 0x11223344,
            localId = 0x04000006,
            remoteId = 0,
            statusWord = 0xFFFF4100.toInt(),
        )
        assertEquals("c1efabff000000004433221106000004000000000041ffff09871c00", packet.hexed())
        val parsed = RbUdpPackets.parseControl(packet)
        assertEquals(0x11223344, parsed.word8)
        assertEquals(0x04000006, parsed.localId)
        assertEquals(0xFFFF4100.toInt(), parsed.statusWord)
        assertEquals(28, parsed.packetLen16)
    }

    @Test
    fun `wrapped connect matches python builder`() {
        val inner = RbUdpPackets.buildKcpConnect(srcId = 0x04000006, channel = 0)
        assertEquals(
            "ffffffff4c0000000000000000000000000100000101120000000000000000000000000024000000060000040000000000000000000000000000000000000000000000000000000000000000",
            inner.hexed(),
        )
        val wrapped = RbUdpPackets.buildWrapped(
            innerPacket = inner,
            word4 = 0,
            word8 = 0x55667788,
            localId = 7,
            remoteId = 9,
            statusWord = RbUdpConstants.WRAPPED_STATUS,
            tag8 = "ad()  st".toByteArray(Charsets.US_ASCII),
        )
        assertEquals("c1efabff00000000887766550700000009000000001900002b2a6800ffffffff4c0000006164282920207374000100000101120000000000000000000000000024000000060000040000000000000000000000000000000000000000000000000000000000000000", wrapped.hexed())
        val parsed = RbUdpPackets.parseWrapped(wrapped)
        assertEquals(inner.size, parsed.innerTotalLength)
        // Python parse clasifica su propio wrapped como "direct" (0xFFFFFFFF en 0x1C)
        assertTrue(parsed.tag8.contentEquals(ByteArray(8)))
        assertTrue(parsed.innerPacket.contentEquals(hex("ffffffff4c0000006164282920207374000100000101120000000000000000000000000024000000060000040000000000000000000000000000000000000000000000000000000000000000")))
    }

    @Test
    fun `kcp disconnect matches python builder`() {
        val packet = RbUdpPackets.buildKcpDisconnect(srcId = 0x04000006, destId = 0xABCD1234.toInt())
        assertEquals(
            "ffffffff3000000000000000000000000001000002011200000000000000000000000000080000003412cdab06000004",
            packet.hexed(),
        )
    }

    @Test
    fun `rb data and ack match python builders`() {
        val data = RbUdpPackets.buildRbData(
            payload = ByteArray(12) { it.toByte() },
            srcId = 0x04000006,
            destId = 0xABCD1234.toInt(),
            seq = 42,
        )
        assertEquals(
            "ffffffff44000000000000000000000000010000030112002a00000000000000000000001c0000003412cdab060000040c00000000000000000102030405060708090a0b",
            data.hexed(),
        )
        val parsed = RbUdpPackets.parseRbData(data)
        assertEquals(42, parsed.seq)
        assertEquals(0xABCD1234.toInt(), parsed.destId)
        assertEquals(12, parsed.payload.size)
        assertEquals(false, RbUdpPackets.isRbDataAck(data))

        val ack = RbUdpPackets.buildRbDataAck(payloadLength = 1000, seq = 7, srcId = 0x04000006, destId = 0xABCD1234.toInt())
        assertEquals(
            "ffffffff3800000000000000000000000001010003011200070000000000000000000000100000003412cdab06000004e803000000000000",
            ack.hexed(),
        )
        assertTrue(RbUdpPackets.isRbDataAck(ack))
    }
}
