package com.spendwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.database.entities.TransactionEntity
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/**
 * Category drill-down — v2. Tinted left border + colored category
 * header, mono totals, rows match the Copilot-inspired TransactionListItem.
 * See mockups/v2.html frame 4.
 */
@Composable
fun CategoryDrillDownScreen(
    monthKey: String,
    category: String,
    transactionsFlow: Flow<List<TransactionEntity>>,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val transactions by transactionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val catTint = SpendWiseTheme.colors.forCategory(category)
    var txSort: TxSort by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<TxSort>(TxSort.DateDesc)
    }
    val sortedTxs = remember(transactions, txSort) { applyTxSort(transactions, txSort) }

    val totalSpend = transactions
        .filter { it.budgetEffect == "SPEND" && it.inrAmount != null }
        .sumOf { it.inrAmount!! }
    val refunds = transactions
        .filter { it.budgetEffect == "REFUND" && it.inrAmount != null }
        .sumOf { it.inrAmount!! }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed header
            Column(
                modifier = Modifier
                    .padding(top = statusInset.calculateTopPadding() + 8.dp)
                    .padding(horizontal = 24.dp),
            ) {
                BackRow(label = "Dashboard", onBack = onBack)
                Spacer(Modifier.height(16.dp))

                // Tinted category header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Vertical color bar
                    Spacer(
                        modifier = Modifier
                            .width(3.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(catTint),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "${prettyCategory(category)} · ${prettyMonth(monthKey)}".uppercase(),
                            style = SpendWiseTextStyles.Caps,
                            color = catTint,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = inr.formatCompact(totalSpend),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${transactions.size} transaction(s)" +
                                if (refunds > 0) " · refunds ${inr.format(refunds)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendWiseTheme.colors.onMuted,
                        )
                    }
                }

                infoBoxForCategory(category)?.let { infoText ->
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = SpendWiseTheme.colors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = SpendWiseTheme.colors.divider,
                        ),
                    ) {
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendWiseTheme.colors.onMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "TRANSACTIONS",
                        style = SpendWiseTextStyles.Caps,
                        color = SpendWiseTheme.colors.onFaint,
                    )
                    if (transactions.isNotEmpty()) {
                        Text(
                            text = txSort.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = SpendWiseTheme.colors.onMuted,
                            modifier = Modifier
                                .clickable { txSort = txSort.next() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (transactions.isEmpty()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "No transactions in this category this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpendWiseTheme.colors.onMuted,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = sortedTxs,
                        key = { _, tx -> tx.id },
                    ) { index, tx ->
                        TransactionListItem(
                            entity = tx,
                            inr = inr,
                            onClick = { onTransactionClick(tx.id) },
                        )
                        if (index < sortedTxs.lastIndex) {
                            HorizontalDivider(
                                color = SpendWiseTheme.colors.divider,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineSmall,
            color = SpendWiseTheme.colors.onMuted,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = SpendWiseTheme.colors.onMuted,
        )
    }
}

private fun prettyMonth(monthKey: String): String = try {
    val (y, m) = monthKey.split("-").let { it[0] to it[1].toInt() }
    val name = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[m - 1]
    "$name $y"
} catch (_: Exception) { monthKey }

// ── Shared formatter used by the list item too ──
internal fun NumberFormat.formatCompact(value: Double): String {
    val rounded = kotlin.math.round(value)
    return if (kotlin.math.abs(value - rounded) < 0.005) {
        format(rounded).replace(Regex("\\.\\d{2}$"), "")
    } else {
        format(value)
    }
}

/** Cycle: latest first → oldest first → biggest first → smallest first → back. */
internal enum class TxSort(val label: String) {
    DateDesc("Latest"),
    DateAsc("Oldest"),
    AmountDesc("Amount ↓"),
    AmountAsc("Amount ↑");

    fun next(): TxSort = when (this) {
        DateDesc   -> DateAsc
        DateAsc    -> AmountDesc
        AmountDesc -> AmountAsc
        AmountAsc  -> DateDesc
    }
}

internal fun applyTxSort(
    txs: List<TransactionEntity>,
    sort: TxSort,
): List<TransactionEntity> = when (sort) {
    TxSort.DateDesc   -> txs.sortedByDescending { it.occurredAtMs }
    TxSort.DateAsc    -> txs.sortedBy { it.occurredAtMs }
    TxSort.AmountDesc -> txs.sortedByDescending { it.inrAmount ?: 0.0 }
    TxSort.AmountAsc  -> txs.sortedBy { it.inrAmount ?: 0.0 }
}

private fun infoBoxForCategory(category: String): String? = when (category) {
    "CASH" ->
        "Cash withdrawn is counted at time of withdrawal. Smaller cash " +
            "spends after that are not tracked."
    "UNCATEGORIZED" ->
        "These merchants (Amazon, Blinkit, etc.) are cross-cutting " +
            "platforms and can't be categorized from the SMS alone."
    "OTHER" ->
        "Miscellaneous merchants that don't fit a specific category."
    else -> null
}
