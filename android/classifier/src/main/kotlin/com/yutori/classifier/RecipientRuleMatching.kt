package com.yutori.classifier

import com.yutori.classifier.internal.RecipientRuleMatcher

/**
 * Public facade around the internal matcher so external callers
 * (e.g. the suggestion miner in `:transactions`) can reuse the exact
 * same match semantics the classifier uses — without leaking the
 * internal object.
 */
object RecipientRuleMatching {

    /** True if any enabled rule in [rules] matches [merchant]. */
    fun isCovered(merchant: String?, rules: List<RecipientRule>): Boolean =
        RecipientRuleMatcher.firstMatch(merchant, rules) != null
}
