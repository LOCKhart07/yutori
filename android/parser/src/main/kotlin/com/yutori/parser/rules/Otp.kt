package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput

/**
 * parser-spec.md §5.18 — OTP / verification-code messages.
 *
 * Any sender. Body must contain "OTP", "verification code", or
 * "one-time password" (case-insensitive). Safety-tip messages
 * ("do not share", "never share", "beware") are rejected even if
 * they mention OTP.
 */
private val otpRegex = Regex(
    """\b(?:OTP|verification code|one[- ]time\s+password)\b""",
    RegexOption.IGNORE_CASE,
)

private val otpGuardRegex = Regex(
    """(?:do not share|never share|beware)""",
    RegexOption.IGNORE_CASE,
)

fun otp(input: SmsInput): ParseResult? {
    if (otpGuardRegex.containsMatchIn(input.body)) return null
    if (!otpRegex.containsMatchIn(input.body)) return null
    return ParseResult(
        classification = Classification.OTP,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "otp",
    )
}
