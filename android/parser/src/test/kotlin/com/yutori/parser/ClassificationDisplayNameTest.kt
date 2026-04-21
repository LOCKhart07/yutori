package com.yutori.parser

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ClassificationDisplayNameTest {

    @Test
    fun `every Classification has a distinct, human-readable display name`() {
        val mapping = mapOf(
            Classification.CC_TRANSACTION to "CC transaction",
            Classification.CC_BILL_PAYMENT to "CC bill payment",
            Classification.UPI_PAYMENT to "UPI payment",
            Classification.DEBIT_CARD to "Debit card",
            Classification.ATM_WITHDRAWAL to "ATM withdrawal",
            Classification.REFUND to "Refund",
            Classification.CASHBACK to "Cashback",
            Classification.INCOMING_CREDIT to "Incoming credit",
            Classification.OTP to "OTP",
            Classification.BALANCE_ALERT to "Balance alert",
            Classification.NON_FINANCIAL to "Non-financial",
            Classification.SELF_TRANSFER to "Self-transfer",
            Classification.UNMATCHED to "Unmatched",
        )

        // Coverage: every enum value must appear in the mapping.
        mapping.keys shouldBe Classification.values().toSet()

        // Correctness: each value's displayName matches the pinned string.
        mapping.forEach { (c, expected) -> c.displayName shouldBe expected }

        // Uniqueness: no two classifications share a label.
        mapping.values.toSet().size shouldBe mapping.size
    }
}
