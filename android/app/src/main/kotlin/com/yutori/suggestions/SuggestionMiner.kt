package com.yutori.suggestions

import com.yutori.classifier.RecipientRuleMatching
import com.yutori.classifier.suggestions.InferredSuggestion
import com.yutori.classifier.suggestions.SuggestionCandidate
import com.yutori.classifier.suggestions.SuggestionInference
import com.yutori.database.dao.AccountDao
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.RuleSuggestionDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.database.mappers.AccountMapper
import com.yutori.database.mappers.RecipientRuleMapper
import java.util.concurrent.TimeUnit

/**
 * Mines `transactions` for repeat merchants not yet covered by an enabled
 * recipient rule and upserts [RuleSuggestionEntity] rows via
 * [RuleSuggestionDao]. Pure logic — no Android dependencies.
 *
 * Spec: plans/suggestions-spec.md §3.
 *
 * Originally placed in `:transactions` in the spec, moved to `:app` because
 * `:transactions` sits upstream of `:database` in the module graph and can't
 * see DAOs. Inference stays pure in `:classifier`.
 */
class SuggestionMiner(
    private val transactionDao: TransactionDao,
    private val ruleSuggestionDao: RuleSuggestionDao,
    private val recipientRuleDao: RecipientRuleDao,
    private val accountDao: AccountDao,
) {
    suspend fun runOnce(nowMs: Long): RunReport {
        val cutoff = nowMs - CANDIDATE_WINDOW_MS
        val staleBefore = nowMs - STALE_PRUNE_MS

        ruleSuggestionDao.pruneStaleActive(staleBefore)

        val accounts = accountDao.getAll().map(AccountMapper::toDomain)
        val enabledRules = recipientRuleDao.getEnabled().map(RecipientRuleMapper::toDomain)

        val candidates = transactionDao.aggregateSuggestionCandidates(
            cutoffMs = cutoff,
            threshold = MIN_MATCH_COUNT,
            limit = CANDIDATE_LIMIT,
        )

        var considered = 0
        var inserted = 0
        var updated = 0
        var resurfaced = 0
        var skippedDismissed = 0

        for (row in candidates) {
            considered++
            if (RecipientRuleMatching.isCovered(row.merchantKey, enabledRules)) continue

            val inferred = SuggestionInference.infer(
                SuggestionCandidate(row.merchantKey, row.matchCount, row.totalInr),
                accounts,
                enabledRules,
            )

            val existing = ruleSuggestionDao.getByMerchantKey(row.merchantKey)
            when {
                existing == null -> {
                    ruleSuggestionDao.insert(newEntity(row, inferred, nowMs))
                    inserted++
                }
                existing.dismissedAtMs == null -> {
                    ruleSuggestionDao.updateOnRescan(
                        id = existing.id,
                        pattern = inferred.pattern,
                        patternKind = inferred.patternKind.name,
                        inferredClassification = inferred.inferredClassification?.name,
                        inferredAccountId = inferred.inferredAccountId,
                        reasonCode = inferred.reasonCode.name,
                        matchCount = row.matchCount,
                        totalInr = row.totalInr,
                        lastScannedMs = nowMs,
                    )
                    updated++
                }
                row.matchCount >= existing.matchCount * RESURFACE_MULTIPLIER -> {
                    // Dismissed, but activity has doubled since dismissal — bring it back.
                    ruleSuggestionDao.clearDismissed(existing.id)
                    ruleSuggestionDao.updateOnRescan(
                        id = existing.id,
                        pattern = inferred.pattern,
                        patternKind = inferred.patternKind.name,
                        inferredClassification = inferred.inferredClassification?.name,
                        inferredAccountId = inferred.inferredAccountId,
                        reasonCode = inferred.reasonCode.name,
                        matchCount = row.matchCount,
                        totalInr = row.totalInr,
                        lastScannedMs = nowMs,
                    )
                    resurfaced++
                }
                else -> {
                    // Dismissed and below resurface threshold — leave the row frozen
                    // (don't bump lastScannedMs either; pruning already ignores dismissed).
                    skippedDismissed++
                }
            }
        }

        return RunReport(
            candidatesConsidered = considered,
            inserted = inserted,
            updated = updated,
            resurfaced = resurfaced,
            skippedDismissed = skippedDismissed,
        )
    }

    private fun newEntity(
        row: com.yutori.database.dao.MerchantAggregateRow,
        inferred: InferredSuggestion,
        nowMs: Long,
    ): RuleSuggestionEntity = RuleSuggestionEntity(
        merchantKey = row.merchantKey,
        pattern = inferred.pattern,
        patternKind = inferred.patternKind.name,
        inferredClassification = inferred.inferredClassification?.name,
        inferredAccountId = inferred.inferredAccountId,
        reasonCode = inferred.reasonCode.name,
        matchCount = row.matchCount,
        totalInr = row.totalInr,
        firstSeenMs = nowMs,
        lastScannedMs = nowMs,
    )

    data class RunReport(
        val candidatesConsidered: Int,
        val inserted: Int,
        val updated: Int,
        val resurfaced: Int,
        val skippedDismissed: Int,
    )

    companion object {
        /** Minimum occurrences for a merchant to be eligible. */
        const val MIN_MATCH_COUNT = 3

        /** Look-back window — only transactions within this window count toward the threshold. */
        val CANDIDATE_WINDOW_MS = TimeUnit.DAYS.toMillis(60)

        /** Active rows not seen in this long get pruned. Dismissed rows are kept regardless. */
        val STALE_PRUNE_MS = TimeUnit.DAYS.toMillis(90)

        /** Cap on how many candidates a single scan examines. */
        const val CANDIDATE_LIMIT = 50

        /** Dismissed row resurfaces when current match_count reaches this × stored match_count. */
        const val RESURFACE_MULTIPLIER = 2
    }
}
