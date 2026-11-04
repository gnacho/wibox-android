package dev.nacho.wibox.core.cloud.quii

object RbUdpConstants {
    const val MARKER: Long = 0xFFABEFC1L
    const val CONNECT_TAG: String = "ion::Ope"
    const val DATA_TAG: String = "ad()  st"
    const val CONTROL_BOOTSTRAP_STATUS: Int = 0xFFFF4100.toInt()
    const val CONTROL_ESTABLISHED_STATUS: Int = 0xFFFF0900.toInt()
    const val CONTROL_SYN_ACK_STATUS: Int = 0xFFFF4900.toInt()
    const val CONTROL_FLOW_STATUS: Int = 0xFFFF0D00.toInt()
    const val CONTROL_HEARTBEAT_REQUEST_STATUS: Int = 0xFFFF0500.toInt()
    const val CONTROL_PROGRESS_STATUS: Int = 0xFFB40900.toInt()
    const val CONTROL_POST_CONNECT_STATUS: Int = 0xFFA80900.toInt()
    const val CONTROL_POST_CONNECT_ACK_STATUS: Int = 0xFF700900.toInt()
    const val CONTROL_POST_SETUP_STATUS: Int = 0xFED80900.toInt()
    const val CONTROL_POST_SETUP_ACK_STATUS: Int = 0xFEB00900.toInt()
    const val CONTROL_POST_PLAY_STATUS: Int = 0xFEB80900.toInt()
    const val CONTROL_POST_PLAY_ACK_STATUS: Int = 0xFE900900.toInt()
    const val CONTROL_STREAM_READY_STATUS: Int = 0xFF480900.toInt()
    const val CONTROL_STREAM_READY_ACK_STATUS: Int = 0xFF100900.toInt()
    const val CONTROL_PLAY_SYNC_STATUS: Int = 0xFF780900.toInt()
    const val CONTROL_PLAY_LATE_STATUS: Int = 0xFF880900.toInt()
    const val CONTROL_PLAY_SYNC_ACK_STATUS: Int = 0xFA740900.toInt()
    const val CONTROL_PLAY_ESTABLISHED_ACK_STATUS: Int = 0xFB9E0900.toInt()
    const val WRAPPED_STATUS: Int = 0x00001900
    const val STREAM_DATA_STATUS: Int = 0x00000500
    const val SRC_ID_BASE: Int = 0x04000006
    const val CMD_CONNECT: Int = 0x00120101
    const val CMD_DISCONNECT: Int = 0x00120102
    const val CMD_DATA: Int = 0x00120103
    val QUII_PLAY_TAG_0: ByteArray = hexBytes("61006e0064007200")
    val QUII_PLAY_TAG_1: ByteArray = hexBytes("0100000000000000")

