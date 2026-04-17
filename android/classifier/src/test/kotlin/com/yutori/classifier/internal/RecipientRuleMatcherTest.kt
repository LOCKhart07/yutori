package com.yutori.classifier.internal

import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.parser.Classification
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RecipientRuleMatcherTest {

    private fun rule(
        id: Long,
        pattern: String,
        kind: PatternKind,
        reclassifyAs: Classification = Classification.UPI_PAYMENT,
        isEnabled: Boolean = true,
    ) = RecipientRule(
        id = id,
        pattern = pattern,
        patternKind = kind,
        reclassifyAs = reclassifyAs,
        isEnabled = isEnabled,
    )

    @Test
    fun `null merchant returns null`() {
        RecipientRuleMatcher.firstMatch(
            null,
            listOf(rule(1, "anything", PatternKind.LITERAL)),
        ).shouldBeNull()
    }

    @Test
    fun `empty rules returns null`() {
        RecipientRuleMatcher.firstMatch("cred.club@axisb", emptyList()).shouldBeNull()
    }

    @Test
    fun `LITERAL exact match returns rule`() {
        val r = rule(1, "CRED", PatternKind.LITERAL)
        RecipientRuleMatcher.firstMatch("CRED", listOf(r)) shouldBe r
    }

    @Test
    fun `LITERAL is case-sensitive`() {
        val r = rule(1, "CRED", PatternKind.LITERAL)
        RecipientRuleMatcher.firstMatch("cred", listOf(r)).shouldBeNull()
    }

    @Test
    fun `PREFIX matches when merchant starts with pattern`() {
        val r = rule(1, "cred.club", PatternKind.PREFIX)
        RecipientRuleMatcher.firstMatch("cred.club@axisb", listOf(r)) shouldBe r
    }

    @Test
    fun `PREFIX does not match when merchant is shorter`() {
        val r = rule(1, "cred.club", PatternKind.PREFIX)
        RecipientRuleMatcher.firstMatch("cr", listOf(r)).shouldBeNull()
    }

    @Test
    fun `REGEX matches alternation class - at sign variant`() {
        val r = rule(1, "cred\\.club[@¡]axisb", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("cred.club@axisb", listOf(r)) shouldBe r
    }

    @Test
    fun `REGEX matches alternation class - inverted exclamation variant`() {
        val r = rule(1, "cred\\.club[@¡]axisb", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("cred.club¡axisb", listOf(r)) shouldBe r
    }

    @Test
    fun `REGEX does not match unrelated merchant`() {
        val r = rule(1, "cred\\.club[@¡]axisb", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("cred@other", listOf(r)).shouldBeNull()
    }

    @Test
    fun `REGEX with anchors matches exact string`() {
        val r = rule(1, "^paytmcc$", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("paytmcc", listOf(r)) shouldBe r
    }

    @Test
    fun `REGEX with start anchor rejects prefixed strings`() {
        val r = rule(1, "^paytmcc$", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("xpaytmcc", listOf(r)).shouldBeNull()
    }

    @Test
    fun `disabled rule is skipped and later enabled rule matches`() {
        val disabled = rule(1, "cred.club", PatternKind.PREFIX, isEnabled = false)
        val enabled = rule(2, "cred.club", PatternKind.PREFIX, isEnabled = true)
        RecipientRuleMatcher.firstMatch(
            "cred.club@axisb",
            listOf(disabled, enabled),
        ) shouldBe enabled
    }

    @Test
    fun `only-disabled matching rule returns null`() {
        val disabled = rule(1, "cred.club", PatternKind.PREFIX, isEnabled = false)
        RecipientRuleMatcher.firstMatch("cred.club@axisb", listOf(disabled)).shouldBeNull()
    }

    @Test
    fun `first-match-wins when multiple rules match`() {
        val first = rule(1, "cred", PatternKind.PREFIX, reclassifyAs = Classification.CC_BILL_PAYMENT)
        val second = rule(2, "cred.club", PatternKind.PREFIX, reclassifyAs = Classification.UPI_PAYMENT)
        RecipientRuleMatcher.firstMatch(
            "cred.club@axisb",
            listOf(first, second),
        ) shouldBe first
    }

    @Test
    fun `malformed regex is skipped and later valid rule matches`() {
        val bad = rule(1, "[unclosed", PatternKind.REGEX)
        val good = rule(2, "cred\\.club", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch(
            "cred.club@axisb",
            listOf(bad, good),
        ) shouldBe good
    }

    @Test
    fun `malformed regex as only rule returns null without throwing`() {
        val bad = rule(1, "[unclosed", PatternKind.REGEX)
        RecipientRuleMatcher.firstMatch("cred.club@axisb", listOf(bad)).shouldBeNull()
    }

    @Test
    fun `realistic seed scenario - cred club regex reclassifies to CC_BILL_PAYMENT`() {
        val seed = rule(
            id = 1,
            pattern = "cred\\.club[@¡]axisb",
            kind = PatternKind.REGEX,
            reclassifyAs = Classification.CC_BILL_PAYMENT,
        )
        val matched = RecipientRuleMatcher.firstMatch("cred.club@axisb", listOf(seed))
        matched shouldBe seed
        matched?.reclassifyAs shouldBe Classification.CC_BILL_PAYMENT
    }
}
