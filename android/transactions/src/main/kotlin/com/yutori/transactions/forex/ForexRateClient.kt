package com.yutori.transactions.forex

/**
 * Boundary the forex fetcher calls to obtain a live exchange rate.
 * Concrete implementation hits exchangerate-api's free tier; tests
 * provide a deterministic fake. Kept in :transactions (pure JVM) so
 * the orchestration logic can be unit-tested without Android.
 */
interface ForexRateClient {
    /**
     * Fetch the INR-per-unit rate for [currency] (ISO-4217, e.g. "USD").
     * Implementations MUST return CURRENCY_UNKNOWN when the response
     * was OK but the currency is missing from the rate table — backoff
     * for that kind is "do not auto-retry" per business-logic-spec §5.2.
     */
    suspend fun fetch(currency: String): ForexFetchResult
}

sealed interface ForexFetchResult {
    data class Success(val rateInrPerUnit: Double) : ForexFetchResult
    data class Failure(val kind: ForexErrorKind, val detail: String? = null) : ForexFetchResult
}
