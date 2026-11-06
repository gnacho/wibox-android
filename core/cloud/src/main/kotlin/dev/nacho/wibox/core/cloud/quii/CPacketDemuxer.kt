package dev.nacho.wibox.core.cloud.quii

sealed class MediaFrame {
    data class Video(
        val bitstream: ByteArray,
        val frameType: Int,
        val codec: Int,
        val width: Int,
        val height: Int,
        val fps: Float,
        val timestampMs: Long,
        val keyFrame: Boolean,
    ) : MediaFrame()

    data class Audio(
        val samples: ByteArray,
        val codec: Int,
        val sampleRate: Int,
        val channels: Int,
        val timestampMs: Long,
    ) : MediaFrame()
}

data class CPacketHeader(
    val marker: Int,
    val frameType: Int,
    val payloadLen: Int,
    val timestampMs: Long,
    val codec: Int,
    val fpsOrChannels: Int,
    val widthOrSampleRate: Int,
    val height: Int,
)

object CPacketDemuxer {
    const val AUDIO_CODEC_G711A = 4
    const val AUDIO_CODEC_G711U = 5
    const val AUDIO_CODEC_PCM = 9

    fun parseHeader(payload: ByteArray, offset: Int = 0): CPacketHeader {
        require(payload.size >= offset + 0x14) { "cpacket too short" }
        require(payload[offset] == 0.toByte() && payload[offset + 1] == 0.toByte() && payload[offset + 2] == 1.toByte()) {
            "bad cpacket magic"
        }
        val marker = payload[offset + 3].toInt() and 0xFF
        require(marker in 0xE0..0xEA) { "bad cpacket marker 0x${marker.toString(16)}" }
        return CPacketHeader(
            marker = marker,
            frameType = (marker + 0x20) and 0xFF,
            payloadLen = readU32Le(payload, offset + 4),
            timestampMs = computeTimestampMs(payload, offset),
            codec = payload[offset + 14].toInt() and 0xFF,
            fpsOrChannels = payload[offset + 15].toInt() and 0xFF,
            widthOrSampleRate = readU16Le(payload, offset + 16),
            height = readU16Le(payload, offset + 18),
        )
    }

    fun demux(payload: ByteArray): List<MediaFrame> {
        val frames = mutableListOf<MediaFrame>()
        var pos = 0
        while (pos + 0x14 <= payload.size) {
            if (payload[pos] != 0.toByte() || payload[pos + 1] != 0.toByte() || payload[pos + 2] != 1.toByte()) break
            val header = parseHeader(payload, pos)
            if (header.payloadLen <= 0) {
                // Corrupt or empty cpacket. Skip this header and keep looking for
                // the next frame instead of giving up on the whole payload.
                pos += 0x14
                continue
            }
            // The QUII header length excludes the AES zero padding, so the last
            // cpacket of a decrypted payload is truncated by up to 15 bytes. Take
            // what is available instead of dropping the frame and the rest of the
            // payload with it.
            val end = (pos.toLong() + 0x14 + header.payloadLen.toLong())
                .coerceAtMost(payload.size.toLong())
                .toInt()
            val bitstream = payload.copyOfRange(pos + 0x14, end)
            when (header.frameType) {
                0x00, 0x01, 0x09, 0x0A -> {
                    val isH264 = header.codec == 1 || header.codec in 0x10..0x1F
                    if (isH264) {
                        frames.add(
                            MediaFrame.Video(
                                bitstream = bitstream,
                                frameType = header.frameType,
                                codec = header.codec,
                                width = header.widthOrSampleRate,
                                height = header.height,
                                fps = header.fpsOrChannels / 4.0f,
                                timestampMs = header.timestampMs,
                                keyFrame = containsIdr(bitstream),
                            ),
                        )
                    }
                }
                0x03 -> frames.add(
                    MediaFrame.Audio(
                        samples = bitstream,
                        codec = header.codec,
                        sampleRate = header.widthOrSampleRate,
                        channels = if (header.fpsOrChannels == 0) 1 else header.fpsOrChannels,
                        timestampMs = header.timestampMs,
                    ),
                )
                else -> Unit
            }
            pos = end
        }
        return frames
    }

    fun containsIdr(bitstream: ByteArray): Boolean {
        var i = 0
        while (i + 4 <= bitstream.size) {
            if (bitstream[i] == 0.toByte() && bitstream[i + 1] == 0.toByte()) {
                var startCodeLen = 0
                if (bitstream[i + 2] == 1.toByte()) startCodeLen = 3
                else if (bitstream[i + 2] == 0.toByte() && i + 4 <= bitstream.size && bitstream[i + 3] == 1.toByte()) startCodeLen = 4
                if (startCodeLen > 0) {
                    val nalIndex = i + startCodeLen
                    if (nalIndex < bitstream.size) {
                        val nalType = bitstream[nalIndex].toInt() and 0x1F
                        if (nalType == 5) return true
                    }
                    i += startCodeLen + 1
                    continue
                }
            }
            i++
        }
        return false
    }

    private fun computeTimestampMs(payload: ByteArray, offset: Int): Long {
        val stamp = readU32Le(payload, offset + 8).toLong() and 0xFFFFFFFFL
        val ms = readU16Le(payload, offset + 12).toLong()
        val year = ((stamp shr 26) and 0x3FL).toInt() + 2000
        val month = ((stamp shr 22) and 0xFL).toInt()
        val day = ((stamp shr 17) and 0x1FL).toInt()
        val hour = ((stamp shr 12) and 0x1FL).toInt()
        val minute = ((stamp shr 6) and 0x3FL).toInt()
        val second = (stamp and 0x3FL).toInt()
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis + ms
    }

    private fun readU16Le(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or ((src[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32Le(src: ByteArray, off: Int): Int =
        readU16Le(src, off) or (readU16Le(src, off + 2) shl 16)
}
