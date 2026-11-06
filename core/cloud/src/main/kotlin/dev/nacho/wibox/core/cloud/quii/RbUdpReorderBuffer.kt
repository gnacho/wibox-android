package dev.nacho.wibox.core.cloud.quii

/**
 * Small reordering queue for the RB-UDP data lane.
 *
 * The doorbell offsets each data packet with a local id that grows by the payload
 * length. The wire is UDP underneath, so packets do arrive out of order and the old
 * receiver dropped every packet that was not exactly the expected one, leaving holes
 * in the H.264 stream (measured on the reference daemon: `expected=15357
 * arrived=16241`).
 *
 * The queue holds the packets that arrive ahead of the expected offset and hands them
 * back once the gap is filled. A gap that does not fill within a bounded number of
 * packets or a short timeout is skipped, so a lost datagram costs one frame instead of
 * the rest of the session.
 */
class RbUdpReorderBuffer(
    private val maxHoldPackets: Int = 32,
    private val maxGapBytes: Int = 96 * 1024,
    private val gapTimeoutMs: Long = 200L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private class Chunk(val localId: Int, val payload: ByteArray, val receivedAtMs: Long)

    private val held = ArrayList<Chunk>()

    /** Next contiguous remote offset; null until the first packet of the stream. */
    var nextRemoteId: Int? = null
        private set

    /** Packets received out of order and delivered later. */
    var reordered = 0
        private set

    /** Packets already delivered (or already held) that arrived again. */
    var duplicates = 0
        private set

    /** Holes left behind because the missing data never arrived. */
    var gapsSkipped = 0
        private set

    fun reset() {
        held.clear()
        nextRemoteId = null
        reordered = 0
        duplicates = 0
        gapsSkipped = 0
    }

    /**
     * Offers one data payload. Returns the payloads that can be handed to the frame
     * reassembler now, in offset order, or an empty list when the packet is held or
     * dropped as a duplicate.
     */
    fun offer(localId: Int, payload: ByteArray, nowMs: Long = clockMs()): List<ByteArray> {
        val out = ArrayList<ByteArray>(2)
        val expected = nextRemoteId
        if (expected == null) {
            nextRemoteId = localId + payload.size
            out.add(payload)
            drain(out, nowMs)
            return out
        }
        val delta = localId - expected
        when {
            // Already delivered: keep the ack path honest without duplicating bytes.
            delta < 0 -> duplicates++
            delta == 0 -> {
                nextRemoteId = localId + payload.size
                out.add(payload)
            }
            held.any { it.localId == localId } -> duplicates++
            // Too far ahead or no room left to wait: drop the hole and resync here.
            delta > maxGapBytes || held.size >= maxHoldPackets -> {
                gapsSkipped++
                held.clear()
                nextRemoteId = localId + payload.size
                out.add(payload)
            }
            else -> {
                held.add(Chunk(localId, payload, nowMs))
                reordered++
            }
        }
        drain(out, nowMs)
        return out
    }

    /**
     * Hands back whatever the timeout allows. The receive loop calls this on every
     * socket timeout so a hole is skipped even when no further packet arrives.
     */
    fun pollGaps(nowMs: Long = clockMs()): List<ByteArray> {
        val out = ArrayList<ByteArray>(2)
        drain(out, nowMs)
        return out
    }

    private fun drain(out: MutableList<ByteArray>, nowMs: Long) {
        while (true) {
            val expected = nextRemoteId ?: return
            held.removeAll { it.localId - expected < 0 }
            val exact = held.indexOfFirst { it.localId == expected }
            if (exact >= 0) {
                val chunk = held.removeAt(exact)
                out.add(chunk.payload)
                nextRemoteId = chunk.localId + chunk.payload.size
                continue
            }
            if (held.isEmpty()) return
            val next = held.reduce { a, b -> if (b.localId - expected < a.localId - expected) b else a }
            if (nowMs - next.receivedAtMs < gapTimeoutMs) return
            // The hole did not fill in time: skip it and continue from here.
            gapsSkipped++
            held.remove(next)
            out.add(next.payload)
            nextRemoteId = next.localId + next.payload.size
        }
    }
}
