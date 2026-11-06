package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPtsClockTest {

    @Test
    fun `a 20 fps stream advances 50 ms per frame`() {
        val clock = VideoPtsClock()
        assertEquals(0L, clock.next(20f))
        assertEquals(50_000L, clock.next(20f))
        assertEquals(100_000L, clock.next(20f))
        assertEquals(100_000L, clock.lastPtsUs)
    }

    @Test
    fun `a 25 fps stream advances 40 ms per frame`() {
        val clock = VideoPtsClock()
        assertEquals(0L, clock.next(25f))
        assertEquals(40_000L, clock.next(25f))
    }

    @Test
    fun `a header without fps falls back to the measured 20 fps`() {
        val clock = VideoPtsClock()
        assertEquals(0L, clock.next(0f))
        assertEquals(50_000L, clock.next(0f))
    }

    @Test
    fun `an insane fps falls back to the measured rate`() {
        val clock = VideoPtsClock()
        assertEquals(50_000L, clock.frameDurationUs(-5f))
        assertEquals(50_000L, clock.frameDurationUs(1000f))
        assertEquals(50_000L, clock.frameDurationUs(Float.NaN))
        assertEquals(50_000L, clock.frameDurationUs(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `timestamps stay monotonic when the frame rate changes`() {
        val clock = VideoPtsClock()
        var previous = -1L
        var fps = 20f
        repeat(60) { step ->
            if (step == 30) fps = 15f
            val pts = clock.next(fps)
            assertTrue(pts > previous, "pts $pts should be greater than $previous")
            previous = pts
        }
    }

    @Test
    fun `a skipped frame still advances the clock`() {
        val clock = VideoPtsClock()
        // Solo se entrega uno de cada tres frames: el PTS no se comprime.
        val delivered = (0 until 9).map { index -> clock.next(20f) }.filterIndexed { index, _ -> index % 3 == 0 }
        assertEquals(listOf(0L, 150_000L, 300_000L), delivered)
    }

    @Test
    fun `reset restarts the session`() {
        val clock = VideoPtsClock()
        clock.next(20f)
        clock.next(20f)
        clock.reset()
        assertEquals(0L, clock.next(20f))
        assertEquals(0L, clock.lastPtsUs)
    }

    @Test
    fun `the first timestamp is zero, not an epoch value`() {
        val clock = VideoPtsClock()
        assertEquals(0L, clock.next(20f))
    }
}
