package com.yutori.classifier

import com.yutori.parser.Category
import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * End-to-end classifier pipeline tests. Each case corresponds to a
 * worked example in business-logic-spec.md §10.
 *
 * Uses a fixed set of "feasibility" accounts + seed recipient rules,
 * matching what v1 will ship with.
 */
class ClassifierPipelineTest {

    // --- Fixture: user's registered accounts, modeled on the shape of
    //     the real portfolio (Kotak + Axis + Vasai Janata + Paytm Money). ---

    private val kotakSavings =
        Account(id = 1, kind = AccountKind.SAVINGS, issuer = "Kotak",
                last4 = "XX0000", isDefaultSpend = true)
    private val axisSavings =
        Account(id = 2, kind = AccountKind.SAVINGS, issuer = "Axis",
                last4 = "XX2222")
    private val axisCc =
        Account(id = 3, kind = AccountKind.CREDIT_CARD, issuer = "Axis",
                last4 = "XX1111")
    private val kotakCc =
        Account(id = 4, kind = AccountKind.CREDIT_CARD, issuer = "Kotak",
                last4 = "x3333")
    private val paytmMoney =
        Account(id = 5, kind = AccountKind.INVESTMENT, issuer = "Paytm",
                last4 = "X5555")

    private val accounts = listOf(kotakSavings, axisSavings, axisCc, kotakCc, paytmMoney)

    // --- Fixture: recipient rules seeded by the app + user. ---

    private val credMiddleman = RecipientRule(
        id = 100,
        pattern = """cred\.club[@¡]axisb""",
        patternKind = PatternKind.REGEX,
        reclassifyAs = Classification.CC_BILL_PAYMENT,
        source = RuleSource.SEED,
        note = "CRED CC bill payments",
    )

    private val ownOksbiVpa = RecipientRule(
        id = 200,
        pattern = """examplename-\d+@oksbi""",
        patternKind = PatternKind.REGEX,
        reclassifyAs = Classification.SELF_TRANSFER,
        accountId = axisSavings.id,
        source = RuleSource.USER,
        note = "user's own Axis A/c via Google Pay",
    )

    private val rules = listOf(credMiddleman, ownOksbiVpa)

    // --- Helpers ---

    private fun classify(parseResult: ParseResult) =
        Classifier.classify(parseResult, accounts, rules)

    // =====================================================================
    // §10.1 — CRED bill payment (parser already classifies as
    // CC_BILL_PAYMENT via middleman detection; classifier adds no change).
    // =====================================================================

    @Test
    fun `§10_1 — CRED payment stays CC_BILL_PAYMENT and drops from budget`() {
        val parseResult = ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 1000.0,
            merchant = "cred.club@axisb",
            last4 = "0000",
            pattern = "kotak_upi_debit",
        )

        val outcome = classify(parseResult)

