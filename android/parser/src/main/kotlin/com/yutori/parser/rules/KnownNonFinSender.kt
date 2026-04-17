package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

/**
 * parser-spec.md §5.20 — catch-all for senders known to be
 * non-financial (telco, gov, retail marketing, etc.).
 *
 * Substring check is case-sensitive: DLT-header identifiers arrive
 * uppercase, and the few mixed-case entries ("JioPay", "BCCBnK")
 * are distinct strings.
 */
private val NON_FIN_SENDER_SUBSTR: List<String> = listOf(
    "ISATHI", "JIOINF", "JIOFBR", "JIOPAY", "JIONET", "-620016-", "-620040-",
    "JioPay", "BCCBnK", "VIJAYS", "MCLBLZ", "GSTIND", "VCPLNT", "POLBAZ", "TATALI",
    "TRAIND", "MHACIS", "MSEDCL", "ELSRUN", "TATAMO", "SBIINB", "BGBMST", "REGINF",
    "SFLTRC", "DOTMAH", "DOTMUM", "EKARTL", "JIOVOC",
)

fun knownNonFinSender(input: SmsInput): ParseResult? {
    if (NON_FIN_SENDER_SUBSTR.none { it in input.sender }) return null
    return ParseResult(
        classification = Classification.NON_FINANCIAL,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "known_non_fin_sender",
    )
}
