package com.yutori.classifier

import com.yutori.classifier.internal.AccountResolver
import com.yutori.classifier.internal.BudgetEffectMapper
import com.yutori.classifier.internal.Categorizer
import com.yutori.classifier.internal.MerchantKeyNormalizer
import com.yutori.classifier.internal.RecipientRuleMatcher
import com.yutori.classifier.internal.SelfTransferHeuristic
import com.yutori.parser.Classification
import com.yutori.parser.ParseResult

/**
 * The classifier — converts a raw [ParseResult] into a final
 * [ClassificationOutcome] by consulting the user's registered
 * [Account]s and [RecipientRule]s.
 *
 * Pure function: no DB access, no clock reads, no side effects. The
 * ingestion layer loads accounts + rules from Room and passes them in;
 * that keeps the classifier unit-testable without an Android runtime.
 *
 * Pipeline per business-logic-spec.md §2.2:
 *   1. Start with `raw = parseResult.classification`.
 *   2. If raw == UNMATCHED → emit UNMATCHED/DROP, no further work.
 *   3. Resolve `account_id` via [AccountResolver].
 *   4. Find matched recipient rule via [RecipientRuleMatcher].
 *   5. Apply [SelfTransferHeuristic] to produce `finalClassification`.
 *   6. Map finalClassification → [BudgetEffect].
 *   7. Normalize merchant to [merchantKey].
 *   8. Resolve [Category] via [Categorizer].
 *   9. Stamp `classification_original` if finalClassification != raw.
 */
object Classifier {

    /** Exposes the canonical classification→budget-effect mapping. */
    fun budgetEffectFor(classification: Classification): BudgetEffect =
        BudgetEffectMapper.effectFor(classification)

    fun classify(
        parseResult: ParseResult,
        accounts: List<Account>,
        recipientRules: List<RecipientRule>,
    ): ClassificationOutcome {
        val raw = parseResult.classification

        // Fast path for UNMATCHED — no account resolution, no rule matching,
        // no category inference. Parser gave up; so do we.
        if (raw == Classification.UNMATCHED) {
            return ClassificationOutcome(
                finalClassification = Classification.UNMATCHED,
                budgetEffect = BudgetEffect.DROP,
                amount = parseResult.amount,
                currency = parseResult.currency,
                merchant = parseResult.merchant,
                merchantKey = MerchantKeyNormalizer.normalize(parseResult.merchant),
                last4 = parseResult.last4,
                accountId = null,
                category = null,
                classificationOriginal = null,
            )
        }

        val account = AccountResolver.resolve(
            last4 = parseResult.last4,
            classification = raw,
            accounts = accounts,
        )

        val matchedRule = RecipientRuleMatcher.firstMatch(
            merchant = parseResult.merchant,
            rules = recipientRules,
        )

        val finalClassification = SelfTransferHeuristic.apply(raw, matchedRule)
        val budgetEffect = budgetEffectFor(finalClassification)
        val merchantKey = MerchantKeyNormalizer.normalize(parseResult.merchant)

        val category = Categorizer.categoryFor(
            classification = finalClassification,
            parserAssignedCategory = parseResult.category,
            merchantKey = merchantKey,
        )

        val classificationOriginal =
            if (finalClassification != raw) raw else null

        return ClassificationOutcome(
            finalClassification = finalClassification,
            budgetEffect = budgetEffect,
            amount = parseResult.amount,
            currency = parseResult.currency,
            merchant = parseResult.merchant,
            merchantKey = merchantKey,
            last4 = parseResult.last4,
            accountId = account?.id,
            category = category,
            classificationOriginal = classificationOriginal,
            matchedRuleId = matchedRule?.id,
        )
    }
}
