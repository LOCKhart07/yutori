package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

/**
 * parser-spec.md §5.15 — declined transaction notice.
 *
 * Any sender. Matches the word "declined" (any case variant from the
 * alternation) anywhere in the body. No amount/merchant/last4 extracted.
 */
private val regex = Regex("""\b(declined|Declined|DECLINED)\b""")

fun declined(input: SmsInput): ParseResult? {
    if (!regex.containsMatchIn(input.body)) return null
    return ParseResult(
        classification = Classification.NON_FINANCIAL,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "declined",
    )
}