        outcome.finalClassification shouldBe Classification.CC_BILL_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.accountId shouldBe kotakSavings.id
        outcome.classificationOriginal.shouldBeNull()
        outcome.category.shouldBeNull()   // DROP events have no category
    }

    // =====================================================================
    // §10.2 — Axis CC bill payment pair. Both SMSes come in as
    // CC_BILL_PAYMENT from the parser; both yield DROP. Dedup/merge is a
    // transactions-layer concern, not ours.
    // =====================================================================

    @Test
    fun `§10_2 — Axis savings debit for CC payment yields DROP`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.CC_BILL_PAYMENT,
                amount = 1000.0,
                last4 = "2222",
                pattern = "axis_savings_cc_bill_debit",
            ),
        )
        outcome.finalClassification shouldBe Classification.CC_BILL_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.accountId shouldBe axisSavings.id
    }

    @Test
    fun `§10_2 — Axis CC 'Payment received' also yields DROP`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.CC_BILL_PAYMENT,
                amount = 1000.0,
                last4 = "1111",
                pattern = "axis_cc_bill_received",
            ),
        )
        outcome.finalClassification shouldBe Classification.CC_BILL_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.accountId shouldBe axisCc.id
    }

    // =====================================================================
    // §10.3 — VVCMC property tax multi-party. Parser-level tests already
    // cover the per-SMS classification. At the classifier level:
    //   - ICICI eazypay → UPI_PAYMENT / SPEND, Bills & Utilities.
    //   - The Axis collect request and VVCMC ack are UNMATCHED → DROP.
    // =====================================================================

    @Test
    fun `§10_3 — ICICI eazypay is UPI_PAYMENT and Bills & Utilities`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.UPI_PAYMENT,
                amount = 100.0,
                merchant = "VVCMC ONLINE PROPERTY TAX ACCOUNT",
                last4 = null,
                pattern = "icici_eazypay",
            ),
        )
        outcome.finalClassification shouldBe Classification.UPI_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.SPEND
        outcome.accountId.shouldBeNull()  // no last4 — can't resolve
        outcome.merchantKey shouldBe "vvcmc online property tax account"
        // Property tax is a utility bill-shaped thing; no current keyword
        // matches so it falls through to OTHER. v1.1 can add "vvcmc" or
        // "property tax" as a BILLS_UTILITIES keyword.
        outcome.category shouldBe Category.OTHER
    }

    // =====================================================================
    // §10.4 — Salary + P2P payback. Both currently bucket to INCOME in v1;
    // the §12.1 "mark as payback" UX that would distinguish them is
    // deferred.
    // =====================================================================

    @Test
    fun `§10_4 — ACME CORP salary NEFT credit is INCOME`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.INCOMING_CREDIT,
                amount = 50000.00,
                merchant = "ACME CORP",
                last4 = "0000",
                pattern = "kotak_neft_credit",
            ),
        )
        outcome.finalClassification shouldBe Classification.INCOMING_CREDIT
        outcome.budgetEffect shouldBe BudgetEffect.INCOME
        outcome.accountId shouldBe kotakSavings.id
        outcome.category.shouldBeNull()
    }

    @Test
    fun `§10_4 — P2P payback from a friend's yescred VPA is also INCOME`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.INCOMING_CREDIT,
                amount = 100.0,
                merchant = "9999999999@yescred",
                last4 = "0000",
                pattern = "kotak_upi_credit",
            ),
        )
        outcome.finalClassification shouldBe Classification.INCOMING_CREDIT
        outcome.budgetEffect shouldBe BudgetEffect.INCOME
    }

    // =====================================================================
    // §10.5 — Self-transfer Kotak → Axis. The UPI_PAYMENT recipient
    // (examplename-4@oksbi) matches the user's registered self-transfer
    // rule → SELF_TRANSFER → DROP.
    // =====================================================================

    @Test
    fun `§10_5 — UPI debit to own oksbi VPA reclassifies as SELF_TRANSFER`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.UPI_PAYMENT,
                amount = 100.0,
                merchant = "examplename-4@oksbi",
                last4 = "0000",
                category = Category.UPI_TRANSFER,
                pattern = "kotak_upi_debit",
            ),
        )

        outcome.finalClassification shouldBe Classification.SELF_TRANSFER
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.classificationOriginal shouldBe Classification.UPI_PAYMENT
        outcome.matchedRuleId shouldBe ownOksbiVpa.id
        outcome.accountId shouldBe kotakSavings.id  // source account
        outcome.category.shouldBeNull()               // SELF_TRANSFER has no category
    }

    // =====================================================================
    // §10.6 — Foreign-currency subscription. Classifier records original
    // currency + amount; INR conversion is downstream (forex worker).
    // =====================================================================

    @Test
    fun `§10_6 — USD CC transaction preserves currency and amount`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.CC_TRANSACTION,
                amount = 10.00,
                currency = "USD",
                merchant = "GITHUB, INC",
                last4 = "1111",
                pattern = "axis_cc_spend",
            ),
        )

        outcome.finalClassification shouldBe Classification.CC_TRANSACTION
        outcome.budgetEffect shouldBe BudgetEffect.SPEND
        outcome.amount shouldBe 10.00
        outcome.currency shouldBe "USD"
        outcome.accountId shouldBe axisCc.id
        outcome.category shouldBe Category.SUBSCRIPTIONS   // "github" keyword
    }

    // =====================================================================
    // Edge cases not in §10 but enforcing pipeline contracts.
    // =====================================================================

    @Test
    fun `UNMATCHED parser result shortcuts to UNMATCHED-DROP`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.UNMATCHED,
                pattern = "UNMATCHED",
            ),
        )

        outcome.finalClassification shouldBe Classification.UNMATCHED
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.accountId.shouldBeNull()
        outcome.classificationOriginal.shouldBeNull()
    }

    @Test
    fun `classification_original is null when no reclassification occurs`() {
        val outcome = classify(
            ParseResult(
                classification = Classification.CC_TRANSACTION,
                amount = 500.0,
                merchant = "Zomato",
                last4 = "3333",
                pattern = "kotak_cc_spend",
            ),
        )
        outcome.classificationOriginal.shouldBeNull()
    }

    @Test
    fun `CRED middleman regex matches even with encoding-glitch inverted exclamation`() {
        // Parser already catches the primary CRED template; simulate the
        // rare case where a UPI_PAYMENT reaches the classifier and needs
        // rule-based reclassification.
        val outcome = classify(
            ParseResult(
                classification = Classification.UPI_PAYMENT,
                amount = 500.00,
                merchant = "cred.club¡axisb",
                last4 = "0000",
                pattern = "kotak_upi_debit",
            ),
        )

        outcome.finalClassification shouldBe Classification.CC_BILL_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.classificationOriginal shouldBe Classification.UPI_PAYMENT
        outcome.matchedRuleId shouldBe credMiddleman.id
    }

    @Test
    fun `category-only rule (null reclassify) preserves classification and tags category`() {
        val rule = RecipientRule(
            id = 300,
            pattern = "swiggy-newbrand@paytm",
            patternKind = PatternKind.LITERAL,
            reclassifyAs = null,
            assignedCategory = Category.FOOD_DINING,
        )
        val outcome = Classifier.classify(
            parseResult = ParseResult(
                classification = Classification.UPI_PAYMENT,
                amount = 350.0,
                merchant = "swiggy-newbrand@paytm",
                pattern = "kotak_upi_debit",
            ),
            accounts = accounts,
            recipientRules = listOf(rule),
        )

        outcome.finalClassification shouldBe Classification.UPI_PAYMENT
        outcome.budgetEffect shouldBe BudgetEffect.SPEND
        outcome.category shouldBe Category.FOOD_DINING
        // No reclassification happened, so classification_original stays
        // null per Classifier.classify().
        outcome.classificationOriginal shouldBe null
        outcome.matchedRuleId shouldBe rule.id
        // Inferred snapshot mirrors the live values at ingest.
        outcome.classificationInferred shouldBe Classification.UPI_PAYMENT
        outcome.categoryInferred shouldBe Category.FOOD_DINING
    }

    @Test
    fun `rule with reclassify and category applies both`() {
        val rule = RecipientRule(
            id = 301,
            pattern = "refund-merchant@upi",
            patternKind = PatternKind.LITERAL,
            reclassifyAs = Classification.REFUND,
            assignedCategory = Category.SHOPPING,
        )
        val outcome = Classifier.classify(
            parseResult = ParseResult(
                classification = Classification.UPI_PAYMENT,
                amount = 100.0,
                merchant = "refund-merchant@upi",
                pattern = "kotak_upi_debit",
            ),
            accounts = accounts,
            recipientRules = listOf(rule),
        )

        outcome.finalClassification shouldBe Classification.REFUND
        outcome.classificationOriginal shouldBe Classification.UPI_PAYMENT
        outcome.category shouldBe Category.SHOPPING
    }
}
