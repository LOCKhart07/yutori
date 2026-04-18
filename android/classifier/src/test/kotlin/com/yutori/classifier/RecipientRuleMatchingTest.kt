package com.yutori.classifier

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class RecipientRuleMatchingTest {

    private val merchants = listOf(
        "cheq@axisbank",
        "cheqpaytmcc@axisbank",
        "billpay.paytmccbill@axisbank",
        "swiggy@paytm",
        "jenslee@oksbi",
        "amazon.india@apl",
    )

    @Test
    fun `empty pattern returns no matches without touching merchants`() {
        val eval = RecipientRuleMatching.evalDraft("", PatternKind.LITERAL, merchants)
        eval.shouldBeInstanceOf<RecipientRuleMatching.DraftEval.Valid>()
        (eval as RecipientRuleMatching.DraftEval.Valid).matches shouldBe emptyList()
    }

    @Test
    fun `LITERAL only matches exact string`() {
        val eval = RecipientRuleMatching.evalDraft("cheq@axisbank", PatternKind.LITERAL, merchants)
            as RecipientRuleMatching.DraftEval.Valid
        eval.matches shouldBe listOf("cheq@axisbank")
    }

    @Test
    fun `PREFIX matches startsWith`() {
        val eval = RecipientRuleMatching.evalDraft("cheq", PatternKind.PREFIX, merchants)
            as RecipientRuleMatching.DraftEval.Valid
        eval.matches shouldBe listOf("cheq@axisbank", "cheqpaytmcc@axisbank")
    }

    @Test
    fun `REGEX matches containsMatchIn semantics`() {
        val eval = RecipientRuleMatching.evalDraft("paytm.*cc", PatternKind.REGEX, merchants)
            as RecipientRuleMatching.DraftEval.Valid
        eval.matches shouldBe listOf("cheqpaytmcc@axisbank", "billpay.paytmccbill@axisbank")
    }

    @Test
    fun `invalid regex returns Invalid with an error message`() {
        val eval = RecipientRuleMatching.evalDraft("[unclosed", PatternKind.REGEX, merchants)
        eval.shouldBeInstanceOf<RecipientRuleMatching.DraftEval.Invalid>()
        (eval as RecipientRuleMatching.DraftEval.Invalid).error.shouldContain("character class")
    }

    @Test
    fun `isCovered remains true for any merchant when a matching rule exists`() {
        val rule = RecipientRule(
            id = 1,
            pattern = "cheq@axisbank",
            patternKind = PatternKind.LITERAL,
            reclassifyAs = com.yutori.parser.Classification.CC_BILL_PAYMENT,
        )
        RecipientRuleMatching.isCovered("cheq@axisbank", listOf(rule)) shouldBe true
        RecipientRuleMatching.isCovered("something-else", listOf(rule)) shouldBe false
    }
}
