package com.spendwise.transactions

import com.spendwise.parser.Classification

/**
 * Predicate governing which `sms_log` rows may be purged by the user's
 * "Remove non-financial SMSes" Settings action.
 *
 * Per ingestion-spec.md §6.1 + §6.2:
 *   - Eligible: NON_FINANCIAL, OTP, BALANCE_ALERT.
 *   - Eligible: UNMATCHED from a *non-financial-looking* sender.
 *   - Never eligible: anything that contributed to a transaction
 *     (the caller checks `transaction_sms_sources` separately — that's
 *     a DB concern, not something this predicate can see).
 *   - Never eligible: UNMATCHED from a financial-looking sender —
 *     those are the highest-priority parser gap signal per plan §2.
 */
object PurgeEligibility {

    /**
     * Heuristic for "financial-looking sender". Matches the same
     * prefixes the UNMATCHED-financial monitoring hook watches for
     * in parser-spec.md §6.
     */
    private val FINANCIAL_SENDER_SUBSTR = listOf(
        "KOTAKB", "AXISBK", "ICICI", "HDFC", "SBI", "UPI",
    )

    /**
     * @return true if the SMS's classification + sender combination
     *         makes it safe to delete on user request.
     */
    fun isPurgeEligible(classification: Classification, sender: String): Boolean =
        when (classification) {
            Classification.NON_FINANCIAL,
            Classification.OTP,
            Classification.BALANCE_ALERT -> true

            Classification.UNMATCHED -> !isFinancialLookingSender(sender)

            // Spend / refund / income / bill-payment / cashback / self-transfer
            // all preserve data that could feed transactions analytics, even
            // if the row itself is DROP-effect. Keep.
            else -> false
        }

    private fun isFinancialLookingSender(sender: String): Boolean =
        FINANCIAL_SENDER_SUBSTR.any { it in sender }
}
