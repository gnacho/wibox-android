package dev.nacho.wibox.externalapi.server

/** Simple token bucket used for per-uid read and action rate limits. */
class TokenBucket(
    private val capacity: Int,
    refillPerMinute: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val refillIntervalMs: Double = 60_000.0 / refillPerMinute.toDouble()
    private var tokens = capacity.toDouble()
    private var lastRefill = nowMs()

    /**
     * Returns null when a token was taken, or a bounded retryAfterMs hint in
     * milliseconds when the bucket is empty.
     */
    @Synchronized
    fun tryAcquire(): Long? {
        val now = nowMs()
        val elapsed = (now - lastRefill).coerceAtLeast(0)
        tokens = (tokens + elapsed / refillIntervalMs).coerceAtMost(capacity.toDouble())
        lastRefill = now
        return if (tokens >= 1.0) {
            tokens -= 1.0
            null
        } else {
            (((1.0 - tokens) * refillIntervalMs).toLong() + 1L).coerceAtMost(60_000L)
        }
    }
}
