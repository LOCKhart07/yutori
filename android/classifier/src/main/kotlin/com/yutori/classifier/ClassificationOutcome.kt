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
)
