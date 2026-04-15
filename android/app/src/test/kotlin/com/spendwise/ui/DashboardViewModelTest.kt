package com.spendwise.ui

import com.spendwise.budget.MonthSnapshot
import com.spendwise.database.dao.BudgetDao
import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.entities.BudgetEntity
import com.spendwise.database.entities.TransactionEntity
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
        accountId = null,
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
        override fun observePendingForex(): Flow<List<TransactionEntity>> =
            MutableStateFlow(pending).asStateFlow()

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
