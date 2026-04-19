package com.yutori.classifier

import com.yutori.parser.Category
import com.yutori.parser.Classification

/**
 * Classifier output. See business-logic-spec.md §2.1.
 *
 * Wraps the raw parser verdict with downstream-resolved fields:
 * - [finalClassification] may differ from [classificationOriginal] if
 *   a recipient rule or self-transfer heuristic reclassified.
 * - [budgetEffect] is derived from [finalClassification] per §2.4.
 * - [accountId] is resolved from the SMS's `last4` against the user's
 *   registered accounts; null when unresolved.
 * - [merchantKey] is the normalized form used for category keyword
 *   matching and for §12.3 dedup.
 */
data class ClassificationOutcome(
    val finalClassification: Classification,
    val budgetEffect: BudgetEffect,
    val amount: Double?,
    val currency: String,
    val merchant: String?,
    val merchantKey: String?,
    val last4: String?,
    val accountId: Long?,
    val category: Category?,
    val classificationOriginal: Classification?,
    val matchedRuleId: Long? = null,
    /**
     * Snapshot of [finalClassification] / [category] at the moment of
     * classification. Mirrored to `transactions.classification_inferred`
     * / `category_inferred` so the per-tx "Use automatic" path can
     * restore without re-running the parser. See business-logic-spec
     * §3.4. Today these are equal to [finalClassification] / [category];
     * they diverge only after a reparse run vs a per-tx override.
     */
    val classificationInferred: Classification = finalClassification,
    val categoryInferred: Category? = category,
)
