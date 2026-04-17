package com.yutori.classifier.internal

import com.yutori.parser.Category
import com.yutori.parser.Classification
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Exercises the §3.1–§3.3 resolution rules from business-logic-spec.md.
 *
 * Structure: one test per concrete rule (drop rules, parser override,
 * each keyword bucket, cross-cutting platforms, null merchant, and the
 * swiggy / swiggy-instamart disambiguation).
 */
class CategorizerTest {

    // ------------------------------------------------------------------
    // §3.3 — DROP-effect and INCOME classifications get no category.
    // ------------------------------------------------------------------

    @Test
    fun `CC_BILL_PAYMENT returns null even with merchant`() {
        Categorizer.categoryFor(
            classification = Classification.CC_BILL_PAYMENT,
            parserAssignedCategory = null,
            merchantKey = "zomato",
        ).shouldBeNull()
    }

    @Test
    fun `OTP returns null`() {
        Categorizer.categoryFor(
            classification = Classification.OTP,
            parserAssignedCategory = null,
            merchantKey = null,
        ).shouldBeNull()
    }

    @Test
    fun `CASHBACK returns null`() {
        Categorizer.categoryFor(
            classification = Classification.CASHBACK,
            parserAssignedCategory = null,
            merchantKey = "amazon",
        ).shouldBeNull()
    }

    @Test
    fun `SELF_TRANSFER returns null`() {
        Categorizer.categoryFor(
            classification = Classification.SELF_TRANSFER,
            parserAssignedCategory = null,
            merchantKey = "someVpa",
        ).shouldBeNull()
    }

    @Test
    fun `INCOMING_CREDIT returns null — income is not a spend category`() {
        Categorizer.categoryFor(
            classification = Classification.INCOMING_CREDIT,
            parserAssignedCategory = null,
            merchantKey = "acme corp",
        ).shouldBeNull()
    }

    @Test
    fun `UNMATCHED returns null`() {
        Categorizer.categoryFor(
            classification = Classification.UNMATCHED,
            parserAssignedCategory = null,
            merchantKey = null,
        ).shouldBeNull()
    }

    // ------------------------------------------------------------------
    // §3.1 rule 1 — parser-assigned category wins.
    // ------------------------------------------------------------------

    @Test
    fun `parser-assigned UPI_TRANSFER respected on UPI_PAYMENT`() {
        // Kotak UPI default.
        Categorizer.categoryFor(
            classification = Classification.UPI_PAYMENT,
            parserAssignedCategory = Category.UPI_TRANSFER,
            merchantKey = "zomato", // would otherwise be FOOD_DINING
        ) shouldBe Category.UPI_TRANSFER
    }

    @Test
    fun `parser-assigned CASH respected on ATM_WITHDRAWAL`() {
        Categorizer.categoryFor(
            classification = Classification.ATM_WITHDRAWAL,
            parserAssignedCategory = Category.CASH,
            merchantKey = null,
        ) shouldBe Category.CASH
    }

    @Test
    fun `parser-assigned UNCATEGORIZED respected on REFUND (Blinkit)`() {
        Categorizer.categoryFor(
            classification = Classification.REFUND,
            parserAssignedCategory = Category.UNCATEGORIZED,
            merchantKey = "blinkit",
        ) shouldBe Category.UNCATEGORIZED
    }

    // ------------------------------------------------------------------
    // §3.2 — keyword buckets (≥2 per major category).
    // ------------------------------------------------------------------

