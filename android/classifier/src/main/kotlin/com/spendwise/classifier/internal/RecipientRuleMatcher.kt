package com.spendwise.classifier.internal

import com.spendwise.classifier.PatternKind
import com.spendwise.classifier.RecipientRule
import java.util.regex.PatternSyntaxException

/**
 * Finds the first [RecipientRule] whose pattern matches a merchant string,
 * per the pattern-kind semantics defined in `settings-spec.md` §3.5.
 *
 * Iteration order is the caller-supplied list order: callers are responsible
 * for ordering rules (e.g. most-specific first). This matcher does not sort.
 */
internal object RecipientRuleMatcher {

    /**
     * Returns the first enabled rule in [rules] whose [pattern] matches
     * [merchant], or `null` if [merchant] is null, if [rules] is empty,
     * or if no rule matches.
     *
     * Match semantics:
     * - [PatternKind.LITERAL]: case-sensitive exact equality.
     * - [PatternKind.PREFIX]: case-sensitive `startsWith`.
     * - [PatternKind.REGEX]: Java-regex `containsMatchIn`. Rule authors
     *   anchor with `^`/`$` themselves.
     *
     * Disabled rules (`isEnabled == false`) are skipped. A malformed regex
     * (throws [PatternSyntaxException]) is swallowed and skipped so one bad
     * rule does not crash the classifier; evaluation continues with the
     * next rule.
     */
    fun firstMatch(merchant: String?, rules: List<RecipientRule>): RecipientRule? {
        if (merchant == null) return null
        for (rule in rules) {
            if (!rule.isEnabled) continue
            if (matches(rule, merchant)) return rule
        }
        return null
    }

    private fun matches(rule: RecipientRule, merchant: String): Boolean =
        when (rule.patternKind) {
            PatternKind.LITERAL -> rule.pattern == merchant
            PatternKind.PREFIX -> merchant.startsWith(rule.pattern)
            PatternKind.REGEX -> try {
                Regex(rule.pattern).containsMatchIn(merchant)
            } catch (_: PatternSyntaxException) {
                false
            }
        }
}
