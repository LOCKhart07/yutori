package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

private val regex = Regex(
    """^INR (?<amt>[\d.]+) credited\s*\nA/c no\. XX(?<last4>\w+)\s*\n""",
)

/**
 * parser-spec.md §5.9 — Axis savings UPI credit (INCOMING_CREDIT).
 *
 * Multi-line Axis savings incoming credit template. Merchant is not
 * extracted (the SMS doesn't name the payer cleanly).
 */
fun axisSavingsUpiCredit(input: SmsInput): ParseResult? {
    if (!input.sender.contains("AXISBK")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = match.groups["last4"]!!.value
    return ParseResult(
        classification = Classification.INCOMING_CREDIT,
        amount = amt,
        last4 = last4,
        pattern = "axis_savings_upi_credit",
    )
}
