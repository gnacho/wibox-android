package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
