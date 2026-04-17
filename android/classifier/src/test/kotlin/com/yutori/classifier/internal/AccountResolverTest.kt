package com.yutori.classifier.internal

import com.yutori.classifier.Account
import com.yutori.classifier.AccountKind
import com.yutori.parser.Classification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Covers the ambiguous-last4 resolution rules from
 * error-states-spec.md §4.2 and the pipeline step in
 * business-logic-spec.md §2.2.
 */
class AccountResolverTest {

    private val kotakSavings = Account(
        id = 1L,
        kind = AccountKind.SAVINGS,
        issuer = "Kotak",
        last4 = "XX0000",
        isDefaultSpend = true,
    )
    private val axisSavings = Account(
        id = 2L,
        kind = AccountKind.SAVINGS,
        issuer = "Axis",
        last4 = "XX2222",
    )
    private val axisCc = Account(
        id = 3L,
        kind = AccountKind.CREDIT_CARD,
        issuer = "Axis",
        last4 = "XX1111",
    )
    private val kotakCc = Account(
        id = 4L,
        kind = AccountKind.CREDIT_CARD,
        issuer = "Kotak",
        last4 = "x3333",
        isDefaultSpend = true,
    )
    private val paytmInvestment = Account(
        id = 5L,
        kind = AccountKind.INVESTMENT,
        issuer = "Paytm",
        last4 = "X5555",
    )

    private val feasibilityAccounts = listOf(
        kotakSavings, axisSavings, axisCc, kotakCc, paytmInvestment,
    )

    @Test
    fun `null last4 returns null`() {
        AccountResolver.resolve(null, Classification.UPI_PAYMENT, feasibilityAccounts) shouldBe null
    }

    @Test
    fun `null last4 with empty accounts returns null`() {
        AccountResolver.resolve(null, Classification.CC_TRANSACTION, emptyList()) shouldBe null
    }

    @Test
    fun `no matching last4 returns null`() {
        AccountResolver.resolve("9999", Classification.UPI_PAYMENT, feasibilityAccounts) shouldBe null
    }

    @Test
    fun `empty accounts returns null`() {
        AccountResolver.resolve("9999", Classification.UPI_PAYMENT, emptyList()) shouldBe null
    }

    @Test
    fun `single match returns it`() {
        val acc = Account(id = 10L, kind = AccountKind.SAVINGS, issuer = "Kotak", last4 = "1234")
        AccountResolver.resolve("1234", Classification.UPI_PAYMENT, listOf(acc)) shouldBe acc
    }

    @Test
    fun `case-insensitive match - stored uppercase, input lowercase`() {
        val acc = Account(id = 10L, kind = AccountKind.SAVINGS, issuer = "Kotak", last4 = "XX0000")
        AccountResolver.resolve("xx0000", Classification.UPI_PAYMENT, listOf(acc)) shouldBe acc
    }

    @Test
    fun `case-insensitive match - stored mixed-case, input different case`() {
        val acc = Account(id = 10L, kind = AccountKind.CREDIT_CARD, issuer = "Kotak", last4 = "x3333")
        AccountResolver.resolve("X3333", Classification.CC_TRANSACTION, listOf(acc)) shouldBe acc
    }

