package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

private val regex = Regex(
    """^Rs\.(?<amt>[\d.]+) is Credited By Trf in A/c (?<last4>\d+) on .+? INT:""",
)

/**
 * parser-spec.md §5.11 — Vasai Janata Bank quarterly savings interest
 * (INCOMING_CREDIT). Merchant is parser-assigned.
 */
fun vjsbnkInterest(input: SmsInput): ParseResult? {
    if (!input.sender.contains("VJSBNK")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = match.groups["last4"]!!.value
    return ParseResult(
        classification = Classification.INCOMING_CREDIT,
        amount = amt,
        merchant = "Savings interest (VJSBNK)",
        last4 = last4,
        pattern = "vjsbnk_interest",
    )
}
