package com.yutori.classifier

import com.yutori.parser.Classification
import com.yutori.parser.Category

/**
 * A recipient-matching rule per §12.2 + §12.4. When a classifier
 * input's merchant/VPA matches the [pattern]:
 *  - [reclassifyAs], if non-null, overrides the raw parser
 *    classification.
 *  - [assignedCategory], if non-null and the final classification
 *    carries categories (SPEND/REFUND), overrides the categorizer.
 *
 * Either field can be null; both null is a no-op rule and the form
 * blocks Save (settings-spec §3.5).
 *
 * Storage lives in the Room `recipient_rules` table; this is the
 * in-memory domain representation.
 */
data class RecipientRule(
    val id: Long,
    val pattern: String,
    val patternKind: PatternKind,
    val reclassifyAs: Classification?,
    val assignedCategory: Category? = null,
    val accountId: Long? = null,
    val source: RuleSource = RuleSource.USER,
    val isEnabled: Boolean = true,
    val note: String? = null,
)

enum class PatternKind {
    /** Case-sensitive exact-string match. */
    LITERAL,

    /** `startsWith` match, case-sensitive. */
    PREFIX,

    /** Java Pattern-compatible regex. */
    REGEX,
}

enum class RuleSource {
    /** Shipped with the app. Disable-only, never deletable. */
    SEED,

    /** Added by the user in Settings. */
    USER,

    /**
     * Inferred by the heuristic miner and accepted by the user. Part 1
     * of issue #64; see `plans/suggestions-spec.md`.
     */
    LEARNED,

    /**
     * Extracted from a free-text user description by the on-device LLM
     * and accepted by the user via `AddEditRecipientRule`'s edit form.
     * Part 2 of issue #64; see `plans/ai-rules-spec.md` §3.1.
     */
    AI,
}
