package com.spendwise.transactions.forex

/**
 * In-memory rate cache per business-logic-spec.md §5.1.
 *
 * Exchange rates vary by <0.5% intra-day for major currencies, so we
 * cache per (date, currency) for 24 hours. The cache is pure: caller
 * passes in the "current time" for testability; no clock reads here.
 *
 * Storage shape (immutable lookup):
 *   key   = "YYYY-MM-DD|USD"
 *   value = (rateInrPerUnit, capturedAtMs)
 *
 * Default TTL is 24 h. A stale entry is returned only to ForexRetry
 * logic; lookup() enforces freshness.
 */
data class CachedRate(
    val rateInrPerUnit: Double,
    val capturedAtMs: Long,
)

class ForexRateCache(
    private val ttlMs: Long = TWENTY_FOUR_HOURS_MS,
    initial: Map<String, CachedRate> = emptyMap(),
) {

    private val entries = HashMap(initial)

    /**
     * Returns a cached rate if one exists and is fresher than [ttlMs].
     * @param currency ISO code like "USD". Case preserved.
     * @param dateKey  `YYYY-MM-DD` of the transaction's occurrence.
     * @param nowMs    current time; caller supplies for testability.
     */
    fun lookup(currency: String, dateKey: String, nowMs: Long): Double? {
        val entry = entries[key(currency, dateKey)] ?: return null
        return if (nowMs - entry.capturedAtMs <= ttlMs) entry.rateInrPerUnit else null
    }

    /** Write or overwrite an entry. Returns the new cache (immutable callers). */
    fun put(
        currency: String,
        dateKey: String,
        rate: Double,
        capturedAtMs: Long,
    ): ForexRateCache {
        val next = HashMap(entries)
        next[key(currency, dateKey)] = CachedRate(rate, capturedAtMs)
        return ForexRateCache(ttlMs, next)
    }

    /** Size helper for tests. */
    fun size(): Int = entries.size

    private fun key(currency: String, dateKey: String) = "$dateKey|$currency"

    companion object {
        const val TWENTY_FOUR_HOURS_MS: Long = 24L * 60 * 60 * 1000L
    }
}