    @Test
    fun `multiple matches - CC_TRANSACTION picks CREDIT_CARD`() {
        val savings = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "X", last4 = "1111")
        val cc = Account(id = 2L, kind = AccountKind.CREDIT_CARD, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.CC_TRANSACTION, listOf(savings, cc)) shouldBe cc
    }

    @Test
    fun `multiple matches - CC_BILL_PAYMENT picks CREDIT_CARD`() {
        val savings = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "X", last4 = "1111")
        val cc = Account(id = 2L, kind = AccountKind.CREDIT_CARD, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.CC_BILL_PAYMENT, listOf(savings, cc)) shouldBe cc
    }

    @Test
    fun `multiple matches - UPI_PAYMENT picks SAVINGS over CREDIT_CARD`() {
        val savings = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "X", last4 = "1111")
        val cc = Account(id = 2L, kind = AccountKind.CREDIT_CARD, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.UPI_PAYMENT, listOf(savings, cc)) shouldBe savings
    }

    @Test
    fun `multiple matches - UPI_PAYMENT falls back to INVESTMENT if no SAVINGS`() {
        val cc = Account(id = 1L, kind = AccountKind.CREDIT_CARD, issuer = "X", last4 = "1111")
        val inv = Account(id = 2L, kind = AccountKind.INVESTMENT, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.UPI_PAYMENT, listOf(cc, inv)) shouldBe inv
    }

    @Test
    fun `multiple matches - INCOMING_CREDIT prefers SAVINGS`() {
        val savings = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "X", last4 = "1111")
        val inv = Account(id = 2L, kind = AccountKind.INVESTMENT, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.INCOMING_CREDIT, listOf(savings, inv)) shouldBe savings
    }

    @Test
    fun `multiple matches - ATM_WITHDRAWAL prefers SAVINGS`() {
        val savings = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "X", last4 = "1111")
        val cc = Account(id = 2L, kind = AccountKind.CREDIT_CARD, issuer = "X", last4 = "1111")
        AccountResolver.resolve("1111", Classification.ATM_WITHDRAWAL, listOf(savings, cc)) shouldBe savings
    }

    @Test
    fun `multiple matches same kind - isDefaultSpend wins`() {
        val plain = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "A", last4 = "1111")
        val default = Account(
            id = 2L, kind = AccountKind.SAVINGS, issuer = "B", last4 = "1111",
            isDefaultSpend = true,
        )
        AccountResolver.resolve("1111", Classification.UPI_PAYMENT, listOf(plain, default)) shouldBe default
    }

    @Test
    fun `multiple matches same kind - no default returns null`() {
        val a = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "A", last4 = "1111")
        val b = Account(id = 2L, kind = AccountKind.SAVINGS, issuer = "B", last4 = "1111")
        AccountResolver.resolve("1111", Classification.UPI_PAYMENT, listOf(a, b)) shouldBe null
    }

    @Test
    fun `multiple matches same kind - both defaults returns null`() {
        val a = Account(
            id = 1L, kind = AccountKind.SAVINGS, issuer = "A", last4 = "1111",
            isDefaultSpend = true,
        )
        val b = Account(
            id = 2L, kind = AccountKind.SAVINGS, issuer = "B", last4 = "1111",
            isDefaultSpend = true,
        )
        AccountResolver.resolve("1111", Classification.UPI_PAYMENT, listOf(a, b)) shouldBe null
    }

    @Test
    fun `classification with no kind preference - default breaks tie`() {
        val plain = Account(id = 1L, kind = AccountKind.SAVINGS, issuer = "A", last4 = "1111")
        val default = Account(
            id = 2L, kind = AccountKind.CREDIT_CARD, issuer = "B", last4 = "1111",
            isDefaultSpend = true,
        )
        AccountResolver.resolve("1111", Classification.REFUND, listOf(plain, default)) shouldBe default
    }

    @Test
    fun `feasibility dataset - 3333 with CC_TRANSACTION returns Kotak CC`() {
        AccountResolver.resolve(
            last4 = "3333",
            classification = Classification.CC_TRANSACTION,
            accounts = feasibilityAccounts,
        ) shouldBe kotakCc
    }

    @Test
    fun `feasibility dataset - 0000 with UPI_PAYMENT returns Kotak savings`() {
        AccountResolver.resolve(
            last4 = "0000",
            classification = Classification.UPI_PAYMENT,
            accounts = feasibilityAccounts,
        ) shouldBe kotakSavings
    }

    @Test
    fun `feasibility dataset - case-insensitive 3333 match`() {
        AccountResolver.resolve(
            last4 = "X3333",
            classification = Classification.CC_TRANSACTION,
            accounts = feasibilityAccounts,
        ) shouldBe kotakCc
    }
}
