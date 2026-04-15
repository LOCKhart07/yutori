package com.spendwise.transactions.forex

/**
 * Why a forex rate fetch failed. Different kinds get different backoff
 * policies per business-logic-spec.md §5.2.
 */
enum class ForexErrorKind {
    /** HTTP 429 — exchangerate-api.com free tier quota exhausted. */
    QUOTA_EXHAUSTED,

    /**
     * Network unavailable, connection timeout, 5xx, or the response
     * parsed to a bad rate (0 / NaN / negative). Recoverable; backs
     * off progressively.
     */
    TRANSIENT,

    /**
     * The API returned OK but the requested currency wasn't in its
     * response. Keeps the transaction pending indefinitely; only a
     * manual user entry or a different rate source resolves it.
     */
    CURRENCY_UNKNOWN,
}
