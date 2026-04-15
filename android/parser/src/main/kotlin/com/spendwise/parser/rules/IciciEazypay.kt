package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

private val regex = Regex(
    """^Dear Sir/Madam, you have made a payment of Rs\. (?<amt>[\d.]+) to (?<merchant>.+?) vide ICICI Bank eazypay""",
)

/**
 * parser-spec.md §5.14 — ICICI eazypay guest-checkout payment.
 *
 * Gateway flow, not a bank-side debit. A duplicate bank debit SMS may
 * follow; dedup is §12.3's problem — the parser keeps both distinct.
 */
fun iciciEazypay(input: SmsInput): ParseResult? {
    if (!input.sender.contains("ICICIT")) return null
    val match = regex.find(input.body) ?: return null
    return ParseResult(
        classification = Classification.UPI_PAYMENT,
        amount = match.groups["amt"]!!.value.replace(",", "").toDouble(),
        currency = "INR",
        merchant = match.groups["merchant"]!!.value.trim(),
        pattern = "icici_eazypay",
    )
}
