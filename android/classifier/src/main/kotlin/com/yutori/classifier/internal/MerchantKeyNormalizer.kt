package com.yutori.classifier.internal

/**
 * Normalizes merchant strings into a stable key used for:
 *   (a) category keyword matching in Categorizer, and
 *   (b) the §12.3 dedup `merchantKey` field.
 *
 * See business-logic-spec.md §3 (Category assignment), in particular §3.2
 * which covers the Kotak CC 5-char tag expansion (e.g. `UPI-<ref>-ZOMAT`
 * → `zomato`).
 *
 * Pure: null-safe, no I/O, no locale dependency (plain `.lowercase()`).
 */
internal object MerchantKeyNormalizer {

    // Known Kotak CC 5-char tag expansions. Keys are uppercase-source but
    // the matching is done on lowercased text, so store lowercase.
    private val KOTAK_TAG_EXPANSIONS: Map<String, String> = mapOf(
        "zomat" to "zomato",
        "domin" to "dominos",
        "kawi" to "kawi",
    )

    // After lowercasing + punctuation-stripping, a Kotak CC merchant looks
    // like: `upi <digits> <tag>` (hyphens have already been turned into
    // spaces and collapsed). We match that shape and pull out the tag.
    private val KOTAK_UPI_REGEX = Regex("""^upi \d+ ([a-z]+)$""")

    // Characters allowed verbatim in the output: a-z, 0-9, '@', '.', space.
    // Anything else is replaced with a space, and runs of whitespace are
    // then collapsed to a single space.
    private val DISALLOWED_CHAR_REGEX = Regex("""[^a-z0-9@. ]""")
    private val WHITESPACE_RUN_REGEX = Regex("""\s+""")

    fun normalize(merchant: String?): String? {
        if (merchant == null) return null
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) return null

        val lowered = trimmed.lowercase()
        val punctStripped = DISALLOWED_CHAR_REGEX.replace(lowered, " ")
        val collapsed = WHITESPACE_RUN_REGEX.replace(punctStripped, " ").trim()
        if (collapsed.isEmpty()) return null

        val kotakMatch = KOTAK_UPI_REGEX.matchEntire(collapsed)
        if (kotakMatch != null) {
            val tag = kotakMatch.groupValues[1]
            return KOTAK_TAG_EXPANSIONS[tag] ?: tag
        }

        return collapsed
    }
}
