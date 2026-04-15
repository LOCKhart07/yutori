package com.spendwise.transactions

/**
 * A `transaction_sms_sources` join-table row: links a
 * [TransactionRow] to a contributing `sms_log` row with its [role]
 * and whether it's the primary (authoritative) source.
 *
 * Per schema.md: primary key is `(transactionId, smsLogId)`. Exactly
 * one row per [transactionId] has `isPrimary == true`
 * (business-logic-spec.md §9.3 invariant).
 */
data class TransactionSource(
    val transactionId: Long,
    val smsLogId: Long,
    val role: SourceRole,
    val isPrimary: Boolean,
)
