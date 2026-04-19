package com.yutori.classifier.internal

import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.parser.Classification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SelfTransferHeuristicTest {

    private fun rule(reclassifyAs: Classification?) = RecipientRule(
        id = 1,
        pattern = "ignored",
        patternKind = PatternKind.LITERAL,
        reclassifyAs = reclassifyAs,
    )

    @Test
    fun `no matched rule returns raw classification`() {
        SelfTransferHeuristic.apply(Classification.UPI_PAYMENT, null) shouldBe
            Classification.UPI_PAYMENT
    }

    @Test
    fun `category-only rule (null reclassify) returns raw classification`() {
        SelfTransferHeuristic.apply(
            Classification.UPI_PAYMENT,
            rule(reclassifyAs = null),
        ) shouldBe Classification.UPI_PAYMENT
    }

    @Test
    fun `UPI_PAYMENT plus SELF_TRANSFER rule becomes SELF_TRANSFER`() {
        SelfTransferHeuristic.apply(
            Classification.UPI_PAYMENT,
            rule(Classification.SELF_TRANSFER),
        ) shouldBe Classification.SELF_TRANSFER
    }

    @Test
    fun `INCOMING_CREDIT plus SELF_TRANSFER rule becomes SELF_TRANSFER`() {
        SelfTransferHeuristic.apply(
            Classification.INCOMING_CREDIT,
            rule(Classification.SELF_TRANSFER),
        ) shouldBe Classification.SELF_TRANSFER
    }

    @Test
    fun `CC_TRANSACTION plus SELF_TRANSFER rule stays CC_TRANSACTION`() {
        // §2.3 guard: SELF_TRANSFER doesn't make sense over CC rails.
        SelfTransferHeuristic.apply(
            Classification.CC_TRANSACTION,
            rule(Classification.SELF_TRANSFER),
        ) shouldBe Classification.CC_TRANSACTION
    }

    @Test
    fun `DEBIT_CARD plus SELF_TRANSFER rule stays DEBIT_CARD`() {
        SelfTransferHeuristic.apply(
            Classification.DEBIT_CARD,
            rule(Classification.SELF_TRANSFER),
        ) shouldBe Classification.DEBIT_CARD
    }

    @Test
    fun `UPI_PAYMENT plus CC_BILL_PAYMENT rule becomes CC_BILL_PAYMENT`() {
        // User-added middleman rule; non-SELF_TRANSFER reclassifications
        // are applied unconditionally.
        SelfTransferHeuristic.apply(
            Classification.UPI_PAYMENT,
            rule(Classification.CC_BILL_PAYMENT),
        ) shouldBe Classification.CC_BILL_PAYMENT
    }

    @Test
    fun `CC_TRANSACTION plus NON_FINANCIAL rule becomes NON_FINANCIAL`() {
        SelfTransferHeuristic.apply(
            Classification.CC_TRANSACTION,
            rule(Classification.NON_FINANCIAL),
        ) shouldBe Classification.NON_FINANCIAL
    }

    @Test
    fun `INCOMING_CREDIT plus REFUND rule becomes REFUND`() {
        // Future: §12.1 "mark as payback" action.
        SelfTransferHeuristic.apply(
            Classification.INCOMING_CREDIT,
            rule(Classification.REFUND),
        ) shouldBe Classification.REFUND
    }
}
