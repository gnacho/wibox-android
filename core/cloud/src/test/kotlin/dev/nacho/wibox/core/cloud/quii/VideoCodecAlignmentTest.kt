package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCodecAlignmentTest {

    private val sps = byteArrayOf(0x47.toByte(), 0x4D.toByte(), 0x40.toByte(), 0x1E.toByte())
    private val pps = byteArrayOf(0x48.toByte(), 0xEE.toByte(), 0x3C.toByte(), 0x80.toByte())

    private fun sig(
        width: Int = 352,
        height: Int = 288,
        spsBytes: ByteArray = sps,
        ppsBytes: ByteArray = pps,
    ) = VideoCodecAlignment.Signature(spsBytes, ppsBytes, width, height)

    private fun alignment() = VideoCodecAlignment(maxRebuilds = 3).also { it.reset(sig()) }

    @Test
    fun `matching parameter sets and size do not trigger a rebuild`() {
        val guard = alignment()
        assertEquals(VideoCodecAlignment.Decision.ALREADY_ALIGNED, guard.decide(sig()))
        assertEquals(0, guard.rebuildCount)
    }

    @Test
    fun `equal parameter sets with a different size trigger a rebuild`() {
        val guard = alignment()
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(sig(width = 640, height = 480)))
    }

    @Test
    fun `different parameter sets trigger a rebuild`() {
        val guard = alignment()
        val other = sig(spsBytes = byteArrayOf(0x47, 0x4D, 0x40, 0x1F))
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(other))
    }

    @Test
    fun `a failed rebuild is not retried with the same input`() {
        val guard = alignment()
        val incoming = sig(width = 640, height = 480)
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(incoming))
        guard.onRebuildFailed(incoming)
        // El keyframe siguiente repite exactamente los mismos parametros.
        assertEquals(VideoCodecAlignment.Decision.RETRY_SUPPRESSED, guard.decide(incoming))
        assertEquals(VideoCodecAlignment.Decision.RETRY_SUPPRESSED, guard.decide(sig(width = 640, height = 480)))
        // Un stream distinto vuelve a intentarlo.
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(sig(width = 704, height = 576)))
    }

    @Test
    fun `a successful rebuild keeps the new signature and clears the failure`() {
        val guard = alignment()
        val incoming = sig(width = 640, height = 480)
        guard.onRebuildFailed(incoming)
        assertEquals(VideoCodecAlignment.Decision.RETRY_SUPPRESSED, guard.decide(incoming))
        val other = sig(width = 704, height = 576)
        guard.onRebuilt(other)
        assertEquals(1, guard.rebuildCount)
        assertEquals(VideoCodecAlignment.Decision.ALREADY_ALIGNED, guard.decide(other))
        assertTrue(guard.matchesConfigured(sps, pps))
        // El fallo anterior ya no aplica.
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(sig(width = 640, height = 480)))
    }

    @Test
    fun `the rebuild budget caps the session`() {
        val guard = alignment()
        repeat(3) { step ->
            guard.onRebuilt(sig(width = 352 + step, height = 288 + step))
        }
        assertEquals(3, guard.rebuildCount)
        assertEquals(VideoCodecAlignment.Decision.CAPPED, guard.decide(sig(width = 800, height = 600)))
        // Repetir los parametros ya configurados nunca se considera un rebuild.
        assertEquals(VideoCodecAlignment.Decision.ALREADY_ALIGNED, guard.decide(sig(width = 354, height = 290)))
    }

    @Test
    fun `reset starts a new session budget`() {
        val guard = alignment()
        repeat(3) { step -> guard.onRebuilt(sig(width = 352 + step, height = 288)) }
        assertEquals(VideoCodecAlignment.Decision.CAPPED, guard.decide(sig(width = 900, height = 600)))
        guard.reset(sig())
        assertEquals(0, guard.rebuildCount)
        assertEquals(VideoCodecAlignment.Decision.REBUILD, guard.decide(sig(width = 900, height = 600)))
    }

    @Test
    fun `matchesConfigured compares bytes not references`() {
        val guard = alignment()
        assertTrue(guard.matchesConfigured(sps.copyOf(), pps.copyOf()))
        assertFalse(guard.matchesConfigured(sps, byteArrayOf(0x00)))
    }

    @Test
    fun `signatures compare their byte content`() {
        assertEquals(sig(), sig(spsBytes = sps.copyOf(), ppsBytes = pps.copyOf()))
        assertEquals(sig().hashCode(), sig(spsBytes = sps.copyOf(), ppsBytes = pps.copyOf()).hashCode())
        assertFalse(sig() == sig(width = 353))
    }
}
