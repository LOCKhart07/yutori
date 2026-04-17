package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

private val regex = Regex(
    """^Spent (?<ccy>INR|USD|EUR|GBP|AED) (?<amt>[\d.]+)\s*
Axis Bank Card no\. XX(?<last4>\w+)\s*
\S+ \S+ IST\s*
(?<merchant>.+?)\s*
Avl Limit""",
)

/**
 * parser-spec.md §5.3 — Axis credit card spend.
 *
 * Multi-line template. Currency whitelist is deliberate: unknown
 * currencies fall through to UNMATCHED so gaps surface as alerts.
 */
fun axisCcSpend(input: SmsInput): ParseResult? {
    if (!input.sender.contains("AXISBK")) return null
    val match = regex.find(input.body) ?: return null
    return ParseResult(
        classification = Classification.CC_TRANSACTION,
        amount = match.groups["amt"]!!.value.replace(",", "").toDouble(),
        currency = match.groups["ccy"]!!.value,
        merchant = match.groups["merchant"]!!.value.trim(),
        last4 = match.groups["last4"]!!.value,
        pattern = "axis_cc_spend",
    )
}
