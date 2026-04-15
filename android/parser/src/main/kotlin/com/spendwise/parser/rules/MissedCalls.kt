package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

/**
 * parser-spec.md §5.19 — Jio missed-call notices.
 *
 * Sender is a raw phone number (digits, optional leading '+'); body
 * mentions a missed call or caller availability.
 */
private val phoneSenderRegex = Regex("""^\+?\d+$""")

private val missedCallBodyRegex = Regex(
    """missed call|is now available to take calls""",
    RegexOption.IGNORE_CASE,
)

fun missedCalls(input: SmsInput): ParseResult? {
    if (!phoneSenderRegex.matches(input.sender)) return null
    if (!missedCallBodyRegex.containsMatchIn(input.body)) return null
    return ParseResult(
        classification = Classification.NON_FINANCIAL,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "missed_calls",
    )
}
