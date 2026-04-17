package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

/**
 * parser-spec.md §5.17 — Axis Bank Credit Card administrative / service
 * notices (new PIN, card dispatch, limit updates, SR status, maintenance,
 * contact acknowledgements). Classified NON_FINANCIAL.
 *
 * Sender substring: "AXISBK". Case-sensitive search (matches the Python
 * reference, which does not pass re.I for these patterns).
 */
private val patterns = listOf(
    Regex("""New PIN for Axis Bank Credit Card"""),
    Regex("""replace .* Credit Card no\.\s*XX\w+ has been dispatched"""),
    Regex("""usage & transaction limit options have been updated"""),
    Regex("""SR no\. \w+ (?:for|related to) .*(?:Axis Bank)"""),
    Regex("""txns\. with Axis Bank Credit Card .* will be paused"""),
    Regex("""has been resolved - Axis Bank"""),
    Regex("""Thank you for contacting Axis Bank"""),
)

fun axisCcAdmin(input: SmsInput): ParseResult? {
    if ("AXISBK" !in input.sender) return null
    if (patterns.none { it.containsMatchIn(input.body) }) return null
    return ParseResult(
        classification = Classification.NON_FINANCIAL,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "axis_cc_admin",
    )
}
