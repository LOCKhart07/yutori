package com.spendwise.transactions

/**
 * The pure-function output of [TransactionBuilder.apply]. Contains
 * the updated `transactions` + `transaction_sms_sources` snapshots,
 * plus a [decision] describing what happened.
 *
 * The caller is responsible for persisting these; this module never
 * writes to storage.
 */
data class BuilderResult(
    val transactions: List<TransactionRow>,
    val sources: List<TransactionSource>,
    val decision: MergeDecision,
)