    @Test
    fun `zomato → FOOD_DINING`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "zomato",
        ) shouldBe Category.FOOD_DINING
    }

    @Test
    fun `dominos pizza → FOOD_DINING (substring match)`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "dominos pizza mumbai",
        ) shouldBe Category.FOOD_DINING
    }

    @Test
    fun `uber → TRAVEL_TRANSPORT`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "uber india systems",
        ) shouldBe Category.TRAVEL_TRANSPORT
    }

    @Test
    fun `irctc → TRAVEL_TRANSPORT`() {
        Categorizer.categoryFor(
            Classification.DEBIT_CARD, null, "irctc rail",
        ) shouldBe Category.TRAVEL_TRANSPORT
    }

    @Test
    fun `netflix → ENTERTAINMENT`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "netflix.com",
        ) shouldBe Category.ENTERTAINMENT
    }

    @Test
    fun `github → SUBSCRIPTIONS`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "github, inc",
        ) shouldBe Category.SUBSCRIPTIONS
    }

    @Test
    fun `claude → SUBSCRIPTIONS`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "anthropic claude",
        ) shouldBe Category.SUBSCRIPTIONS
    }

    @Test
    fun `myntra → SHOPPING`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "myntra designs",
        ) shouldBe Category.SHOPPING
    }

    @Test
    fun `tanishq → SHOPPING`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "tanishq jewellery",
        ) shouldBe Category.SHOPPING
    }

    @Test
    fun `airtel → BILLS_UTILITIES`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "airtel payments",
        ) shouldBe Category.BILLS_UTILITIES
    }

    @Test
    fun `tata power → BILLS_UTILITIES`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "tata power ltd",
        ) shouldBe Category.BILLS_UTILITIES
    }

    @Test
    fun `apollo → HEALTH`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "apollo pharmacy",
        ) shouldBe Category.HEALTH
    }

    @Test
    fun `pharmeasy → HEALTH`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "pharmeasy",
        ) shouldBe Category.HEALTH
    }

    @Test
    fun `reliance fresh → GROCERIES`() {
        Categorizer.categoryFor(
            Classification.DEBIT_CARD, null, "reliance fresh andheri",
        ) shouldBe Category.GROCERIES
    }

    // ------------------------------------------------------------------
    // Cross-cutting platforms → UNCATEGORIZED.
    // ------------------------------------------------------------------

    @Test
    fun `amazon → UNCATEGORIZED`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "amazon pay india",
        ) shouldBe Category.UNCATEGORIZED
    }

    @Test
    fun `blinkit → UNCATEGORIZED`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "blinkit",
        ) shouldBe Category.UNCATEGORIZED
    }

    @Test
    fun `flipkart → UNCATEGORIZED`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "flipkart internet",
        ) shouldBe Category.UNCATEGORIZED
    }

    @Test
    fun `swiggy instamart → UNCATEGORIZED (wins over generic swiggy)`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "swiggy instamart",
        ) shouldBe Category.UNCATEGORIZED
    }

    @Test
    fun `bare swiggy → FOOD_DINING (no instamart suffix)`() {
        Categorizer.categoryFor(
            Classification.UPI_PAYMENT, null, "swiggy",
        ) shouldBe Category.FOOD_DINING
    }

    // ------------------------------------------------------------------
    // §3.1 rule 4 — fallback.
    // ------------------------------------------------------------------

    @Test
    fun `unknown merchant VINOD on CC_TRANSACTION → OTHER`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "vinod",
        ) shouldBe Category.OTHER
    }

    @Test
    fun `random merchant on CC_TRANSACTION → OTHER`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "random merchant xyz",
        ) shouldBe Category.OTHER
    }

    @Test
    fun `null merchantKey on CC_TRANSACTION → OTHER (has classification, no merchant)`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, null,
        ) shouldBe Category.OTHER
    }

    @Test
    fun `null merchantKey on REFUND → OTHER`() {
        Categorizer.categoryFor(
            Classification.REFUND, null, null,
        ) shouldBe Category.OTHER
    }

    // ------------------------------------------------------------------
    // Case insensitivity.
    // ------------------------------------------------------------------

    @Test
    fun `uppercase merchantKey still matches keywords (case-insensitive)`() {
        Categorizer.categoryFor(
            Classification.CC_TRANSACTION, null, "ZOMATO LTD",
        ) shouldBe Category.FOOD_DINING
    }
}
