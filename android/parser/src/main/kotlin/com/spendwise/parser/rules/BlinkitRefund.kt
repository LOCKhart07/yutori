package com.spendwise.parser.rules

import com.spendwise.parser.Category
import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

private val regex = Regex(
    """^We have initiated a refund of Rs\.(?<amt>[\d.]+) for the cancelled order (?<order>\S+)""",
)

/**
 * parser-spec.md §5.13 — Blinkit order-cancellation refund.
 *
 * Blinkit is a cross-cutting platform (plan §5.1), so category is
 * UNCATEGORIZED — the real category belongs to the original order's
 * debit, not this refund.
 */
fun blinkitRefund(input: SmsInput): ParseResult? {
    if (!input.sender.lowercase().contains("blnkit")) return null
    val match = regex.find(input.body) ?: return null
    return ParseResult(
        classification = Classification.REFUND,
        amount = match.groups["amt"]!!.value.replace(",", "").toDouble(),
        currency = "INR",
        merchant = "Blinkit",
        category = Category.UNCATEGORIZED,
        pattern = "blinkit_refund",
    )
}
