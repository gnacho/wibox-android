package dev.nacho.wibox.core.cloud.quii

/**
 * Presentation timestamps for the live H264 decoder.
 *
 * The doorbell runs at 20 fps, but the consumer advanced the PTS by a hardcoded
 * 40 ms (25 fps) per queued frame: playback ran ahead of real time and the error
 * accumulated over a session. The clock here uses the frame rate the stream
 * announces in its cpacket and keeps counting on the frames that never reach the
 * decoder (skipped while waiting for a keyframe), so the timeline is not compressed.
 */
class VideoPtsClock(private val fallbackFps: Float = DEFAULT_FPS) {

    /** PTS of the last frame handed out, in microseconds. */
    var lastPtsUs = 0L
        private set

    private var nextPtsUs = 0L

    fun reset() {
        lastPtsUs = 0
        nextPtsUs = 0
    }

    /**
     * PTS for the next frame, in microseconds from the first frame of the session.
     * [fps] is the frame rate announced by the cpacket, or 0 when the header does not
     * carry one; anything outside a sane range falls back to the measured stream rate.
     */
    fun next(fps: Float): Long {
        lastPtsUs = nextPtsUs
        nextPtsUs += frameDurationUs(fps)
        return lastPtsUs
    }

    /** Microseconds per frame for [fps]; 20 fps (50 ms) when the value is unusable. */
    fun frameDurationUs(fps: Float): Long {
        val rate = if (fps.isFinite() && fps in MIN_FPS..MAX_FPS) fps else fallbackFps
        return (1_000_000.0 / rate).toLong()
    }

    companion object {
        /** Measured rate of the doorbell stream when the cpacket says nothing. */
        const val DEFAULT_FPS = 20f
        const val MIN_FPS = 1f
        const val MAX_FPS = 60f
    }
}
