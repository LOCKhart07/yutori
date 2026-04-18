package com.yutori.classifier

import com.yutori.classifier.internal.RecipientRuleMatcher
import java.util.regex.PatternSyntaxException

/**
 * Public facade around the internal matcher so external callers
 * (e.g. the suggestion miner in `:transactions`, the rule add/edit
 * form in `:app`) can reuse the exact same match semantics the
 * classifier uses — without leaking the internal object.
 */
object RecipientRuleMatching {

    /** True if any enabled rule in [rules] matches [merchant]. */
    fun isCovered(merchant: String?, rules: List<RecipientRule>): Boolean =
        RecipientRuleMatcher.firstMatch(merchant, rules) != null

    /**
     * Evaluate a draft pattern against a set of candidate merchant strings.
     * Used by the Test preview panel on the add/edit rule screen.
     */
    fun evalDraft(
        pattern: String,
        kind: PatternKind,
        merchants: List<String>,
    ): DraftEval {
        if (pattern.isEmpty()) return DraftEval.Valid(emptyList())
        return when (kind) {
            PatternKind.LITERAL ->
                DraftEval.Valid(merchants.filter { it == pattern })
            PatternKind.PREFIX ->
                DraftEval.Valid(merchants.filter { it.startsWith(pattern) })
            PatternKind.REGEX -> try {
                val regex = Regex(pattern)
                DraftEval.Valid(merchants.filter { regex.containsMatchIn(it) })
            } catch (e: PatternSyntaxException) {
                DraftEval.Invalid(e.description ?: e.message ?: "Invalid regex")
            }
        }
    }

    sealed class DraftEval {
        data class Valid(val matches: List<String>) : DraftEval()
        data class Invalid(val error: String) : DraftEval()
    }
}
