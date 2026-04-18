package com.yutori.classifier.suggestions

import com.yutori.classifier.Account
import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.parser.Classification

/**
 * Domain types for the heuristic rule-suggestion surface.
 * Spec: plans/suggestions-spec.md §3.4 (inference).
 */

/** Mined from `transactions` by [com.yutori.transactions.suggestions.SuggestionMiner]. */
data class SuggestionCandidate(
    val merchantKey: String,
    val matchCount: Int,
    val totalInr: Double,
)

/** Output of [SuggestionInference.infer] — ready to persist or display. */
data class InferredSuggestion(
    val pattern: String,
    val patternKind: PatternKind,
    val inferredClassification: Classification?,
    val inferredAccountId: Long?,
    val reasonCode: ReasonCode,
)

enum class ReasonCode {
    /** Candidate VPA's local-part matches a registered own-handle's local-part. */
    OWN_HANDLE_SHAPE,

    /** Candidate string contains a known CC-bill-middleman keyword. */
    KEYWORD_MIDDLEMAN,

    /** Repeat merchant with no heuristic match — user must pick a type. */
    REPEAT_NO_DEFAULT,
}

/**
 * Pure inference — no DB, no side effects. Order of checks is load-bearing:
 * own-handle (strongest signal) wins over middleman-keyword wins over fall-through.
 */
object SuggestionInference {

    /**
     * Keywords whose presence in [SuggestionCandidate.merchantKey] (case-
     * insensitive substring match) signals a CC-bill middleman. Derived from
     * the seed rules in settings-spec.md §3.3 — these are the middleman
     * families most Indian users touch.
     */
    val DEFAULT_MIDDLEMAN_KEYWORDS: List<String> = listOf(
        "cred",
        "cheq",
        "ccbill",
        "cc-bill",
        "creditcard",
        "credit-card",
        "postpaid",
        "bbps",
    )

    fun infer(
        candidate: SuggestionCandidate,
        accounts: List<Account>,
        recipientRules: List<RecipientRule>,
        middlemanKeywords: List<String> = DEFAULT_MIDDLEMAN_KEYWORDS,
    ): InferredSuggestion {
        val merchantKey = candidate.merchantKey

        ownHandleMatch(merchantKey, recipientRules, accounts)?.let { accountId ->
            return InferredSuggestion(
                pattern = merchantKey,
                patternKind = PatternKind.LITERAL,
                inferredClassification = Classification.SELF_TRANSFER,
                inferredAccountId = accountId,
                reasonCode = ReasonCode.OWN_HANDLE_SHAPE,
            )
        }

        if (middlemanKeywords.any { merchantKey.contains(it, ignoreCase = true) }) {
            return InferredSuggestion(
                pattern = merchantKey,
                patternKind = PatternKind.LITERAL,
                inferredClassification = Classification.CC_BILL_PAYMENT,
                inferredAccountId = null,
                reasonCode = ReasonCode.KEYWORD_MIDDLEMAN,
            )
        }

        return InferredSuggestion(
            pattern = merchantKey,
            patternKind = PatternKind.LITERAL,
            inferredClassification = null,
            inferredAccountId = null,
            reasonCode = ReasonCode.REPEAT_NO_DEFAULT,
        )
    }

    /**
     * Returns the account id for a candidate VPA whose local-part matches a
     * registered own-handle's local-part but whose domain differs — strong
     * signal that the user is transferring to themselves via a different
     * UPI provider. Requires the candidate to contain '@'.
     */
    private fun ownHandleMatch(
        merchantKey: String,
        rules: List<RecipientRule>,
        accounts: List<Account>,
    ): Long? {
        val candidateLocal = localPart(merchantKey) ?: return null
        val candidateDomain = domainPart(merchantKey)

        val accountIds = accounts.map { it.id }.toHashSet()

        return rules
            .asSequence()
            .filter { it.isEnabled }
            .filter { it.reclassifyAs == Classification.SELF_TRANSFER }
            .filter { it.patternKind == PatternKind.LITERAL }
            .filter { it.accountId != null && it.accountId in accountIds }
            .mapNotNull { rule ->
                val ruleLocal = localPart(rule.pattern) ?: return@mapNotNull null
                val ruleDomain = domainPart(rule.pattern)
                if (ruleLocal.equals(candidateLocal, ignoreCase = true) &&
                    !ruleDomain.equals(candidateDomain, ignoreCase = true)
                ) {
                    rule.accountId
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private fun localPart(vpa: String): String? {
        val at = vpa.indexOf('@')
        if (at <= 0) return null
        return vpa.substring(0, at)
    }

    private fun domainPart(vpa: String): String {
        val at = vpa.indexOf('@')
        if (at < 0 || at == vpa.length - 1) return ""
        return vpa.substring(at + 1)
    }
}
