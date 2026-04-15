package com.spendwise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.database.entities.AccountEntity
import com.spendwise.ui.theme.SpendWiseTheme
import kotlinx.coroutines.flow.Flow

/**
 * Accounts list per settings-spec §2.4. Tapping a row opens the edit
 * form; the "+" button creates a new account.
 */
@Composable
fun AccountsScreen(
    accountsFlow: Flow<List<AccountEntity>>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val accounts by accountsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("← Back") }

            Text("My accounts", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            if (accounts.isEmpty()) {
                Text(
                    "Register your own accounts here so we can tell when " +
                        "you're moving money between them, not spending it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpendWiseTheme.colors.onMuted,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd) { Text("Add your first account") }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onAdd) { Text("+ Add account") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = accounts, key = { it.id }) { acc ->
                        AccountRow(acc, onClick = { onEdit(acc.id) })
                        HorizontalDivider(color = SpendWiseTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(entity: AccountEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                entity.displayName?.takeIf { it.isNotBlank() }
                    ?: "${entity.issuer} ••${entity.last4}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${entity.issuer} ••${entity.last4} · " +
                    prettyKind(entity.kind) +
                    if (entity.isDefaultSpend) " · default spend" else "",
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.onMuted,
            )
        }
    }
}

private fun prettyKind(kind: String): String = when (kind) {
    "SAVINGS" -> "Savings"
    "CREDIT_CARD" -> "Credit card"
    "INVESTMENT" -> "Investment"
    "OTHER" -> "Other"
    else -> kind
}
