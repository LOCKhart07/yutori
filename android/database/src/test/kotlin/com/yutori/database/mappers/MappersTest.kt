package com.yutori.database.mappers

import com.yutori.budget.AlertFiring
import com.yutori.budget.Budget
import com.yutori.classifier.Account
import com.yutori.classifier.AccountKind
import com.yutori.classifier.BudgetEffect
import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.classifier.RuleSource
import com.yutori.parser.Category
import com.yutori.parser.Classification
import com.yutori.transactions.SourceRole
import com.yutori.transactions.TransactionRow
import com.yutori.transactions.TransactionSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Round-trip tests: domain → entity → domain produces the original,
 * and entity → domain → entity produces the original (minus
 * timestamps that are supplied externally).
 *
 * Pure JVM; no Room runtime required.
 */
class MappersTest {

    // --- AccountMapper ---

    @Test
    fun `Account round-trips via AccountEntity`() {
        val source = Account(
            id = 42,
            kind = AccountKind.SAVINGS,
            issuer = "Kotak",
            last4 = "XX0000",
            displayName = "Primary",
            isDefaultSpend = true,
        )
        val entity = AccountMapper.toEntity(source, createdAtMs = 1_700_000_000_000L)
        AccountMapper.toDomain(entity) shouldBe source
    }

    @Test
    fun `Account with null last4 round-trips via AccountEntity`() {
        // Issue #6: UPI-only accounts (Paytm, PhonePe, bank UPI apps)
        // have no last-4. Ensure the mapper preserves null instead of
        // coercing to empty string or crashing.
        val source = Account(
            id = 11,
            kind = AccountKind.SAVINGS,
            issuer = "Paytm",
            last4 = null,
            displayName = "UPI wallet",
            isDefaultSpend = false,
        )
        val entity = AccountMapper.toEntity(source, createdAtMs = 0L)
        entity.last4 shouldBe null
        AccountMapper.toDomain(entity) shouldBe source
    }

    @Test
    fun `AccountEntity with every AccountKind round-trips`() {
        AccountKind.entries.forEach { kind ->
            val account = Account(
                id = 1, kind = kind, issuer = "X", last4 = "0000",
            )
            val round = AccountMapper.toDomain(
                AccountMapper.toEntity(account, 0L),
            )
            round.kind shouldBe kind
        }
    }

    // --- RecipientRuleMapper ---

    @Test
    fun `RecipientRule round-trips`() {
        val source = RecipientRule(
            id = 7,
            pattern = """cred\.club[@¡]axisb""",
            patternKind = PatternKind.REGEX,
            reclassifyAs = Classification.CC_BILL_PAYMENT,
            accountId = null,
            source = RuleSource.SEED,
            isEnabled = true,
            note = "CRED CC bill payments",
        )
        val entity = RecipientRuleMapper.toEntity(source)
        RecipientRuleMapper.toDomain(entity) shouldBe source
    }

    @Test
    fun `every PatternKind survives the round trip`() {
        PatternKind.entries.forEach { kind ->
            val rule = RecipientRule(
                id = 1,
                pattern = "x",
                patternKind = kind,
                reclassifyAs = Classification.SELF_TRANSFER,
            )
            RecipientRuleMapper.toDomain(
                RecipientRuleMapper.toEntity(rule),
            ).patternKind shouldBe kind
        }
    }

    @Test
    fun `every RuleSource survives the round trip`() {
        RuleSource.entries.forEach { source ->
            val rule = RecipientRule(
                id = 1,
                pattern = "x",
                patternKind = PatternKind.LITERAL,
                reclassifyAs = Classification.UPI_PAYMENT,
                source = source,
            )
            RecipientRuleMapper.toDomain(
                RecipientRuleMapper.toEntity(rule),
            ).source shouldBe source
        }
    }

    // --- TransactionMapper ---

