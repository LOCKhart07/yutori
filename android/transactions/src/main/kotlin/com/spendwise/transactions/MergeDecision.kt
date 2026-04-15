package com.spendwise.transactions

/**
 * Outcome of feeding one [IncomingEvent] through the transactions
 * builder. Makes the decision observable for testing and for callers
 * that log/surface per-event behavior.
 */
sealed interface MergeDecision {

    /**
     * The event produced a new transaction row. [transactionId] is the
     * id assigned to it.
     */
    data class CreatedNew(val transactionId: Long) : MergeDecision

    /**
     * The event was merged into an existing [transactionId]. A new
     * [TransactionSource] row was added. If [primaryPromoted] is true,
     * the merged source became the new primary (see §4.3 step 2); the
     * old primary's flag was flipped off.
     */
    data class MergedInto(
        val transactionId: Long,
        val primaryPromoted: Boolean,
    ) : MergeDecision

    /**
     * The event was classified DROP or UNMATCHED and does not enter
     * the transactions table. The sms_log row still exists; it just
     * has no corresponding transaction. [reason] is for logging.
     */
    data class Dropped(val reason: String) : MergeDecision
}
