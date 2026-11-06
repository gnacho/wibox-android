package dev.nacho.wibox.core.cloud.quii

import java.io.ByteArrayOutputStream

/**
 * In-band H.264 parameter set handling.
 *
 * The decoder is configured out of band with csd-0 and csd-1, which carry the SPS
 * and PPS that match the stream. The doorbell also repeats its own SPS and PPS
 * inside every keyframe, and that SPS describes a different stream (Main profile
 * level 1.3 with a frame_num width that its own slices do not use). Feeding it
 * back to MediaCodec makes the keyframe fail to decode, so the parameter sets the
 * device sends in band are removed before queueing the bitstream.
 */
object H264ParamSets {
    private const val NAL_TYPE_SPS = 7
    private const val NAL_TYPE_PPS = 8

    /**
     * Returns [bitstream] without its NAL units of type 7 and 8, keeping every
     * other NAL and its Annex-B start code untouched.
     *
     * A buffer without Annex-B start codes is returned unchanged, since there is
     * nothing to delimit a parameter set with.
     */
    fun stripParamSets(bitstream: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bitstream.size)
        var pos = 0
        var foundStartCode = false
        while (pos < bitstream.size) {
            val startCodeLen = startCodeLengthAt(bitstream, pos)
            if (startCodeLen == 0) {
                pos++
                continue
            }
            foundStartCode = true
            val nal = pos + startCodeLen
            if (nal >= bitstream.size) break
            val next = nextStartCode(bitstream, nal + 1)
            val nalType = bitstream[nal].toInt() and 0x1F
            if (nalType != NAL_TYPE_SPS && nalType != NAL_TYPE_PPS) {
                out.write(bitstream, pos, next - pos)
            }
            pos = next
        }
        return if (foundStartCode) out.toByteArray() else bitstream
    }

    private fun startCodeLengthAt(src: ByteArray, pos: Int): Int = when {
        pos + 3 <= src.size && src[pos] == 0.toByte() && src[pos + 1] == 0.toByte() && src[pos + 2] == 1.toByte() -> 3
        pos + 4 <= src.size && src[pos] == 0.toByte() && src[pos + 1] == 0.toByte() && src[pos + 2] == 0.toByte() &&
            src[pos + 3] == 1.toByte() -> 4
        else -> 0
    }

    private fun nextStartCode(src: ByteArray, from: Int): Int {
        var pos = from
        while (pos + 3 <= src.size) {
            if (src[pos] == 0.toByte() && src[pos + 1] == 0.toByte()) {
                val threeBytes = src[pos + 2] == 1.toByte()
                val fourBytes = src[pos + 2] == 0.toByte() && pos + 4 <= src.size && src[pos + 3] == 1.toByte()
                if (threeBytes || fourBytes) return pos
            }
            pos++
        }
        return src.size
    }
}
