package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

private val regex = Regex(
    """^INR (?<amt>[\d.]+) spent on Kotak Credit Card x(?<last4>\w+) on .+? at (?<merchant>.+?)\. Avl limit""",
)

/**
 * parser-spec.md §5.2 — Kotak credit card spend.
 *
 * `category = null`; merchant-based categorization runs downstream.
 */
fun kotakCcSpend(input: SmsInput): ParseResult? {
    if (!input.sender.contains("KOTAKB")) return null
    val match = regex.find(input.body) ?: return null
    return ParseResult(
        classification = Classification.CC_TRANSACTION,
        amount = match.groups["amt"]!!.value.replace(",", "").toDouble(),
        merchant = match.groups["merchant"]!!.value,
        last4 = match.groups["last4"]!!.value,
        pattern = "kotak_cc_spend",
    )
}
