package com.spendwise.classifier

import com.spendwise.parser.Classification
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class AccountAutoDetectorTest {

    private val noAccounts = emptyList<Account>()

    @Test
    fun `CC_TRANSACTION with unknown CC last4 proposes CREDIT_CARD`() {
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = noAccounts,
        )
        action.shouldBeInstanceOf<AccountAutoDetector.Action.Create>()
        action.issuer shouldBe "Axis"
        action.last4 shouldBe "XX1111"
        action.kind shouldBe AccountKind.CREDIT_CARD
    }

    @Test
    fun `UPI_PAYMENT with unknown last4 proposes SAVINGS`() {
        val action = AccountAutoDetector.detect(
            sender = "VM-KOTAKB",
            parserLast4 = "X0000",
            classification = Classification.UPI_PAYMENT,
            existingAccounts = noAccounts,
        )
        action.shouldBeInstanceOf<AccountAutoDetector.Action.Create>()
        action.issuer shouldBe "Kotak"
        action.kind shouldBe AccountKind.SAVINGS
    }

    @Test
    fun `OTP classifications do not propose`() {
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.OTP,
            existingAccounts = noAccounts,
        )
        action shouldBe null
    }

    @Test
    fun `unknown issuer does not propose`() {
        val action = AccountAutoDetector.detect(
            sender = "VM-OBSCUREBANK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = noAccounts,
        )
        action shouldBe null
    }

    @Test
    fun `missing last4 does not propose`() {
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = null,
            classification = Classification.CC_TRANSACTION,
            existingAccounts = noAccounts,
        )
        action shouldBe null
    }

    @Test
    fun `existing CONFIRMED account — no action`() {
        val existing = Account(
            id = 1, kind = AccountKind.CREDIT_CARD,
            issuer = "Axis", last4 = "XX1111",
            status = AccountStatus.CONFIRMED,
        )
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = listOf(existing),
        )
        action shouldBe null
    }

    @Test
    fun `existing DISMISSED account — no action (sticky)`() {
        val existing = Account(
            id = 1, kind = AccountKind.CREDIT_CARD,
            issuer = "Axis", last4 = "XX1111",
            status = AccountStatus.DISMISSED,
        )
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = listOf(existing),
        )
        action shouldBe null
    }

    @Test
    fun `existing SUGGESTED account — bumps seen count`() {
        val existing = Account(
            id = 42, kind = AccountKind.CREDIT_CARD,
            issuer = "Axis", last4 = "XX1111",
            status = AccountStatus.SUGGESTED,
        )
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = listOf(existing),
        )
        action.shouldBeInstanceOf<AccountAutoDetector.Action.BumpSeen>()
        action.accountId shouldBe 42
    }

    @Test
    fun `issuer and last4 match case-insensitively`() {
        val existing = Account(
            id = 1, kind = AccountKind.CREDIT_CARD,
            issuer = "AXIS", last4 = "xx1111",
            status = AccountStatus.CONFIRMED,
        )
        val action = AccountAutoDetector.detect(
            sender = "VK-AXISBK",
            parserLast4 = "XX1111",
            classification = Classification.CC_TRANSACTION,
            existingAccounts = listOf(existing),
        )
        action shouldBe null
    }
}
