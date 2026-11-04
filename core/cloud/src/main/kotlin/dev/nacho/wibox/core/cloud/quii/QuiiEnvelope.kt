package dev.nacho.wibox.core.cloud.quii

import java.io.ByteArrayOutputStream

data class QuiiHeader(
    val packetType: Int,
    val timestampSecs: Long,
    val commandPayloadSize: Int,
    val rawSize: Int,
    val mediaPayloadSize: Int,
    val b13: Int,
    val b14: Int,
    val flag15: Int,
    val flag16: Int,
    val flag17: Int,
) {
    val isMedia: Boolean get() = packetType in 0xA0..0xA3
    val readSize: Int get() = if (isMedia) mediaPayloadSize else commandPayloadSize
    val mediaPayloadOffset: Int get() = flag16 or (flag17 shl 8)
    val channel: Int get() = b13 or (b14 shl 8)
}

data class DecodedQuiiPacket(
    val header: QuiiHeader,
    val commandBytes: ByteArray?,
    val mediaPayload: ByteArray?,
    val totalLength: Int,
    val headerBytes: ByteArray? = null,
)

object QuiiEnvelope {
    const val TYPE_KEEPALIVE = 0x00
    const val TYPE_PLAY = 0x01
    const val TYPE_SETUP = 0xA9

    fun buildSetup(): ByteArray = ByteArray(32).also { it[0] = TYPE_SETUP.toByte() }

    fun buildHeader(
        packetType: Int,
        timestampSecs: Long = System.currentTimeMillis() / 1000,
        commandPayloadSize: Int = 0,
        rawSize: Int = 0,
        b13: Int = 0,
        b14: Int = 0,
        flag15: Int = 0,
        flag16: Int = 0,
        flag17: Int = 0,
    ): ByteArray {
        val h = ByteArray(32)
        h[0] = packetType.toByte()
        writeU64Le(h, 1, timestampSecs)
        writeU16Le(h, 9, commandPayloadSize)
        writeU16Le(h, 11, rawSize)
        h[13] = b13.toByte()
        h[14] = b14.toByte()
        h[15] = flag15.toByte()
        h[16] = flag16.toByte()
        h[17] = flag17.toByte()
        return h
    }

    fun parseHeader(clear: ByteArray): QuiiHeader {
        require(clear.size >= 18) { "header too short" }
        return QuiiHeader(
            packetType = clear[0].toInt() and 0xFF,
            timestampSecs = readU64Le(clear, 1),
            commandPayloadSize = readU16Le(clear, 9),
            rawSize = readU16Le(clear, 11),
            mediaPayloadSize = readU32Le(clear, 11),
            b13 = clear[13].toInt() and 0xFF,
            b14 = clear[14].toInt() and 0xFF,
            flag15 = clear[15].toInt() and 0xFF,
            flag16 = clear[16].toInt() and 0xFF,
            flag17 = clear[17].toInt() and 0xFF,
        )
    }

    fun playBody(username: String, password: String, target: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(username.toByteArray(Charsets.UTF_8))
        out.write("&&".toByteArray(Charsets.UTF_8))
        out.write(password.toByteArray(Charsets.UTF_8))
        out.write(0)
        out.write(target.toByteArray(Charsets.UTF_8))
        out.write(0)
        return out.toByteArray()
    }

    fun playTarget(channel: Int, stream: Int): String {
        val sb = StringBuilder("/mode=real&idc=$channel&ids=$stream")
        LiveTuning.apParam?.let { sb.append("&ap=$it") }
        if (LiveTuning.inner) sb.append("&inner=1")
        return sb.toString()
    }

    fun encryptPacket(
        packetType: Int,
        body: ByteArray,
        key: ByteArray,
        b13: Int = 0,
        b14: Int = 0,
        flag15: Int = 0,
        flag16: Int = 0,
        flag17: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
    ): ByteArray {
        val extLen = QuiiCrypto.zeroPad(body + ByteArray(32)).size
        val clearHeader = buildHeader(
            packetType = packetType,
            timestampSecs = timestamp,
            commandPayloadSize = extLen,
            rawSize = body.size,
            b13 = b13,
            b14 = b14,
            flag15 = flag15,
            flag16 = flag16,
            flag17 = flag17,
        )
        val shaFinal = QuiiCrypto.sha256(clearHeader, body)
        val encHeader = QuiiCrypto.aesCbcEncrypt(key, clearHeader)
        val encBody = QuiiCrypto.aesCbcEncrypt(key, body + shaFinal)
        return encHeader + encBody
    }

    fun buildKeepalive(key: ByteArray, timestampSecs: Long = System.currentTimeMillis() / 1000): ByteArray =
        encryptPacket(TYPE_KEEPALIVE, ByteArray(0), key, timestamp = timestampSecs)

    fun decodeBlob(blob: ByteArray, key: ByteArray, offset: Int = 0): DecodedQuiiPacket {
        require(blob.size >= offset + 32) { "blob too short" }
        val encHeader = blob.copyOfRange(offset, offset + 32)
        val clearHeader = QuiiCrypto.aesCbcDecrypt(key, encHeader)
        val header = parseHeader(clearHeader)
        require(offset + 32 + header.readSize <= blob.size) { "truncated packet body" }
        val body = blob.copyOfRange(offset + 32, offset + 32 + header.readSize)
        if (header.isMedia) {
            require(header.flag15 == 0) { "encrypted media prefix unsupported" }
            var plainBody = body
            if (header.commandPayloadSize > 0) {
                plainBody = QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, header.commandPayloadSize)
            }
            val media = plainBody.copyOfRange(header.mediaPayloadOffset, plainBody.size)
            return DecodedQuiiPacket(header, null, media, 32 + header.readSize)
        }
        val decrypted = QuiiCrypto.aesCbcDecrypt(key, body)
        val commandBytes = if (header.rawSize > 0) decrypted.copyOfRange(0, header.rawSize) else ByteArray(0)
        val shaLen = 32
        val shaAt = if (decrypted.size >= header.rawSize + shaLen) {
            decrypted.copyOfRange(header.rawSize, header.rawSize + shaLen)
        } else {
            decrypted.copyOfRange(0, shaLen)
        }
        val actualSha = QuiiCrypto.sha256(clearHeader, commandBytes)
        if (!shaAt.contentEquals(actualSha)) {
            throw IllegalStateException("QUII command SHA mismatch type=0x${header.packetType.toString(16)}")
        }
        return DecodedQuiiPacket(header, commandBytes, null, 32 + header.readSize, clearHeader)
    }

    fun decodeChained(blob: ByteArray, key: ByteArray, offset: Int = 0): List<DecodedQuiiPacket> {
        val out = mutableListOf<DecodedQuiiPacket>()
        var pos = offset
        while (pos + 32 <= blob.size) {
            val packet = try {
                decodeBlob(blob, key, pos)
            } catch (_: Exception) {
                break
            }
            out.add(packet)
            pos += packet.totalLength
            if (packet.totalLength <= 0) break
        }
        return out
    }

    private fun writeU16Le(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun writeU64Le(dst: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) dst[off + i] = ((v shr (8 * i)) and 0xFF).toByte()
    }

    private fun readU16Le(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or ((src[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32Le(src: ByteArray, off: Int): Int =
        readU16Le(src, off) or (readU16Le(src, off + 2) shl 16)

    private fun readU64Le(src: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (src[off + i].toLong() and 0xFF)
        return v
    }
}
