package dev.nacho.wibox.core.cloud.quii

/**
 * Guards the live H264 decoder against redundant rebuilds.
 *
 * Every keyframe repeats the SPS/PPS of the stream, so the alignment check runs once
 * per GOP. When the parameter sets and the size match what the decoder already uses
 * there is nothing to rebuild: doing it anyway throws work away and, if the rebuild
 * fails, paints the same failure in the log every GOP for the whole session.
 *
 * The last failing input is remembered as well, so a rebuild that cannot succeed with
 * these exact parameter sets is attempted once and not retried until the stream
 * announces something different.
 */
class VideoCodecAlignment(private val maxRebuilds: Int = 8) {

    /** Parameter sets and frame size the decoder has to be built with. */
    class Signature(
        val sps: ByteArray,
        val pps: ByteArray,
        val width: Int,
        val height: Int,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Signature &&
                width == other.width &&
                height == other.height &&
                sps.contentEquals(other.sps) &&
                pps.contentEquals(other.pps)

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + sps.contentHashCode()
            result = 31 * result + pps.contentHashCode()
            return result
        }

        override fun toString(): String = "${width}x$height sps=${sps.size}B pps=${pps.size}B"
    }

    enum class Decision {
        /** The decoder is already configured with these parameter sets and this size. */
        ALREADY_ALIGNED,

        /** A rebuild with these exact inputs already failed. */
        RETRY_SUPPRESSED,

        /** The per-session rebuild budget is exhausted. */
        CAPPED,

        /** Rebuild the decoder with the incoming signature. */
        REBUILD,
    }

    /** Signature the decoder is configured with right now, if it is known. */
    var configured: Signature? = null
        private set

    /** Number of successful rebuilds in the session. */
    var rebuildCount = 0
        private set

    private var failed: Signature? = null

    /** Restarts the budget and records the signature of a freshly built decoder. */
    fun reset(signature: Signature) {
        configured = signature
        failed = null
        rebuildCount = 0
    }

    fun decide(incoming: Signature): Decision {
        if (configured == incoming) return Decision.ALREADY_ALIGNED
        if (failed == incoming) return Decision.RETRY_SUPPRESSED
        if (rebuildCount >= maxRebuilds) return Decision.CAPPED
        return Decision.REBUILD
    }

    /** Records a decoder built with [signature]; a previous failure stops being relevant. */
    fun onRebuilt(signature: Signature) {
        configured = signature
        failed = null
        rebuildCount++
    }

    /** Records that a rebuild with [signature] failed, so it is not retried as is. */
    fun onRebuildFailed(signature: Signature) {
        failed = signature
    }

    /** True when in-band parameter sets are the ones the decoder was built with. */
    fun matchesConfigured(sps: ByteArray, pps: ByteArray): Boolean {
        val current = configured ?: return false
        return current.sps.contentEquals(sps) && current.pps.contentEquals(pps)
    }
}
