package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H264ParamSetsTest {
    @Test
    fun `drops sps and pps before the idr with four byte start codes`() {
        val sps = "00000001674d400ddc16096c"
        val pps = "0000000168ee3c80"
        val idr = "00000001658884"
        val stripped = H264ParamSets.stripParamSets(hex(sps + pps + idr))
        assertEquals(hex(idr).toList(), stripped.toList())
    }

    @Test
    fun `drops sps and pps with three byte start codes`() {
        val sps = "000001674d400d"
        val pps = "00000168ee3c80"
        val idr = "000001658884"
        val stripped = H264ParamSets.stripParamSets(hex(sps + pps + idr))
        assertEquals(hex(idr).toList(), stripped.toList())
    }

    @Test
    fun `keeps slice and sei nal units around the parameter sets`() {
        val sei = "0000000106091234"
        val sps = "000001674d400d"
        val pps = "0000000168ee3c80"
        val slice = "0000000141aabb"
        val stripped = H264ParamSets.stripParamSets(hex(sps + sei + pps + slice))
        assertEquals(hex(sei + slice).toList(), stripped.toList())
    }

    @Test
    fun `bitstream without parameter sets is returned unchanged`() {
        val input = hex("00000001658884" + "0000000141aabb")
        val stripped = H264ParamSets.stripParamSets(input)
        assertEquals(input.toList(), stripped.toList())
    }

    @Test
    fun `bitstream with only parameter sets becomes empty`() {
        val stripped = H264ParamSets.stripParamSets(hex("000001674d400d" + "00000168ee3c80"))
        assertTrue(stripped.isEmpty())
    }

    @Test
    fun `buffer without annex b start codes is returned unchanged`() {
        val input = hex("658884aabb")
        assertEquals(input.toList(), H264ParamSets.stripParamSets(input).toList())
    }

    @Test
    fun `extracts the captured parameter sets of a keyframe`() {
        val inBand = "00000001" + capturedSps + "00000001" + capturedPps +
            "0000000145b80048083f12b311e47fce"
        val sets = H264ParamSets.extractParameterSets(hex(inBand))
        assertEquals(hex(capturedSps).toList(), sets?.sps?.toList())
        assertEquals(hex(capturedPps).toList(), sets?.pps?.toList())
    }

    @Test
    fun `extracts parameter sets sent with three byte start codes`() {
        val inBand = "000001" + capturedSps + "000001" + capturedPps + "00000145b80048083f12b311e47fce"
        val sets = H264ParamSets.extractParameterSets(hex(inBand))
        assertEquals(hex(capturedSps).toList(), sets?.sps?.toList())
        assertEquals(hex(capturedPps).toList(), sets?.pps?.toList())
    }

    @Test
    fun `drops trailing zero bytes that belong to the next start code`() {
        val inBand = "00000001" + capturedSps + "0000" + "00000001" + capturedPps + "0000" +
            "0000000145b80048083f12b311e47fce"
        val sets = H264ParamSets.extractParameterSets(hex(inBand))
        assertEquals(hex(capturedSps).toList(), sets?.sps?.toList())
        assertEquals(hex(capturedPps).toList(), sets?.pps?.toList())
    }

    @Test
    fun `a slice without in band parameter sets yields nothing`() {
        val slice = "0000000141e01d5c147fc6b9c7db9f34"
        assertNull(H264ParamSets.extractParameterSets(hex(slice)))
        assertFalse(H264ParamSets.hasParameterSets(hex(slice)))
    }

    @Test
    fun `an sps without its pps is not a usable pair`() {
        val inBand = "00000001" + capturedSps + "0000000145b80048083f12b311e47fce"
        assertNull(H264ParamSets.extractParameterSets(hex(inBand)))
        assertFalse(H264ParamSets.hasParameterSets(hex(inBand)))
    }

    @Test
    fun `has parameter sets accepts a keyframe that carries both`() {
        val inBand = "00000001" + capturedSps + "00000001" + capturedPps + "0000000145b80048083f12b311e47fce"
        assertTrue(H264ParamSets.hasParameterSets(hex(inBand)))
    }

    @Test
    fun `parameter sets compare by content`() {
        val first = H264ParamSets.extractParameterSets(hex("00000001$capturedSps" + "00000001$capturedPps"))
        val second = H264ParamSets.extractParameterSets(hex("000001$capturedSps" + "000001$capturedPps"))
        assertEquals(first, second)

        val otherPps = H264ParamSets.extractParameterSets(
            hex("00000001$capturedSps" + "0000000148ee3c81"),
        )
        assertNotEquals(first, otherPps)
    }

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        // Real bytes captured from a live session of the doorbell.
        const val capturedSps = "674d401e99a05825a100000303e800009c4084"
        const val capturedPps = "48ee3c80"
    }
}
