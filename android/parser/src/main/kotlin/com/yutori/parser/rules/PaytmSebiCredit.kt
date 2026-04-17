package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

private val regex = Regex(
    """^Rs\.\s*(?<amt>[\d.]+) successfully transferred .* SEBI-mandated \w+ settlement""",
)

/**
 * parser-spec.md §5.10 — Paytm SEBI settlement credit (INCOMING_CREDIT).
 *
 * Paytm Money's periodic return of unused funds (SEBI mandate). The SMS
 * doesn't name a source, so the merchant is parser-assigned.
 */
fun paytmSebiCredit(input: SmsInput): ParseResult? {
    if (!input.sender.contains("PAYTMM")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    return ParseResult(
        classification = Classification.INCOMING_CREDIT,
        amount = amt,
        merchant = "Paytm (SEBI settlement)",
        pattern = "paytm_sebi_credit",
    )
}
