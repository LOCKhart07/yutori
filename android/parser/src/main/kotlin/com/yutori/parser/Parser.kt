package com.yutori.parser

import com.yutori.parser.rules.axisCashback
import com.yutori.parser.rules.axisCcAdmin
import com.yutori.parser.rules.axisCcBillReceived
import com.yutori.parser.rules.axisCcSpend
import com.yutori.parser.rules.axisSavingsCcBillDebit
import com.yutori.parser.rules.axisSavingsUpiCredit
import com.yutori.parser.rules.blinkitRefund
import com.yutori.parser.rules.ccStatementGenerated
import com.yutori.parser.rules.declined
import com.yutori.parser.rules.iciciEazypay
import com.yutori.parser.rules.knownNonFinSender
import com.yutori.parser.rules.kotakCcBillReceived
import com.yutori.parser.rules.kotakCcSpend
import com.yutori.parser.rules.kotakNeftCredit
import com.yutori.parser.rules.kotakUpiCredit
import com.yutori.parser.rules.kotakUpiDebit
import com.yutori.parser.rules.missedCalls
import com.yutori.parser.rules.otp
import com.yutori.parser.rules.paytmSebiCredit
import com.yutori.parser.rules.vjsbnkInterest

/**
 * Yutori SMS parser. Pure function, no Android dependencies.
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
