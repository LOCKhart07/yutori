package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

/**
 * parser-spec.md §5.4 — Axis CC bill payment received.
 *
 * Sender substring: "AXISBK".
 * Matches: `Payment of INR <amt> has been received towards your Axis Bank Credit Card XX<last4>`
 */
private val regex = Regex(
    """^Payment of INR (?<amt>[\d.]+) has been received towards your """ +
        """Axis Bank Credit Card XX(?<last4>\w+)""",
)

fun axisCcBillReceived(input: SmsInput): ParseResult? {
    if ("AXISBK" !in input.sender) return null
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
        pattern = "axis_cc_bill_received",
    )
}
