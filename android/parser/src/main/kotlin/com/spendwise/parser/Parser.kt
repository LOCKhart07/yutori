package com.spendwise.parser

import com.spendwise.parser.rules.axisCashback
import com.spendwise.parser.rules.axisCcAdmin
import com.spendwise.parser.rules.axisCcBillReceived
import com.spendwise.parser.rules.axisCcSpend
import com.spendwise.parser.rules.axisSavingsCcBillDebit
import com.spendwise.parser.rules.axisSavingsUpiCredit
import com.spendwise.parser.rules.blinkitRefund
import com.spendwise.parser.rules.ccStatementGenerated
import com.spendwise.parser.rules.declined
import com.spendwise.parser.rules.iciciEazypay
import com.spendwise.parser.rules.knownNonFinSender
import com.spendwise.parser.rules.kotakCcBillReceived
import com.spendwise.parser.rules.kotakCcSpend
import com.spendwise.parser.rules.kotakNeftCredit
import com.spendwise.parser.rules.kotakUpiCredit
import com.spendwise.parser.rules.kotakUpiDebit
import com.spendwise.parser.rules.missedCalls
import com.spendwise.parser.rules.otp
import com.spendwise.parser.rules.paytmSebiCredit
import com.spendwise.parser.rules.vjsbnkInterest

/**
 * SpendWise SMS parser. Pure function, no Android dependencies.
 *
 * Rule order is load-bearing — see parser-spec.md §4. Money-movement
 * rules run first so real spends are never shadowed by an admin/promo
 * pattern. Catch-alls run last.
 */
object Parser {

    private val RULES: List<(SmsInput) -> ParseResult?> = listOf(
        // Tier 1 — money-movement events
        ::kotakUpiDebit,
        ::kotakCcSpend,
        ::axisCcSpend,

        // Tier 2 — bill payments
        ::axisCcBillReceived,
        ::kotakCcBillReceived,
        ::axisSavingsCcBillDebit,

        // Tier 3 — incoming money
        ::kotakUpiCredit,
        ::kotakNeftCredit,
        ::axisSavingsUpiCredit,
        ::paytmSebiCredit,
        ::vjsbnkInterest,

        // Tier 4 — bank adjustments
        ::axisCashback,
        ::blinkitRefund,
        ::iciciEazypay,

        // Tier 5 — notices
        ::declined,
        ::ccStatementGenerated,
        ::axisCcAdmin,

        // Tier 6 — catch-alls
        ::otp,
        ::missedCalls,
        ::knownNonFinSender,
    )

    fun parse(input: SmsInput): ParseResult {
        for (rule in RULES) {
            val result = rule(input)
            if (result != null) return result
        }
        return ParseResult(
            classification = Classification.UNMATCHED,
            pattern = "UNMATCHED",
        )
    }
}
