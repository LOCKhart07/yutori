package com.spendwise.ui

import com.spendwise.budget.MonthSnapshot
import com.spendwise.database.dao.BudgetDao
import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.entities.BudgetEntity
import com.spendwise.database.entities.TransactionEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no permission yields NeedsPermission`() = runTest(dispatcher) {
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { false },
            nowMs = { epoch("2026-04-15") },
        )
        vm.uiState.first { it !is DashboardUiState.Loading } shouldBe
            DashboardUiState.NeedsPermission
    }

    @Test
    fun `empty dataset yields Empty with hasBudget=false`() = runTest(dispatcher) {
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        val state = vm.uiState.first { it !is DashboardUiState.Loading }
        state.shouldBeInstanceOf<DashboardUiState.Empty>()
        state.hasBudget shouldBe false
    }

    @Test
    fun `budget set but no transactions yields Empty with hasBudget=true`() =
        runTest(dispatcher) {
            val vm = DashboardViewModel(
                transactionDao = FakeTxDao(),
                budgetDao = FakeBudgetDao(
                    month = BudgetEntity(
                        monthKey = "2026-04",
                        limitInr = 30_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
                hasPermissionProvider = { true },
                nowMs = { epoch("2026-04-15") },
            )
            val state = vm.uiState.first { it !is DashboardUiState.Loading }
            state.shouldBeInstanceOf<DashboardUiState.Empty>()
            state.hasBudget shouldBe true
        }

    @Test
    fun `transactions with a budget yield Ready with correct snapshot`() =
        runTest(dispatcher) {
            val txs = listOf(
                txEntity(id = 1, inrAmount = 10_000.0, effect = "SPEND"),
                txEntity(id = 2, inrAmount = 2_000.0, effect = "REFUND"),
            )
            val vm = DashboardViewModel(
                transactionDao = FakeTxDao(txs),
                budgetDao = FakeBudgetDao(
                    month = BudgetEntity(
                        monthKey = "2026-04",
                        limitInr = 30_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
                hasPermissionProvider = { true },
                nowMs = { epoch("2026-04-15") },
            )

            val state = vm.uiState.first { it is DashboardUiState.Ready }
                    as DashboardUiState.Ready

            state.monthKey shouldBe "2026-04"
            state.snapshot.grossSpendInr shouldBe (10_000.0 plusOrMinus 1e-9)
            state.snapshot.refundsInr shouldBe (2_000.0 plusOrMinus 1e-9)
            state.snapshot.netSpendInr shouldBe (8_000.0 plusOrMinus 1e-9)
            state.snapshot.effectiveBudgetInr shouldBe (30_000.0 plusOrMinus 1e-9)
            state.transactionCount shouldBe 2
        }

    @Test
    fun `categories are bucketed and sorted by total desc`() = runTest(dispatcher) {
        val txs = listOf(
            txEntity(id = 1, inrAmount = 300.0, category = "FOOD_DINING"),
            txEntity(id = 2, inrAmount = 1_200.0, category = "SHOPPING"),
            txEntity(id = 3, inrAmount = 700.0, category = "FOOD_DINING"),
        )
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(txs),
            budgetDao = FakeBudgetDao(
                month = BudgetEntity(
                    monthKey = "2026-04",
                    limitInr = 30_000.0,
                    createdAtMs = 0, updatedAtMs = 0,
                ),
            ),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        val state = vm.uiState.first { it is DashboardUiState.Ready }
                as DashboardUiState.Ready
        state.byCategory.map { it.categoryName } shouldBe listOf("SHOPPING", "FOOD_DINING")
        state.byCategory.first().totalInr shouldBe 1_200.0
        state.byCategory[1].totalInr shouldBe 1_000.0
    }

    @Test
    fun `prior-month spend is folded into carry-over`() = runTest(dispatcher) {
        // March: budget ₹10,000, spent ₹6,500 net ⇒ surplus ₹3,500.
        // April: budget ₹10,000, no spend yet.
        // Expected April effective budget: 10,000 + 3,500 carry = 13,500.
        val txs = listOf(
            txEntity(id = 1, inrAmount = 4_000.0, monthKey = "2026-03"),
            txEntity(id = 2, inrAmount = 3_000.0, monthKey = "2026-03"),
            txEntity(id = 3, inrAmount = 500.0, effect = "REFUND", monthKey = "2026-03"),
            txEntity(id = 4, inrAmount = 100.0, monthKey = "2026-04"),
        )
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(txs),
            budgetDao = FakeBudgetDao(
                month = BudgetEntity(
                    monthKey = "2026-04", limitInr = 10_000.0,
                    createdAtMs = 0, updatedAtMs = 0,
                ),
                priors = listOf(
                    BudgetEntity(
                        monthKey = "2026-03", limitInr = 10_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
            ),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        val state = vm.uiState.first { it is DashboardUiState.Ready }
            as DashboardUiState.Ready
        state.snapshot.carryOverInr shouldBe 3_500.0
        state.snapshot.effectiveBudgetInr shouldBe 13_500.0
        state.snapshot.netSpendInr shouldBe 100.0
    }

    @Test
    fun `card grouping - accountId wins and unregistered last4 forms its own chip`() =
        runTest(dispatcher) {
            // Issue #6: UPI-only accounts (null last4 on tx, accountId
            // set) must form their own chip. Registered cards group by
            // accountId regardless of surface-form variance. Rows
            // without accountId but with last4 still surface (fallback
            // for unregistered cards).
            val txs = listOf(
                // Registered UPI-only account — no last4 on the tx.
                txEntity(id = 1, inrAmount = 500.0, accountId = 42L,
                    last4 = null, issuer = "Paytm"),
                txEntity(id = 2, inrAmount = 300.0, accountId = 42L,
                    last4 = null, issuer = "Paytm"),
                // Registered card — same accountId, two surface forms.
                txEntity(id = 3, inrAmount = 200.0, accountId = 7L,
                    last4 = "XX0000", issuer = "Kotak"),
                txEntity(id = 4, inrAmount = 100.0, accountId = 7L,
                    last4 = "0000", issuer = "Kotak"),
                // Unregistered card — no accountId, has last4.
                txEntity(id = 5, inrAmount = 50.0, accountId = null,
                    last4 = "XX9999", issuer = "Unknown"),
            )
            val vm = DashboardViewModel(
                transactionDao = FakeTxDao(txs),
                budgetDao = FakeBudgetDao(
                    month = BudgetEntity(
                        monthKey = "2026-04", limitInr = 30_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
                hasPermissionProvider = { true },
                nowMs = { epoch("2026-04-15") },
            )
            val state = vm.uiState.first { it is DashboardUiState.Ready }
                as DashboardUiState.Ready

            // Three chips: Paytm (UPI-only, accountId 42), Kotak
            // (card, accountId 7), Unknown (last4-only).
            state.byCard shouldHaveSize 3
            val paytm = state.byCard.single { it.accountId == 42L }
            paytm.last4 shouldBe null
            paytm.totalInr shouldBe (800.0 plusOrMinus 1e-9)
            paytm.transactionCount shouldBe 2

            val kotak = state.byCard.single { it.accountId == 7L }
            // Both surface forms collapsed under one chip.
            kotak.totalInr shouldBe (300.0 plusOrMinus 1e-9)
            kotak.transactionCount shouldBe 2

            val unknown = state.byCard.single { it.accountId == null }
            unknown.last4 shouldBe "XX9999"
            unknown.totalInr shouldBe (50.0 plusOrMinus 1e-9)
        }

    @Test
    fun `card grouping - rows without accountId and without last4 are excluded`() =
        runTest(dispatcher) {
            // Txs with neither identifier can't anchor a chip — excluding
            // them prevents an "Unknown ••null" ghost tile.
            val txs = listOf(
                txEntity(id = 1, inrAmount = 100.0, accountId = null, last4 = null),
                txEntity(id = 2, inrAmount = 200.0, accountId = 9L,
                    last4 = "XX1111", issuer = "Axis"),
            )
            val vm = DashboardViewModel(
                transactionDao = FakeTxDao(txs),
                budgetDao = FakeBudgetDao(
                    month = BudgetEntity(
                        monthKey = "2026-04", limitInr = 30_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
                hasPermissionProvider = { true },
                nowMs = { epoch("2026-04-15") },
            )
            val state = vm.uiState.first { it is DashboardUiState.Ready }
                as DashboardUiState.Ready
            state.byCard shouldHaveSize 1
            state.byCard.single().accountId shouldBe 9L
        }

    @Test
    fun `observeMonth yields a different snapshot per monthKey`() = runTest(dispatcher) {
        // #21 — each pager page collects its own per-month flow so
        // adjacent pages render their own data during a drag.
        val txs = listOf(
            txEntity(id = 1, inrAmount = 1_000.0, monthKey = "2026-03"),
            txEntity(id = 2, inrAmount = 4_000.0, monthKey = "2026-04"),
        )
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(txs),
            budgetDao = FakeBudgetDao(
                month = BudgetEntity(
                    monthKey = "2026-04", limitInr = 30_000.0,
                    createdAtMs = 0, updatedAtMs = 0,
                ),
                priors = listOf(
                    BudgetEntity(
                        monthKey = "2026-03", limitInr = 30_000.0,
                        createdAtMs = 0, updatedAtMs = 0,
                    ),
                ),
            ),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        val march = vm.observeMonth("2026-03")
            .first { it is DashboardUiState.Ready } as DashboardUiState.Ready
        val april = vm.observeMonth("2026-04")
            .first { it is DashboardUiState.Ready } as DashboardUiState.Ready
        march.snapshot.grossSpendInr shouldBe (1_000.0 plusOrMinus 1e-9)
        april.snapshot.grossSpendInr shouldBe (4_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `observeMonth honours permission gate`() = runTest(dispatcher) {
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { false },
            nowMs = { epoch("2026-04-15") },
        )
        vm.observeMonth("2026-03").first() shouldBe DashboardUiState.NeedsPermission
    }

    @Test
    fun `setMonth updates viewedMonthKey without a delta computation`() = runTest(dispatcher) {
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        vm.viewedMonthKey.value shouldBe "2026-04"
        vm.setMonth("2025-11")
        vm.viewedMonthKey.value shouldBe "2025-11"
    }

    @Test
    fun `earliestMonthKey falls back to current when table empty`() = runTest(dispatcher) {
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        // Loading-initial is current; once the Flow emits null (empty
        // table) the fallback becomes currentMonthKey too.
        vm.earliestMonthKey.first { true } shouldBe "2026-04"
    }

    @Test
    fun `earliestMonthKey reflects the min month_key present`() = runTest(dispatcher) {
        val txs = listOf(
            txEntity(id = 1, inrAmount = 500.0, monthKey = "2025-09"),
            txEntity(id = 2, inrAmount = 400.0, monthKey = "2026-04"),
            txEntity(id = 3, inrAmount = 300.0, monthKey = "2026-02"),
        )
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(txs),
            budgetDao = FakeBudgetDao(),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        vm.earliestMonthKey.first { it != "2026-04" } shouldBe "2025-09"
    }

    @Test
    fun `pending forex count is exposed`() = runTest(dispatcher) {
        val txs = listOf(
            txEntity(id = 1, inrAmount = 500.0),
        )
        val pending = listOf(
            txEntity(id = 2, inrAmount = null, rateSource = "pending"),
        )
        val vm = DashboardViewModel(
            transactionDao = FakeTxDao(txs, pending),
            budgetDao = FakeBudgetDao(
                month = BudgetEntity(
                    monthKey = "2026-04",
                    limitInr = 30_000.0,
                    createdAtMs = 0, updatedAtMs = 0,
                ),
            ),
            hasPermissionProvider = { true },
            nowMs = { epoch("2026-04-15") },
        )
        val state = vm.uiState.first { it is DashboardUiState.Ready }
                as DashboardUiState.Ready
        state.pendingForexCount shouldBe 1
    }

    // ---- helpers ----

    private fun epoch(ymd: String): Long {
        // Parse "YYYY-MM-DD" at IST midnight.
        val (y, m, d) = ymd.split("-").map { it.toInt() }
        val zdt = java.time.ZonedDateTime.of(
            y, m, d, 0, 0, 0, 0,
            java.time.ZoneId.systemDefault(),
        )
        return zdt.toInstant().toEpochMilli()
    }

    private fun txEntity(
        id: Long,
        inrAmount: Double? = 100.0,
        effect: String = "SPEND",
        category: String? = "FOOD_DINING",
        last4: String? = "3333",
        issuer: String? = "Kotak",
        monthKey: String = "2026-04",
        rateSource: String? = null,
        accountId: Long? = null,
    ) = TransactionEntity(
        id = id,
        classification = "UPI_PAYMENT",
        classificationOriginal = null,
        budgetEffect = effect,
        inrAmount = inrAmount,
        originalAmount = null,
        originalCurrency = "INR",
        rateSource = rateSource,
        merchant = "x",
        merchantKey = "x",
        category = category,
        accountId = accountId,
        last4 = last4,
        issuer = issuer,
        occurredAtMs = 1_700_000_000_000L,
        monthKey = monthKey,
    )

    // --- minimal DAO fakes ---
    class FakeTxDao(
        private val all: List<TransactionEntity> = emptyList(),
        private val pending: List<TransactionEntity> = emptyList(),
    ) : TransactionDao {
        override fun observeByMonth(monthKey: String): Flow<List<TransactionEntity>> =
            MutableStateFlow(all.filter { it.monthKey == monthKey }).asStateFlow()
        override suspend fun getBeforeMonth(monthKey: String): List<TransactionEntity> =
            all.filter { it.monthKey < monthKey && it.budgetEffect in setOf("SPEND", "REFUND") }
        override fun observePendingForex(): Flow<List<TransactionEntity>> =
            MutableStateFlow(pending).asStateFlow()
        override fun observeEarliestMonthKey(): Flow<String?> =
            MutableStateFlow(all.minOfOrNull { it.monthKey }).asStateFlow()

        // Unused in tests — nothing calls these paths through the ViewModel.
        override suspend fun insert(row: TransactionEntity) = 0L
        override suspend fun update(row: TransactionEntity) = Unit
        override suspend fun delete(row: TransactionEntity) = Unit
        override suspend fun getById(id: Long) = null
        override fun observeByMonthAndAccount(monthKey: String, accountId: Long) =
            MutableStateFlow(emptyList<TransactionEntity>()).asStateFlow()
        override fun observeByMonthAndCategory(monthKey: String, category: String) =
            MutableStateFlow(emptyList<TransactionEntity>()).asStateFlow()
        override suspend fun findDedupCandidates(
            effect: String, inrAmount: Double, tolerance: Double,
            occurredAtMs: Long, windowMs: Long,
        ) = emptyList<TransactionEntity>()
        override suspend fun sumSpendForMonth(monthKey: String) = 0.0
        override suspend fun sumRefundsForMonth(monthKey: String) = 0.0
        override suspend fun findBySelfTransferCandidateMerchant(merchant: String) =
            emptyList<TransactionEntity>()
    }

    class FakeBudgetDao(
        private val month: BudgetEntity? = null,
        private val priors: List<BudgetEntity> = emptyList(),
    ) : BudgetDao {
        override fun observeByMonth(monthKey: String): Flow<BudgetEntity?> =
            MutableStateFlow(month?.takeIf { it.monthKey == monthKey }).asStateFlow()
        override suspend fun getByMonth(monthKey: String) =
            month?.takeIf { it.monthKey == monthKey }
        override suspend fun getAllBefore(monthKey: String) =
            priors.filter { it.monthKey < monthKey }
        override suspend fun upsert(row: BudgetEntity) = Unit
        override suspend fun update(row: BudgetEntity) = Unit
        override suspend fun delete(row: BudgetEntity) = Unit
        override suspend fun getAll() = priors + listOfNotNull(month)
    }
}
