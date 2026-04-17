package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

private val regex = Regex(
    """^Congratulations! Cashback of INR (?<amt>[\d.]+) has been credited to your Axis Bank .*? Credit Card XX(?<last4>\w+)""",
)

/**
 * parser-spec.md §5.12 — Axis credit-card cashback credit.
 */
fun axisCashback(input: SmsInput): ParseResult? {
    if (!input.sender.contains("AXISBK")) return null
    val match = regex.find(input.body) ?: return null
    return ParseResult(
        classification = Classification.CASHBACK,
        amount = match.groups["amt"]!!.value.replace(",", "").toDouble(),
        currency = "INR",
        last4 = match.groups["last4"]!!.value,
        pattern = "axis_cashback",
    )
}
