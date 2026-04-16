package com.spendwise.classifier.internal

import com.spendwise.classifier.Account
import com.spendwise.classifier.AccountKind
import com.spendwise.classifier.AccountStatus
import com.spendwise.parser.Classification

/**
 * Resolves a transaction's [Account] by matching the parsed `last4`
 * (and [Classification], for kind-based disambiguation) against the
 * user's registered accounts.
 *
 * See business-logic-spec.md §2.2 (pipeline step 3) and
 * error-states-spec.md §4.2 (ambiguous last4 handling).
 */
internal object AccountResolver {

    /**
     * Returns the single best-matching [Account] for [last4], or null
     * if no match can be determined. Ambiguity that cannot be broken
     * by kind preference or `isDefaultSpend` is resolved as null —
     * we never pick arbitrarily.
     */
    fun resolve(
        last4: String?,
        classification: Classification,
        accounts: List<Account>,
    ): Account? {
        if (last4 == null) return null

        // SUGGESTED and DISMISSED rows are machine-generated proposals —
        // they exist in the DB but must not steer transaction routing.
        val confirmed = accounts.filter { it.status == AccountStatus.CONFIRMED }

        // Normalize both sides: strip non-digits. SMS bodies use varied
        // prefixes ("X0000", "XX1111", "x3333") and the parser may capture
        // with or without them depending on the rule's regex. Accounts
        // stored in settings may use any of those forms too. Matching on
        // digits-only makes the comparison stable across surface forms.
        val inputDigits = last4.filter(Char::isDigit)
        if (inputDigits.isEmpty()) return null
        // Accounts with null last4 (UPI-only) never match a parsed
        // last-4 — identity for those flows through recipient_rules.
        val matches = confirmed.filter {
            it.last4?.filter(Char::isDigit) == inputDigits
        }
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.single()

        // Step 5a: kind preference based on classification.
        val kindNarrowed = narrowByKind(matches, classification)
        if (kindNarrowed.size == 1) return kindNarrowed.single()

        // Step 5b: isDefaultSpend tie-breaker.
        val defaults = kindNarrowed.filter { it.isDefaultSpend }
        if (defaults.size == 1) return defaults.single()

        // Step 5c: still ambiguous — refuse to guess.
        return null
    }

    /**
     * Applies the classification → preferred-kind rules from
     * error-states-spec §4.2. When no candidate matches the preferred
     * kind, falls back to the fallback kind if specified. If neither
     * yields matches (or the classification has no preference) the
     * input list is returned unchanged.
     */
    private fun narrowByKind(
        matches: List<Account>,
        classification: Classification,
    ): List<Account> {
        val (preferred, fallback) = when (classification) {
            Classification.CC_TRANSACTION,
            Classification.CC_BILL_PAYMENT -> AccountKind.CREDIT_CARD to null

            Classification.UPI_PAYMENT,
            Classification.DEBIT_CARD,
            Classification.ATM_WITHDRAWAL,
            Classification.INCOMING_CREDIT -> AccountKind.SAVINGS to AccountKind.INVESTMENT

            else -> return matches
        }

        val preferredMatches = matches.filter { it.kind == preferred }
        if (preferredMatches.isNotEmpty()) return preferredMatches

        if (fallback != null) {
            val fallbackMatches = matches.filter { it.kind == fallback }
            if (fallbackMatches.isNotEmpty()) return fallbackMatches
        }

        return matches
    }
}
