package com.yutori.classifier.internal

import com.yutori.parser.Category
import com.yutori.parser.Classification

/**
 * Resolves a [Category] for a transaction per business-logic-spec.md §3.
 *
 * Resolution order (§3.1):
 *   1. Respect parser-assigned category when present — the parser knows
 *      specific rules (Kotak UPI → UPI_TRANSFER; Blinkit refund).
 *   2. Only money-moving classifications get a category (§3.3). All
 *      DROP-effect classifications (CC_BILL_PAYMENT, OTP, CASHBACK,
 *      SELF_TRANSFER, BALANCE_ALERT, NON_FINANCIAL, UNMATCHED,
 *      INCOMING_CREDIT) return null here.
 *      INCOMING_CREDIT is INCOME per §2.4 — not a spend/refund, so no
 *      spend category applies.
 *   3. Keyword match on [merchantKey] against the §3.2 map. Cross-cutting
 *      platforms (Amazon, Flipkart, Blinkit, Zepto, Swiggy Instamart,
 *      BigBasket, DMart) are checked FIRST so "swiggy instamart" resolves
 *      to UNCATEGORIZED instead of matching the generic "swiggy"
 *      FOOD_DINING keyword.
 *   4. Fallback: OTHER for a non-null merchant that matched nothing, or
 *      for a null merchantKey on a money-moving event (we still have a
 *      classification, just no merchant to key on).
 *
 * Note: UPI_TRANSFER and CASH are never produced by keyword matching.
 * CASH is reachable via rule 1 (ATM_WITHDRAWAL-assigned); UPI_TRANSFER
 * via parser-assigned category on Kotak UPI.
 */
internal object Categorizer {

    // Classifications that receive a spend category. Everything else →
    // null (mirrors the set of non-DROP + non-INCOME effects from §2.4
    // per §3.3: "Category only applies to money-moving events").
    private val CATEGORIZABLE_CLASSIFICATIONS = setOf(
        Classification.CC_TRANSACTION,
        Classification.UPI_PAYMENT,
        Classification.DEBIT_CARD,
        Classification.ATM_WITHDRAWAL,
        Classification.REFUND,
    )

    // Cross-cutting platforms — must be checked before FOOD_DINING so
    // "swiggy instamart" does not get swallowed by "swiggy".
    private val UNCATEGORIZED_KEYWORDS = listOf(
        "swiggy instamart", // before "swiggy"
        "amazon",
        "flipkart",
        "blinkit",
        "zepto",
        "bigbasket",
        "dmart",
    )

    private val FOOD_DINING_KEYWORDS = listOf(
        "zomato",
        "dominos",
        "mcdonalds",
        "starbucks",
        "subway",
        "kfc",
        "pizza hut",
        "faasos",
        "kawi",
        "café",
        "cafe",
        "restaurant",
        "bakery",
        "eatclub",
        "swiggy", // generic swiggy → food (instamart already caught above)
    )

    private val GROCERIES_KEYWORDS = listOf(
        "reliance fresh",
        "big bazaar",
        "spencers",
    )

    private val TRAVEL_TRANSPORT_KEYWORDS = listOf(
        "uber",
        "ola",
        "rapido",
        "irctc",
        "redbus",
        "bharat petr",
        "hp petrol",
        "indian oil",
        "metro",
        "autorickshaw",
    )

    private val SHOPPING_KEYWORDS = listOf(
        "myntra",
        "ajio",
        "nykaa",
        "vijay sales",
        "croma",
        "swarovski",
        "max fashion",
        "reliance digital",
        "tanishq",
    )

    private val BILLS_UTILITIES_KEYWORDS = listOf(
        "airtel",
        "jio",
        "vi",
        "bsnl",
        "mseb",
        "electricity",
        "water",
        "gas bill",
        "broadband",
        "tata power",
        "adani",
        "mahadiscom",
    )

    private val HEALTH_KEYWORDS = listOf(
        "apollo",
        "netmeds",
        "1mg",
        "pharmeasy",
        "practo",
        "medplus",
    )

    private val ENTERTAINMENT_KEYWORDS = listOf(
        "netflix",
        "hotstar",
        "prime video",
        "spotify",
        "pvr",
        "inox",
        "bookmyshow",
    )

    private val SUBSCRIPTIONS_KEYWORDS = listOf(
        "claude",
        "anthropic",
        "openai",
        "chatgpt",
        "github",
        "cursor",
        "vercel",
        "cloudflare",
        "notion",
        "figma",
        "linear",
        "1password",
        "dropbox",
        "apple.com/bill",
        "google one",
        "google *one",
        "microsoft 365",
        "office 365",
        "medium",
        "substack",
        "real-debrid",
        "f1tv",
        "f1 tv",
    )

    // Order matters: UNCATEGORIZED first (cross-cutting wins over generic
    // "swiggy"), then spec order §3.2.
    private val KEYWORD_TABLE: List<Pair<Category, List<String>>> = listOf(
        Category.UNCATEGORIZED to UNCATEGORIZED_KEYWORDS,
        Category.FOOD_DINING to FOOD_DINING_KEYWORDS,
        Category.GROCERIES to GROCERIES_KEYWORDS,
        Category.TRAVEL_TRANSPORT to TRAVEL_TRANSPORT_KEYWORDS,
        Category.SHOPPING to SHOPPING_KEYWORDS,
        Category.BILLS_UTILITIES to BILLS_UTILITIES_KEYWORDS,
        Category.HEALTH to HEALTH_KEYWORDS,
        Category.SUBSCRIPTIONS to SUBSCRIPTIONS_KEYWORDS,
        Category.ENTERTAINMENT to ENTERTAINMENT_KEYWORDS,
    )

    fun categoryFor(
        classification: Classification,
        parserAssignedCategory: Category?,
        merchantKey: String?,
    ): Category? {
        // (1) Parser-assigned wins — but only on money-moving events. A
        // parser-assigned category on, say, an OTP would be nonsensical;
        // §3.3 says no category for DROP/INCOME effects regardless.
        if (classification !in CATEGORIZABLE_CLASSIFICATIONS) {
            return null
        }
        if (parserAssignedCategory != null) {
            return parserAssignedCategory
        }

        // (3) Keyword match using word-boundary regex. Prevents short
        // keywords like "vi" from falsely matching "VINOD" or "jio" from
        // matching "ajio" (which belongs to SHOPPING). Word boundary is
        // enforced on both sides.
        if (merchantKey != null) {
            val key = merchantKey.lowercase()
            for ((category, keywords) in KEYWORD_TABLE) {
                for (keyword in keywords) {
                    if (matchesWholeToken(key, keyword)) {
                        return category
                    }
                }
            }
        }

        // (4) Fallback. Per task-spec: OTHER for unknown / null merchant
        // on money-moving events. UNCATEGORIZED is reserved for the
        // cross-cutting platforms above, not the null-merchant case.
        return Category.OTHER
    }

    /**
     * Matches [keyword] against [text] as a whole token, not a substring.
     * "vi" matches "vi prepaid" but not "vinod". Multi-word keywords
     * ("swiggy instamart") match when the phrase appears with word
     * boundaries on both sides.
     *
     * Word boundary = start-of-string, end-of-string, or any non-alnum
     * character (so "@" and "." count as boundaries, matching how VPA-
     * shaped merchants get tokenized).
     */
    private fun matchesWholeToken(text: String, keyword: String): Boolean {
        val pattern = Regex("(^|[^a-z0-9])" + Regex.escape(keyword) + "($|[^a-z0-9])")
        return pattern.containsMatchIn(text)
    }
}
