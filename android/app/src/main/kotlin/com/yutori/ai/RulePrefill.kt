package com.yutori.ai

/**
 * Result of a successful LLM extraction, handed from
 * `DescribeRuleViewModel` to the navigation layer and on to
 * `AddEditRecipientRule` as its third pre-fill variant (beyond
 * `ruleId` and `prefillSuggestionId`).
 *
 * See `plans/ai-rules-spec.md` §6.4 — pattern + category are
 * pre-populated; `patternKind` defaults to `LITERAL` and
 * `reclassifyAs` is left blank for the user to pick.
 */
data class RulePrefill(
    val pattern: String,
    val category: String?,
)
