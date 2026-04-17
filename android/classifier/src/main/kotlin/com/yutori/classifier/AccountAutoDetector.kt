package com.yutori.classifier

import com.yutori.parser.Classification

/**
 * Suggests new accounts to the user by mining parsed SMSes for
 * `(issuer, last4)` pairs that don't match any existing account.
 *
 * Pure — no DB access. The ingestion layer takes the result and
 * upserts into the accounts table.
 *
 * Returns null when:
 *   - last4 wasn't extracted (parser didn't find one),
 *   - issuer couldn't be derived from the sender,
 *   - the classification tells us nothing about the account kind
 *     (OTPs, balance alerts, unmatched SMSes),
 *   - a CONFIRMED, SUGGESTED or DISMISSED row already exists for the
 *     same `(issuer, last4)` — dedup + sticky-dismiss in one check.
 *
 * Callers distinguish "bump seen count" vs. "insert new" via
 * [Action].
 */
object AccountAutoDetector {

    sealed interface Action {
        /** Create a new account row with status = SUGGESTED. */
        data class Create(
            val issuer: String,
            val last4: String,
            val kind: AccountKind,
        ) : Action

        /** Existing SUGGESTED row matched — just bump its seenCount. */
        data class BumpSeen(val accountId: Long) : Action
    }

    fun detect(
        sender: String,
        parserLast4: String?,
        classification: Classification,
        existingAccounts: List<Account>,
    ): Action? {
        val last4 = parserLast4?.takeIf { it.isNotBlank() } ?: return null
        val issuer = deriveIssuer(sender) ?: return null
        val inferredKind = inferKind(classification) ?: return null

        val match = existingAccounts.firstOrNull {
            it.issuer.equals(issuer, ignoreCase = true) &&
                it.last4?.equals(last4, ignoreCase = true) == true
        }
        return when {
            // CONFIRMED or DISMISSED → do nothing.
            match?.status == AccountStatus.CONFIRMED -> null
            match?.status == AccountStatus.DISMISSED -> null
            // SUGGESTED → bump.
            match?.status == AccountStatus.SUGGESTED -> Action.BumpSeen(match.id)
            // No match → propose.
            else -> Action.Create(issuer = issuer, last4 = last4, kind = inferredKind)
        }
    }

    private fun inferKind(c: Classification): AccountKind? = when (c) {
        Classification.CC_TRANSACTION,
        Classification.CC_BILL_PAYMENT,
        -> AccountKind.CREDIT_CARD

        Classification.UPI_PAYMENT,
        Classification.INCOMING_CREDIT,
        Classification.ATM_WITHDRAWAL,
        Classification.DEBIT_CARD,
        Classification.REFUND,
        Classification.CASHBACK,
        -> AccountKind.SAVINGS

        else -> null
    }

    // Keep this in sync with transactions/IssuerDeriver. Duplicated
    // rather than cross-depended because classifier can't see
    // :transactions (it's the other way around).
    private fun deriveIssuer(sender: String): String? {
        val mapping = listOf(
            "KOTAKB" to "Kotak",
            "KOTAKD" to "Kotak",
            "AXISBK" to "Axis",
            "ICICI" to "ICICI",
            "HDFC" to "HDFC",
            "SBIUPI" to "SBI",
            "SBIINB" to "SBI",
            "SBIBNK" to "SBI",
            "SBI" to "SBI",
            "PAYTMM" to "Paytm",
            "VJSBNK" to "Vasai Janata Bank",
        )
        return mapping.firstOrNull { it.first in sender }?.second
    }
}
