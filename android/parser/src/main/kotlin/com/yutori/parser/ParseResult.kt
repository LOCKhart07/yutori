package com.yutori.parser

/**
 * Output of [Parser.parse]. See parser-spec.md §3.
 *
 * `pattern` names the rule that fired, or "UNMATCHED" if none did.
 * `classification` is the raw parser verdict — the downstream
 * classifier may later override this (see parser-spec.md §9).
 */
data class ParseResult(
    val classification: Classification,
    val amount: Double? = null,
    val currency: String = "INR",
    val merchant: String? = null,
    val last4: String? = null,
    val category: Category? = null,
    val pattern: String,
)
