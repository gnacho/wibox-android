package dev.nacho.wibox.core.cloud.quii

import java.io.ByteArrayOutputStream

/**
 * In-band H.264 parameter set handling.
 *
 * The decoder is configured out of band with csd-0 and csd-1, which carry the SPS
 * and PPS that match the stream. The doorbell also repeats its own SPS and PPS
 * inside every keyframe, and its resolution changes with the negotiated session
 * (352x288 on one path, 688x576 on another), so the parameter sets a session
 * announces cannot be assumed in advance: they have to be taken from the stream.
 */
object H264ParamSets {
    private const val NAL_TYPE_SPS = 7
    private const val NAL_TYPE_PPS = 8

    /** SPS and PPS of a stream, as raw NAL units without their start codes. */
    class ParameterSets(val sps: ByteArray, val pps: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is ParameterSets && sps.contentEquals(other.sps) && pps.contentEquals(other.pps)

        override fun hashCode(): Int = 31 * sps.contentHashCode() + pps.contentHashCode()

        override fun toString(): String = "ParameterSets(sps=${sps.size}B, pps=${pps.size}B)"
    }

    /**
     * Returns the SPS and PPS carried in [bitstream], or null when either of them
     * is missing. Start codes of three and four bytes are both accepted.
     */
    fun extractParameterSets(bitstream: ByteArray): ParameterSets? {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        for (nal in scanNals(bitstream)) {
            when (nalType(bitstream, nal)) {
                NAL_TYPE_SPS -> if (sps == null) sps = nalPayload(bitstream, nal)
                NAL_TYPE_PPS -> if (pps == null) pps = nalPayload(bitstream, nal)
            }
        }
        val foundSps = sps ?: return null
        val foundPps = pps ?: return null
        return ParameterSets(foundSps, foundPps)
    }

    /** True when [bitstream] carries both an SPS and a PPS. */
    fun hasParameterSets(bitstream: ByteArray): Boolean = extractParameterSets(bitstream) != null

    /**
     * Returns [bitstream] without its NAL units of type 7 and 8, keeping every
     * other NAL and its Annex-B start code untouched.
     *
     * A buffer without Annex-B start codes is returned unchanged, since there is
     * nothing to delimit a parameter set with.
     */
    fun stripParamSets(bitstream: ByteArray): ByteArray {
        val nals = scanNals(bitstream)
        if (nals.isEmpty()) return bitstream
        val out = ByteArrayOutputStream(bitstream.size)
        for (nal in nals) {
            val type = nalType(bitstream, nal)
            if (type != NAL_TYPE_SPS && type != NAL_TYPE_PPS) {
                out.write(bitstream, nal.start, nal.end - nal.start)
            }
        }
        return out.toByteArray()
    }

    private class Nal(val start: Int, val header: Int, val end: Int)

    private fun scanNals(src: ByteArray): List<Nal> {
        val nals = ArrayList<Nal>(4)
        var pos = 0
        while (pos < src.size) {
            val startCodeLen = startCodeLengthAt(src, pos)
            if (startCodeLen == 0) {
                pos++
                continue
            }
            val header = pos + startCodeLen
            if (header >= src.size) break
            val end = nextStartCode(src, header + 1)
            nals.add(Nal(pos, header, end))
            pos = end
        }
        return nals
    }

    private fun nalType(src: ByteArray, nal: Nal): Int = src[nal.header].toInt() and 0x1F

    private fun nalPayload(src: ByteArray, nal: Nal): ByteArray {
        var end = nal.end
        // Trailing zero bytes belong to the start code separator, not the NAL.
        while (end > nal.header && src[end - 1] == 0.toByte()) end--
        return src.copyOfRange(nal.header, end)
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
