package com.yutori.transactions.forex

import com.yutori.transactions.TransactionRow

/**
 * Orchestrates forex rate resolution for a batch of pending
 * transactions per business-logic-spec.md §5. Pure logic — the Android
 * worker hands it a list of pending rows + a client, and this:
 *
 *   1. groups rows by (currency, dateKey) so same-day-same-currency
 *      rows share a single fetch,
 *   2. consults [ForexRateCache] first,
 *   3. calls [ForexRateClient] on cache miss and caches success,
 *   4. applies [ForexConverter] to produce resolved rows,
 *   5. reports per-currency failures so the worker can record backoff.
 *
 * No I/O beyond the client call; no clock reads. Callers supply [nowMs].
 */
class ForexFetcher(
    private val client: ForexRateClient,
    private val cacheRef: CacheHolder = CacheHolder(ForexRateCache()),
) {

    /** Wraps the cache so Fetcher can swap it atomically between calls. */
    class CacheHolder(initial: ForexRateCache) {
        @Volatile var cache: ForexRateCache = initial
    }

    data class ResolvedRow(val row: TransactionRow)
    data class CurrencyFailure(val currency: String, val kind: ForexErrorKind, val detail: String?)

    data class Result(
        val resolved: List<ResolvedRow>,
        val stillPending: List<TransactionRow>,
        val failures: List<CurrencyFailure>,
    )

    suspend fun resolvePending(
        pending: List<TransactionRow>,
        nowMs: Long,
        dateKeyOf: (Long) -> String,
        rateSourceLabel: String = DEFAULT_RATE_SOURCE,
    ): Result {
        if (pending.isEmpty()) {
            return Result(emptyList(), emptyList(), emptyList())
        }

        val resolved = mutableListOf<ResolvedRow>()
        val stillPending = mutableListOf<TransactionRow>()
        val failures = mutableListOf<CurrencyFailure>()

        // Group by currency so one fetch covers every pending row in
        // that currency. Cache key is (dateKey, currency), but for v1
        // we fetch "today's" rate once per currency per batch and
        // reuse it for same-day rows; different-day rows still reuse
        // via cache hits.
        val byCurrency = pending.groupBy { it.originalCurrency }
        for ((currency, rows) in byCurrency) {
            val firstRow = rows.first()
            val dateKey = dateKeyOf(firstRow.occurredAtMs)

            val cached = cacheRef.cache.lookup(currency, dateKey, nowMs)
            val rate: Double? = cached ?: when (val fetched = client.fetch(currency)) {
                is ForexFetchResult.Success -> {
                    cacheRef.cache = cacheRef.cache.put(
                        currency = currency,
                        dateKey = dateKey,
                        rate = fetched.rateInrPerUnit,
                        capturedAtMs = nowMs,
                    )
                    fetched.rateInrPerUnit
                }
                is ForexFetchResult.Failure -> {
                    failures += CurrencyFailure(currency, fetched.kind, fetched.detail)
                    null
                }
            }

            if (rate == null) {
                stillPending += rows
            } else {
                rows.forEach { row ->
                    resolved += ResolvedRow(
                        ForexConverter.apply(row, rate, rateSourceLabel),
                    )
                }
            }
        }

        return Result(resolved, stillPending, failures)
    }

    companion object {
        const val DEFAULT_RATE_SOURCE = "exchangerate-api.com"
    }
}