    fun hexBytes(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

data class RbControl(
    val word4: Int,
    val word8: Int,
    val localId: Int,
    val remoteId: Int,
    val statusWord: Int,
    val checksum16: Int,
    val packetLen16: Int,
)

data class RbWrapped(
    val word4: Int,
    val word8: Int,
    val localId: Int,
    val remoteId: Int,
    val statusWord: Int,
    val innerTotalLength: Int,
    val tag8: ByteArray,
    val innerPacket: ByteArray,
)

data class RbDataPacket(
    val packetLength: Int,
    val typeFlag: Int,
    val command: Int,
    val seq: Int,
    val payloadLength: Int,
    val destId: Int,
    val srcId: Int,
    val payload: ByteArray,
)

data class KcpConnectResponse(
    val packetLength: Int,
    val typeFlag: Int,
    val command: Int,
    val resultCode: Int,
    val connectId: Int,
    val destId: Int,
)

object RbUdpPackets {
    fun nativeChecksum16(packet: ByteArray): Int {
        val total = sumForChecksum(packet)
        var folded = (total ushr 16) + (total and 0xFFFF)
        folded += folded ushr 16
        return folded.inv() and 0xFFFF
    }

    private fun sumForChecksum(packet: ByteArray): Int {
        var total = 0
        var i = 0
        while (i + 1 < packet.size) {
            total += readU16Le(packet, i)
            i += 2
        }
        if (packet.size % 2 == 1) total += packet[packet.size - 1].toInt() and 0xFF
        return total
    }

    fun buildControl(
        word4: Int,
        word8: Int,
        localId: Int,
        remoteId: Int,
        statusWord: Int,
    ): ByteArray {
        val packet = ByteArray(28)
        writeU32Le(packet, 0x00, 0xFFABEFC1.toInt())
        writeU32Le(packet, 0x04, word4)
        writeU32Le(packet, 0x08, word8)
        writeU32Le(packet, 0x0C, localId)
        writeU32Le(packet, 0x10, remoteId)
        writeU32Le(packet, 0x14, statusWord)
        writeU16Le(packet, 0x1A, 28)
        writeU16Le(packet, 0x18, nativeChecksum16(packet))
        return packet
    }

    fun parseControl(packet: ByteArray): RbControl {
        require(packet.size == 28) { "control packet must be 28 bytes" }
        require(readU32Le(packet, 0x00) == 0xFFABEFC1.toInt()) { "bad control marker" }
        return RbControl(
            word4 = readU32Le(packet, 0x04),
            word8 = readU32Le(packet, 0x08),
            localId = readU32Le(packet, 0x0C),
            remoteId = readU32Le(packet, 0x10),
            statusWord = readU32Le(packet, 0x14),
            checksum16 = readU16Le(packet, 0x18),
            packetLen16 = readU16Le(packet, 0x1A),
        )
    }

    fun buildWrapped(
        innerPacket: ByteArray,
        word4: Int,
        word8: Int,
        localId: Int,
        remoteId: Int,
        statusWord: Int,
        tag8: ByteArray,
    ): ByteArray {
        require(tag8.size == 8) { "tag8 must be 8 bytes" }
        require(innerPacket.size >= 0x10) { "inner too short" }
        val total = 0x24 + 8 + (innerPacket.size - 0x10)
        val packet = ByteArray(total)
        writeU32Le(packet, 0x00, 0xFFABEFC1.toInt())
        writeU32Le(packet, 0x04, word4)
        writeU32Le(packet, 0x08, word8)
        writeU32Le(packet, 0x0C, localId)
        writeU32Le(packet, 0x10, remoteId)
        writeU32Le(packet, 0x14, statusWord)
        writeU16Le(packet, 0x1A, total and 0xFFFF)
        writeU32Le(packet, 0x1C, -1)
        writeU32Le(packet, 0x20, innerPacket.size)
        System.arraycopy(tag8, 0, packet, 0x24, 8)
        System.arraycopy(innerPacket, 0x10, packet, 0x2C, innerPacket.size - 0x10)
        writeU16Le(packet, 0x18, nativeChecksum16(packet.copyOfRange(0, 0x1C)))
        return packet
    }

    fun parseWrapped(packet: ByteArray): RbWrapped {
        require(packet.size >= 0x24) { "wrapped too short" }
        require(readU32Le(packet, 0x00) == 0xFFABEFC1.toInt()) { "bad wrapped marker" }
        val innerTotal = readU32Le(packet, 0x20)
        require(innerTotal >= 0x10) { "inner too small" }
        if (readU32Le(packet, 0x1C) == -1 && packet.size >= 0x1C + innerTotal) {
            return RbWrapped(
                word4 = readU32Le(packet, 0x04),
                word8 = readU32Le(packet, 0x08),
                localId = readU32Le(packet, 0x0C),
                remoteId = readU32Le(packet, 0x10),
                statusWord = readU32Le(packet, 0x14),
                innerTotalLength = innerTotal,
                tag8 = ByteArray(8),
                innerPacket = packet.copyOfRange(0x1C, 0x1C + innerTotal),
            )
        }
        require(packet.size >= 0x24 + 8) { "tagged wrapped too short" }
        val payload = packet.copyOfRange(0x24, packet.size)
        require(payload.size == 8 + (innerTotal - 0x10)) { "wrapped size mismatch" }
        val inner = ByteArray(innerTotal)
        writeU32Le(inner, 0x00, -1)
        writeU32Le(inner, 0x04, innerTotal)
        System.arraycopy(payload, 8, inner, 0x10, innerTotal - 0x10)
        return RbWrapped(
            word4 = readU32Le(packet, 0x04),
            word8 = readU32Le(packet, 0x08),
            localId = readU32Le(packet, 0x0C),
            remoteId = readU32Le(packet, 0x10),
            statusWord = readU32Le(packet, 0x14),
            innerTotalLength = innerTotal,
            tag8 = payload.copyOfRange(0, 8),
            innerPacket = inner,
        )
    }

    fun buildKcpConnect(srcId: Int, channel: Int, connType: Int = 0): ByteArray {
        val packet = ByteArray(0x4C)
        writeU32Le(packet, 0x00, -1)
        writeU32Le(packet, 0x04, 0x4C)
        writeU16Le(packet, 0x10, 0x0100)
        writeU16Le(packet, 0x12, 0x0000)
        writeU32Le(packet, 0x14, RbUdpConstants.CMD_CONNECT)
        writeU32Le(packet, 0x24, 0x24)
        writeU32Le(packet, 0x28, srcId)
        packet[0x40] = (((channel and 0x0F) shl 4) or ((connType and 0x01) shl 3)).toByte()
        return packet
    }

    fun parseKcpConnectResponse(packet: ByteArray): KcpConnectResponse {
        require(packet.size >= 0x3C) { "connect response too short" }
        val packetLength = readU32Le(packet, 0x04)
        val typeFlag = readU16Le(packet, 0x12)
        val command = readU32Le(packet, 0x14)
        val payloadLength = readU32Le(packet, 0x24)
        return if (packetLength >= 0x4C && packet.size >= packetLength) {
            KcpConnectResponse(packetLength, typeFlag, command, readU16Le(packet, 0x10), readU32Le(packet, 0x28), readU32Le(packet, packetLength - 4))
        } else {
            KcpConnectResponse(packetLength, typeFlag, command, readU32Le(packet, 0x10), readU32Le(packet, 0x18), readU32Le(packet, 0x38))
        }
    }

    fun buildKcpDisconnect(srcId: Int, destId: Int): ByteArray {
        val packet = ByteArray(0x30)
        writeU32Le(packet, 0x00, -1)
        writeU32Le(packet, 0x04, 0x30)
        writeU16Le(packet, 0x10, 0x0100)
        writeU16Le(packet, 0x12, 0x0000)
        writeU32Le(packet, 0x14, RbUdpConstants.CMD_DISCONNECT)
        writeU32Le(packet, 0x24, 0x08)
        writeU32Le(packet, 0x28, destId)
        writeU32Le(packet, 0x2C, srcId)
        return packet
    }

    fun buildRbData(payload: ByteArray, srcId: Int, destId: Int, seq: Int): ByteArray {
        val totalLen = 0x38 + payload.size
        val packet = ByteArray(totalLen)
        writeU32Le(packet, 0x00, -1)
        writeU32Le(packet, 0x04, totalLen)
        writeU16Le(packet, 0x10, 0x0100)
        writeU16Le(packet, 0x12, 0x0000)
        writeU32Le(packet, 0x14, RbUdpConstants.CMD_DATA)
        writeU32Le(packet, 0x18, seq)
        writeU32Le(packet, 0x24, totalLen - 0x28)
        writeU32Le(packet, 0x28, destId)
        writeU32Le(packet, 0x2C, srcId)
        writeU32Le(packet, 0x30, totalLen - 0x28 - 0x10)
        System.arraycopy(payload, 0, packet, 0x38, payload.size)
        return packet
    }

    fun buildRbDataAck(payloadLength: Int, seq: Int, srcId: Int, destId: Int): ByteArray {
        val packet = ByteArray(0x38)
        writeU32Le(packet, 0x00, -1)
        writeU32Le(packet, 0x04, 0x38)
        writeU16Le(packet, 0x10, 0x0100)
        writeU16Le(packet, 0x12, 0x0001)
        writeU32Le(packet, 0x14, RbUdpConstants.CMD_DATA)
        writeU32Le(packet, 0x18, seq)
        writeU32Le(packet, 0x24, 0x10)
        writeU32Le(packet, 0x28, destId)
        writeU32Le(packet, 0x2C, srcId)
        writeU32Le(packet, 0x30, payloadLength)
        return packet
    }

    fun parseRbData(packet: ByteArray): RbDataPacket {
        require(packet.size >= 0x38) { "rb data too short" }
        val packetLength = readU32Le(packet, 0x04)
        val payloadEnd = minOf(packetLength, packet.size)
        return RbDataPacket(
            packetLength = packetLength,
            typeFlag = readU16Le(packet, 0x12),
            command = readU32Le(packet, 0x14),
            seq = readU32Le(packet, 0x18),
            payloadLength = readU32Le(packet, 0x24),
            destId = readU32Le(packet, 0x28),
            srcId = readU32Le(packet, 0x2C),
            payload = packet.copyOfRange(0x38, payloadEnd),
        )
    }

    fun isRbDataAck(packet: ByteArray): Boolean = readU16Le(packet, 0x12) == 0x0001

    private fun writeU16Le(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun writeU32Le(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
        dst[off + 2] = ((v shr 16) and 0xFF).toByte()
        dst[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun readU16Le(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or ((src[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32Le(src: ByteArray, off: Int): Int =
        readU16Le(src, off) or (readU16Le(src, off + 2) shl 16)
}
