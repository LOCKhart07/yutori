package com.yutori.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.database.entities.TransactionEntity
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Composable
fun CardDrillDownScreen(
    monthKey: String,
    last4: String?,
    issuerLabel: String?,
    transactionsFlow: Flow<List<TransactionEntity>>,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val transactions by transactionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = YutoriTheme.colors

    val spend         = transactions.filter { it.budgetEffect == "SPEND" }
    val refunds       = transactions.filter { it.budgetEffect == "REFUND" }
    val billPayments  = transactions.filter { it.classification == "CC_BILL_PAYMENT" }
    val selfTransfers = transactions.filter { it.classification == "SELF_TRANSFER" }
    val other = transactions.filter { t ->
        t !in spend && t !in refunds && t !in billPayments && t !in selfTransfers
    }

    val spendTotal = spend.mapNotNull { it.inrAmount }.sum()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(top = statusInset.calculateTopPadding() + 8.dp)
                    .padding(horizontal = 24.dp),
            ) {
                BackRow(label = "Dashboard", onBack = onBack)
                Spacer(Modifier.height(16.dp))

                Text(
                    text = buildString {
                        append((issuerLabel ?: "ACCOUNT").uppercase())
                        // UPI-only accounts (issue #6) have no last-4;
                        // skip the "\u2022\u2022..." segment entirely.
                        if (last4 != null) append(" \u2022\u2022").append(last4)
                        append(" \u00B7 ").append(prettyMonthShort(monthKey))
                    },
                    style = YutoriTextStyles.Caps,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (spendTotal > 0) inr.formatAmount(spendTotal, compact = true) else "₹0",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${transactions.size} transaction(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )

                Spacer(Modifier.height(20.dp))
            }

            if (transactions.isEmpty()) {
                Text(
                    "No transactions on this card this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (spend.isNotEmpty()) sectionBlock("Spend", spend, inr, onTransactionClick)
                    if (refunds.isNotEmpty()) sectionBlock("Refunds", refunds, inr, onTransactionClick)
                    if (billPayments.isNotEmpty()) sectionBlock("Bill payments", billPayments, inr, onTransactionClick)
                    if (selfTransfers.isNotEmpty()) sectionBlock("Self-transfers", selfTransfers, inr, onTransactionClick)
                    if (other.isNotEmpty()) sectionBlock("Other", other, inr, onTransactionClick)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionBlock(
    label: String,
    rows: List<TransactionEntity>,
    inr: NumberFormat,
    onTransactionClick: (Long) -> Unit,
) {
    item(key = "header-$label") {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label.uppercase(),
                style = YutoriTextStyles.Caps,
                color = YutoriTheme.colors.onFaint,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    items(rows, key = { "row-${it.id}" }) { tx ->
        TransactionListItem(
            entity = tx,
            inr = inr,
            onClick = { onTransactionClick(tx.id) },
        )
        HorizontalDivider(
            color = YutoriTheme.colors.divider,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun prettyMonthShort(monthKey: String): String = try {
    val (y, m) = monthKey.split("-").let { it[0] to it[1].toInt() }
    val name = listOf(
        "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec",
    )[m - 1]
    "$name $y"
} catch (_: Exception) { monthKey }
