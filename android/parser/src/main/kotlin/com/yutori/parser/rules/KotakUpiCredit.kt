package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

private val regex = Regex(
    """^Received Rs\.(?<amt>[\d.]+) in your Kotak Bank AC X(?<last4>\w+) from (?<src>\S+) on""",
)

/**
 * parser-spec.md §5.7 — Kotak UPI credit (INCOMING_CREDIT).
 *
 * UPI credits into Kotak savings. `src` is a UPI VPA — stored in the
 * `merchant` field per the spec's "counterparty" convention.
 */
fun kotakUpiCredit(input: SmsInput): ParseResult? {
    if (!input.sender.contains("KOTAKB")) return null
    if (!input.body.startsWith("Received Rs.")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = match.groups["last4"]!!.value
    val src = match.groups["src"]!!.value
    return ParseResult(
        classification = Classification.INCOMING_CREDIT,
        amount = amt,
        merchant = src,
        last4 = last4,
        pattern = "kotak_upi_credit",
    )
}