    @Test
    fun `TransactionRow with full INR shape round-trips`() {
        val source = TransactionRow(
            id = 101,
            classification = Classification.CC_TRANSACTION,
            classificationOriginal = Classification.UPI_PAYMENT,
            budgetEffect = BudgetEffect.SPEND,
            inrAmount = 100.0,
            originalAmount = null,
            originalCurrency = "INR",
            exchangeRate = null,
            rateSource = null,
            merchant = "VVCMC",
            merchantKey = "vvcmc",
            category = Category.BILLS_UTILITIES,
            accountId = 3L,
            last4 = "0000",
            issuer = "Kotak",
            occurredAtMs = 1_700_000_000_000L,
            monthKey = "2026-04",
            manuallyAdjusted = true,
        )
        TransactionMapper.toDomain(TransactionMapper.toEntity(source)) shouldBe source
    }

    @Test
    fun `pending-forex TransactionRow preserves original amount and null inr`() {
        val source = TransactionRow(
            id = 1,
            classification = Classification.CC_TRANSACTION,
            classificationOriginal = null,
            budgetEffect = BudgetEffect.SPEND,
            inrAmount = null,
            originalAmount = 10.00,
            originalCurrency = "USD",
            rateSource = "pending",
            merchant = "GITHUB, INC",
            merchantKey = "github inc",
            category = Category.ENTERTAINMENT,
            accountId = null,
            last4 = "1111",
            occurredAtMs = 1_700_000_000_000L,
            monthKey = "2026-04",
        )
        TransactionMapper.toDomain(TransactionMapper.toEntity(source)) shouldBe source
    }

    @Test
    fun `every Classification survives the round trip`() {
        Classification.entries.forEach { cls ->
            val tx = TransactionRow(
                id = 1,
                classification = cls,
                classificationOriginal = null,
                budgetEffect = BudgetEffect.DROP,
                inrAmount = null,
                originalAmount = null,
                originalCurrency = "INR",
                merchant = null, merchantKey = null, category = null,
                accountId = null, last4 = null,
                occurredAtMs = 0, monthKey = "2026-01",
            )
            TransactionMapper.toDomain(
                TransactionMapper.toEntity(tx),
            ).classification shouldBe cls
        }
    }

    @Test
    fun `every Category survives the round trip`() {
        Category.entries.forEach { cat ->
            val tx = TransactionRow(
                id = 1,
                classification = Classification.CC_TRANSACTION,
                classificationOriginal = null,
                budgetEffect = BudgetEffect.SPEND,
                inrAmount = 100.0,
                originalAmount = null,
                originalCurrency = "INR",
                merchant = "x", merchantKey = "x",
                category = cat,
                accountId = null, last4 = null,
                occurredAtMs = 0, monthKey = "2026-01",
            )
            TransactionMapper.toDomain(
                TransactionMapper.toEntity(tx),
            ).category shouldBe cat
        }
    }

    // --- TransactionSourceMapper ---

    @Test
    fun `TransactionSource round-trips including primary flag`() {
        val source = TransactionSource(
            transactionId = 1,
            smsLogId = 42,
            role = SourceRole.BANK_DEBIT,
            isPrimary = true,
        )
        TransactionSourceMapper.toDomain(
            TransactionSourceMapper.toEntity(source),
        ) shouldBe source
    }

    @Test
    fun `every SourceRole survives the round trip`() {
        SourceRole.entries.forEach { role ->
            val src = TransactionSource(1, 2, role, false)
            TransactionSourceMapper.toDomain(
                TransactionSourceMapper.toEntity(src),
            ).role shouldBe role
        }
    }

    // --- BudgetMapper ---

    @Test
    fun `Budget round-trips`() {
        val source = Budget(monthKey = "2026-04", limitInr = 30_000.0, warnThresholdPct = 75)
        BudgetMapper.toDomain(
            BudgetMapper.toEntity(source, createdAtMs = 1L, updatedAtMs = 2L),
        ) shouldBe source
    }

    @Test
    fun `Budget default warnThresholdPct round-trips as 80`() {
        val source = Budget(monthKey = "2026-04", limitInr = 30_000.0)
        val round = BudgetMapper.toDomain(BudgetMapper.toEntity(source, 0L, 0L))
        round.warnThresholdPct shouldBe 80
    }

    // --- BudgetAlertStateMapper ---

    @Test
    fun `AlertFiring round-trips`() {
        val source = AlertFiring(monthKey = "2026-04", thresholdPct = 80)
        BudgetAlertStateMapper.toDomain(
            BudgetAlertStateMapper.toEntity(source, firedAtMs = 12345L),
        ) shouldBe source
    }
}
