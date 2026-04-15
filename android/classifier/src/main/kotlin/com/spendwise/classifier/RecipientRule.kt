package com.spendwise.classifier

import com.spendwise.parser.Classification

/**
 * A recipient-matching rule per §12.2 + §12.4. When a classifier input's
 * merchant/VPA matches the [pattern], [reclassifyAs] overrides the raw
 * parser classification. Accounts linked via [accountId] carry the
 * resolved account through.
 *
 * Storage lives in the future Room `recipient_rules` table; this is the
 * in-memory domain representation.
 */
data class RecipientRule(
    val id: Long,
    val pattern: String,
    val patternKind: PatternKind,
    val reclassifyAs: Classification,
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

    /** Inferred or suggested automatically (reserved; not used in v1). */
    LEARNED,
}
