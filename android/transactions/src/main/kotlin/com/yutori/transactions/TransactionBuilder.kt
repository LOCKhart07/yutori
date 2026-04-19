package com.yutori.transactions

import com.yutori.classifier.BudgetEffect
import com.yutori.transactions.internal.DedupMatcher
import com.yutori.transactions.internal.PrimarySelector
import com.yutori.transactions.internal.RoleAssigner

/**
 * The transactions layer — single pure-function entry point.
 *
 * Takes one classified SMS event plus the current `transactions` +
 * `transaction_sms_sources` state, returns the updated state and a
 * [MergeDecision] describing what happened.
 *
 * Implements business-logic-spec.md §4 (single-SMS flow, dedup merge,
 * role assignment, primary promotion).
 */
object TransactionBuilder {

    /**
     * Apply a single [event] to the existing state.
     *
     * @param idAllocator called only when creating a new transaction;
     *        the caller owns id assignment (likely a DB AUTOINCREMENT
     *        in the real storage layer).
     */
    fun apply(
        event: IncomingEvent,
        transactions: List<TransactionRow>,
        sources: List<TransactionSource>,
        idAllocator: () -> Long,
    ): BuilderResult {
        // DROP events never enter `transactions`. The sms_log row still
        // exists (ingestion already stored it); it just has no tx row.
        if (event.outcome.budgetEffect == BudgetEffect.DROP) {
            return BuilderResult(
                transactions = transactions,
                sources = sources,
                decision = MergeDecision.Dropped(
                    reason = "budget_effect=DROP (classification=" +
                        "${event.outcome.finalClassification})",
                ),
            )
        }

        val candidate = DedupMatcher.findCandidate(event, transactions)
        return if (candidate == null) {
            createNew(event, transactions, sources, idAllocator)
        } else {
            mergeInto(candidate, event, transactions, sources)
        }
    }

    // --- create path -------------------------------------------------------

    private fun createNew(
        event: IncomingEvent,
        transactions: List<TransactionRow>,
        sources: List<TransactionSource>,
        idAllocator: () -> Long,
    ): BuilderResult {
        val txId = idAllocator()
        val txRow = buildTransactionRow(event, txId)
        val role = RoleAssigner.roleFor(event.parserPattern)
        val source = TransactionSource(
            transactionId = txId,
            smsLogId = event.smsLogId,
            role = role,
            isPrimary = true,   // first source is always primary
        )
        return BuilderResult(
            transactions = transactions + txRow,
            sources = sources + source,
            decision = MergeDecision.CreatedNew(transactionId = txId),
        )
    }

    private fun buildTransactionRow(event: IncomingEvent, id: Long): TransactionRow {
        val outcome = event.outcome
        val isForex = outcome.currency != "INR"
        val inrAmount = if (isForex) null else outcome.amount
        val originalAmount = if (isForex) outcome.amount else null
        val rateSource = if (isForex) "pending" else null
        return TransactionRow(
            id = id,
            classification = outcome.finalClassification,
            classificationOriginal = outcome.classificationOriginal,
            budgetEffect = outcome.budgetEffect,
            inrAmount = inrAmount,
            originalAmount = originalAmount,
            originalCurrency = outcome.currency,
            rateSource = rateSource,
            merchant = outcome.merchant,
            merchantKey = outcome.merchantKey,
            category = outcome.category,
            accountId = outcome.accountId,
            last4 = outcome.last4,
            issuer = event.issuer,
            occurredAtMs = event.occurredAtMs,
            monthKey = event.monthKey,
            classificationInferred = outcome.classificationInferred,
            categoryInferred = outcome.categoryInferred,
        )
    }

    // --- merge path --------------------------------------------------------

    private fun mergeInto(
        candidate: TransactionRow,
        event: IncomingEvent,
        transactions: List<TransactionRow>,
        sources: List<TransactionSource>,
    ): BuilderResult {
        val newRole = RoleAssigner.roleFor(event.parserPattern)
        val currentPrimary = sources.single {
            it.transactionId == candidate.id && it.isPrimary
        }
        val promote = PrimarySelector.shouldPromote(newRole, currentPrimary)

        val newSource = TransactionSource(
            transactionId = candidate.id,
            smsLogId = event.smsLogId,
            role = newRole,
            isPrimary = promote,
        )

        val updatedSources = if (promote) {
            sources.map { src ->
                if (src.transactionId == candidate.id && src.isPrimary) {
                    src.copy(isPrimary = false)
                } else {
                    src
                }
            } + newSource
        } else {
            sources + newSource
        }

        // If we promoted, the new primary may have a better merchant
        // string. Per §4.3 rule 3, rewrite merchant / merchantKey when
        // the new primary's merchant is non-null AND longer than what's
        // currently there. Amount / occurredAtMs / classification are
        // NEVER changed on merge (§4.3 rule 4).
        val updatedTransactions = if (promote) {
            transactions.map { row ->
                if (row.id != candidate.id) row
                else maybeUpgradeMerchant(row, event)
            }
        } else {
            transactions
        }

        return BuilderResult(
            transactions = updatedTransactions,
            sources = updatedSources,
            decision = MergeDecision.MergedInto(
                transactionId = candidate.id,
                primaryPromoted = promote,
            ),
        )
    }

    private fun maybeUpgradeMerchant(
        row: TransactionRow,
        event: IncomingEvent,
    ): TransactionRow {
        val newMerchant = event.outcome.merchant
        val currentMerchant = row.merchant
        val shouldReplace = newMerchant != null &&
            (currentMerchant == null || newMerchant.length > currentMerchant.length)
        return if (shouldReplace) {
            row.copy(
                merchant = newMerchant,
                merchantKey = event.outcome.merchantKey,
            )
        } else {
            row
        }
    }
}
