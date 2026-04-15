package com.spendwise.transactions.internal

import com.spendwise.classifier.BudgetEffect
import com.spendwise.transactions.IncomingEvent
import com.spendwise.transactions.TransactionRow
import kotlin.math.abs

/**
 * Finds an existing [TransactionRow] that the given [IncomingEvent]
 * should merge into. Implements the candidate rule from
 * business-logic-spec.md §4.2:
 *
 *   1. Amount within 0.5 INR (absolute, forex-excluded — pending rows
 *      have inrAmount = null and are never dedup candidates).
 *   2. Time within 5 minutes (±300_000 ms).
 *   3. Same [BudgetEffect] — SPEND/REFUND/INCOME can never cross.
 *   4. At least one of: same last4, OR merchantKey token overlap.
 *
 * Disambiguation when multiple candidates match:
 *   - Prefer same last4.
 *   - Then prefer earliest occurredAtMs.
 */
internal object DedupMatcher {

    private const val AMOUNT_TOLERANCE_INR = 0.5
    private const val TIME_WINDOW_MS = 5L * 60L * 1000L

    fun findCandidate(
        event: IncomingEvent,
        transactions: List<TransactionRow>,
    ): TransactionRow? {
        val eventInr = event.outcome.amount ?: return null
        if (event.outcome.currency != "INR") return null  // forex is merged later
        if (event.outcome.budgetEffect !in mergeableEffects) return null

        val eligible = transactions.filter { isCandidate(event, eventInr, it) }
        if (eligible.isEmpty()) return null
        if (eligible.size == 1) return eligible.single()

        // Disambiguation: same-last4 first, then earliest occurredAt.
        val sameLast4 = eligible.filter {
            it.last4 != null && event.outcome.last4 != null && it.last4 == event.outcome.last4
        }
        val pool = if (sameLast4.isNotEmpty()) sameLast4 else eligible
        return pool.minBy { it.occurredAtMs }
    }

    private fun isCandidate(
        event: IncomingEvent,
        eventInr: Double,
        existing: TransactionRow,
    ): Boolean {
        if (existing.budgetEffect != event.outcome.budgetEffect) return false
        val existingInr = existing.inrAmount ?: return false
        if (abs(existingInr - eventInr) > AMOUNT_TOLERANCE_INR) return false
        if (abs(existing.occurredAtMs - event.occurredAtMs) > TIME_WINDOW_MS) return false
        return matchesByLast4OrMerchantToken(event, existing)
    }

    /**
     * Per plan §12.5 (observed 2026-04-15): "same last4" alone isn't
     * enough when both sides have a merchant and those merchants
     * differ — two distinct UPI payments from the same account for
     * the same amount would collapse into one transaction.
     *
     * Refined predicate:
     *   - Both sides have a merchant → require token overlap.
     *     Same-last4 doesn't override: a pair of same-account
     *     payments to different VPAs stays distinct.
     *   - One or both sides have no merchant → same-last4 is
     *     sufficient. This preserves the §12.3 multi-party case
     *     (bank debit + CC payment receipt for the same bill) where
     *     the CC-payment-receipt side typically has no merchant.
     *   - Neither side has a merchant AND last4 doesn't match → no
     *     match (we can't dedup on nothing).
     */
    private fun matchesByLast4OrMerchantToken(
        event: IncomingEvent,
        existing: TransactionRow,
    ): Boolean {
        val eventLast4 = event.outcome.last4
        val existingLast4 = existing.last4
        val sameLast4 = eventLast4 != null &&
            existingLast4 != null &&
            eventLast4 == existingLast4

        val eventTokens = tokens(event.outcome.merchantKey)
        val existingTokens = tokens(existing.merchantKey)
        val hasOverlap = eventTokens.intersect(existingTokens).isNotEmpty()

        val bothHaveMerchant = eventTokens.isNotEmpty() && existingTokens.isNotEmpty()

        return when {
            bothHaveMerchant -> hasOverlap
            sameLast4 -> true
            else -> hasOverlap
        }
    }

    private fun tokens(merchantKey: String?): Set<String> {
        if (merchantKey.isNullOrBlank()) return emptySet()
        return merchantKey
            .lowercase()
            .split(Regex("[^a-z0-9@.]+"))
            .asSequence()
            .filter { it.length >= 2 }    // drop single-char noise
            .toSet()
    }

    private val mergeableEffects = setOf(
        BudgetEffect.SPEND,
        BudgetEffect.REFUND,
        BudgetEffect.INCOME,
    )
}
