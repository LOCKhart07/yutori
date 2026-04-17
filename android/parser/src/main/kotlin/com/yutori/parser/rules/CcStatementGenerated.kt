package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

/**
 * parser-spec.md §5.16 — credit card statement generated notice.
 *
 * Any sender. Case-insensitive search against three body patterns.
 * No amount/merchant/last4 extracted.
 */
private val patterns = listOf(
    Regex("""statement for .*?Credit Card.*? is generated""", RegexOption.IGNORE_CASE),
    Regex("""statement for Credit Card X\w+ Total Due""", RegexOption.IGNORE_CASE),
    Regex("""Your credit card bill (?:for .+?)?has been generated""", RegexOption.IGNORE_CASE),
)

fun ccStatementGenerated(input: SmsInput): ParseResult? {
    if (patterns.none { it.containsMatchIn(input.body) }) return null
    return ParseResult(
        classification = Classification.BALANCE_ALERT,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "cc_statement_generated",
    )
}
