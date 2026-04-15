package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

/**
 * parser-spec.md §5.5 — Kotak CC bill payment received.
 *
 * Sender substring: "KOTAKB".
 * Matches: `Payment of INR <amt> is credited to your Kotak Bank Credit Card x<last4>`
 */
private val regex = Regex(
    """^Payment of INR (?<amt>[\d.]+) is credited to your Kotak Bank """ +
        """Credit Card x(?<last4>\w+)""",
)

fun kotakCcBillReceived(input: SmsInput): ParseResult? {
    if ("KOTAKB" !in input.sender) return null
    val m = regex.find(input.body) ?: return null
    val amt = m.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = m.groups["last4"]!!.value
    return ParseResult(
        classification = Classification.CC_BILL_PAYMENT,
        amount = amt,
        currency = "INR",
        merchant = null,
        last4 = last4,
        category = null,
        pattern = "kotak_cc_bill_received",
    )
}
