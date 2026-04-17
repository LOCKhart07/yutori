package com.yutori.parser

/**
 * Input to the parser: a single SMS from the user's inbox.
 *
 * Matches parser-spec.md §3.
 */
data class SmsInput(
    val sender: String,
    val body: String,
)
