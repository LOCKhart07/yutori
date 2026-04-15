package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

private val regex = Regex(
    """^Rs\.\s*(?<amt>[\d.]+) credited to your Kotak Bank a/c XX(?<last4>\w+) via NEFT from beneficiary (?<src>.+?)\. UTR Ref""",
)

/**
 * parser-spec.md §5.8 — Kotak NEFT credit (INCOMING_CREDIT).
 *
 * NEFT credits, typically salary (see spec §12.1). `src` is the beneficiary
 * name and is trimmed before being stored in `merchant`.
 */
fun kotakNeftCredit(input: SmsInput): ParseResult? {
    if (!input.sender.contains("KOTAKB")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = match.groups["last4"]!!.value
    val src = match.groups["src"]!!.value.trim()
    return ParseResult(
        classification = Classification.INCOMING_CREDIT,
        amount = amt,
        merchant = src,
        last4 = last4,
        pattern = "kotak_neft_credit",
    )
}
