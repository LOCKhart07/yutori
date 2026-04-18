package com.yutori.classifier.suggestions

import com.yutori.classifier.Account
import com.yutori.classifier.AccountKind
import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.classifier.RuleSource
import com.yutori.parser.Classification
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SuggestionInferenceTest {

    private val kotak = Account(
        id = 1,
        kind = AccountKind.SAVINGS,
        issuer = "Kotak",
        last4 = "0000",
    )
    private val axis = Account(
        id = 2,
        kind = AccountKind.SAVINGS,
        issuer = "Axis",
        last4 = "2222",
    )

    private fun candidate(key: String) = SuggestionCandidate(key, matchCount = 5, totalInr = 1000.0)

    private fun selfTransferRule(pattern: String, accountId: Long) = RecipientRule(
        id = 0,
        pattern = pattern,
        patternKind = PatternKind.LITERAL,
        reclassifyAs = Classification.SELF_TRANSFER,
        accountId = accountId,
        source = RuleSource.USER,
    )

    @Test
    fun `own-handle shape match infers SELF_TRANSFER with that account id`() {
        val rules = listOf(selfTransferRule("jenslee@okhdfcbank", kotak.id))

        val result = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            accounts = listOf(kotak, axis),
            recipientRules = rules,
        )

        result.inferredClassification shouldBe Classification.SELF_TRANSFER
        result.inferredAccountId shouldBe kotak.id
        result.reasonCode shouldBe ReasonCode.OWN_HANDLE_SHAPE
        result.patternKind shouldBe PatternKind.LITERAL
        result.pattern shouldBe "jenslee@ybl"
    }

    @Test
    fun `own-handle match requires a different domain`() {
        // Same local AND same domain means it's literally the registered handle —
        // already covered by the existing rule, should never reach the miner.
        // The safety check here belongs to the already-covered filter, but the
        // inference itself should not claim OWN_HANDLE_SHAPE when domains match.
        val rules = listOf(selfTransferRule("jenslee@okhdfcbank", kotak.id))

        val result = SuggestionInference.infer(
            candidate("jenslee@okhdfcbank"),
            accounts = listOf(kotak),
            recipientRules = rules,
        )

        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
        result.inferredClassification.shouldBeNull()
    }

    @Test
    fun `own-handle match is case-insensitive on local-part`() {
        val rules = listOf(selfTransferRule("JensLee@okhdfcbank", kotak.id))

        val result = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            accounts = listOf(kotak),
            recipientRules = rules,
        )

        result.inferredClassification shouldBe Classification.SELF_TRANSFER
        result.inferredAccountId shouldBe kotak.id
    }

    @Test
    fun `middleman keyword match infers CC_BILL_PAYMENT`() {
        val result = SuggestionInference.infer(
            candidate("cheq@axisbank"),
            accounts = emptyList(),
            recipientRules = emptyList(),
        )

        result.inferredClassification shouldBe Classification.CC_BILL_PAYMENT
        result.inferredAccountId.shouldBeNull()
        result.reasonCode shouldBe ReasonCode.KEYWORD_MIDDLEMAN
        result.patternKind shouldBe PatternKind.LITERAL
    }

    @Test
    fun `middleman keyword match is case-insensitive`() {
        val result = SuggestionInference.infer(
            candidate("CREDCLUB@axisb"),
            accounts = emptyList(),
            recipientRules = emptyList(),
        )

        result.inferredClassification shouldBe Classification.CC_BILL_PAYMENT
        result.reasonCode shouldBe ReasonCode.KEYWORD_MIDDLEMAN
    }

    @Test
    fun `own-handle check wins over middleman keyword`() {
        // Pathological but possible — a VPA that matches both signals.
        val rules = listOf(selfTransferRule("cred@okhdfcbank", kotak.id))

        val result = SuggestionInference.infer(
            candidate("cred@ybl"),
            accounts = listOf(kotak),
            recipientRules = rules,
        )

        result.reasonCode shouldBe ReasonCode.OWN_HANDLE_SHAPE
        result.inferredClassification shouldBe Classification.SELF_TRANSFER
    }

    @Test
    fun `no heuristic match leaves classification null`() {
        val result = SuggestionInference.infer(
            candidate("swiggy-payments@paytm"),
            accounts = emptyList(),
            recipientRules = emptyList(),
        )

        result.inferredClassification.shouldBeNull()
        result.inferredAccountId.shouldBeNull()
        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
    }

    @Test
    fun `disabled own-handle rule does not fire inference`() {
        val disabled = selfTransferRule("jenslee@okhdfcbank", kotak.id).copy(isEnabled = false)

        val result = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            accounts = listOf(kotak),
            recipientRules = listOf(disabled),
        )

        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
    }

    @Test
    fun `own-handle rule whose account was deleted is ignored`() {
        // accountId references an account no longer in the accounts list.
        val stale = selfTransferRule("jenslee@okhdfcbank", accountId = 999)

        val result = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            accounts = listOf(kotak, axis),
            recipientRules = listOf(stale),
        )

        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
    }

    @Test
    fun `own-handle match requires candidate to contain @`() {
        // A raw merchant string without a VPA shape can never be an own-handle.
        val rules = listOf(selfTransferRule("jenslee@okhdfcbank", kotak.id))

        val result = SuggestionInference.infer(
            candidate("AMAZON PAYMENTS"),
            accounts = listOf(kotak),
            recipientRules = rules,
        )

        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
    }

    @Test
    fun `pattern is always the merchant_key verbatim`() {
        val result = SuggestionInference.infer(
            candidate("cheq@axisbank"),
            accounts = emptyList(),
            recipientRules = emptyList(),
        )

        result.pattern shouldBe "cheq@axisbank"
    }

    @Test
    fun `pattern_kind is always LITERAL in v1 inference`() {
        val a = SuggestionInference.infer(candidate("cheq@x"), emptyList(), emptyList())
        val b = SuggestionInference.infer(candidate("amazon"), emptyList(), emptyList())
        val c = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            listOf(kotak),
            listOf(selfTransferRule("jenslee@okhdfcbank", kotak.id)),
        )

        a.patternKind shouldBe PatternKind.LITERAL
        b.patternKind shouldBe PatternKind.LITERAL
        c.patternKind shouldBe PatternKind.LITERAL
    }

    @Test
    fun `non-self-transfer rule with matching pattern does not trigger own-handle`() {
        // A CC_BILL_PAYMENT rule with accountId set (shouldn't happen today but
        // defensively handled) must not be misread as an own-handle signal.
        val ccBillRule = RecipientRule(
            id = 0,
            pattern = "jenslee@okhdfcbank",
            patternKind = PatternKind.LITERAL,
            reclassifyAs = Classification.CC_BILL_PAYMENT,
            accountId = kotak.id,
            source = RuleSource.USER,
        )

        val result = SuggestionInference.infer(
            candidate("jenslee@ybl"),
            accounts = listOf(kotak),
            recipientRules = listOf(ccBillRule),
        )

        result.reasonCode shouldBe ReasonCode.REPEAT_NO_DEFAULT
    }
}
