package com.spendwise.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.database.entities.AccountEntity
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme
import kotlinx.coroutines.flow.Flow

/**
 * Accounts list. Confirmed rows show flat; SUGGESTED rows land in a
 * "Suggested" section above with Add / Ignore actions (auto-detect).
 */
@Composable
fun AccountsScreen(
    accountsFlow: Flow<List<AccountEntity>>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onConfirmSuggestion: (Long) -> Unit,
    onIgnoreSuggestion: (Long) -> Unit,
) {
    val accounts by accountsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggested = accounts.filter { it.status == "SUGGESTED" }
    val confirmed = accounts.filter { it.status == "CONFIRMED" }
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding() + 8.dp)
                .padding(horizontal = 24.dp),
        ) {
            BackRow(label = "Settings", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            Text("My accounts", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(20.dp))

            if (accounts.isEmpty()) {
                Text(
                    "Register your own accounts here so we can tell when " +
                        "you're moving money between them, not spending it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpendWiseTheme.colors.onMuted,
                )
                Spacer(Modifier.height(16.dp))
                AddAccountLink(onAdd)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (suggested.isNotEmpty()) {
                        item {
                            SectionCaps(
                                "SUGGESTED (${suggested.size})",
                                subtitle = "Detected from your SMS inbox. Add to " +
                                    "route future transactions automatically.",
                            )
                        }
                        items(items = suggested, key = { it.id }) { acc ->
                            SuggestionRow(
                                entity = acc,
                                onAdd = { onConfirmSuggestion(acc.id) },
                                onIgnore = { onIgnoreSuggestion(acc.id) },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "CONFIRMED" + if (confirmed.isNotEmpty()) " (${confirmed.size})" else "",
                                style = SpendWiseTextStyles.Caps,
                                color = SpendWiseTheme.colors.onFaint,
                            )
                            AddAccountLink(onAdd)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    items(items = confirmed, key = { it.id }) { acc ->
                        AccountRow(acc, onClick = { onEdit(acc.id) })
                        HorizontalDivider(color = SpendWiseTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAccountLink(onAdd: () -> Unit) {
    Text(
        text = "+ Add account",
        modifier = Modifier
            .clickable(onClick = onAdd)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionCaps(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = SpendWiseTextStyles.Caps,
        color = SpendWiseTheme.colors.onFaint,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SpendWiseTheme.colors.onMuted,
        )
    }
    Spacer(Modifier.height(8.dp))
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
                    ?: accountTitle(entity),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                accountTitle(entity) + " · " +
                    prettyKind(entity.kind) +
                    if (entity.isDefaultSpend) " · default spend" else "",
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.onMuted,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    entity: AccountEntity,
    onAdd: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = SpendWiseTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = accountTitle(entity),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = prettyKind(entity.kind) + " · seen ${entity.seenCount}×",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add",
                    modifier = Modifier
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Ignore",
                    modifier = Modifier
                        .clickable(onClick = onIgnore)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onMuted,
                )
            }
        }
    }
}

/**
 * Human-readable one-line identity for an account. Card-bearing rows
 * show issuer + masked last-4; UPI-only rows (null last4, issue #6)
 * show just the issuer.
 */
private fun accountTitle(entity: AccountEntity): String =
    if (entity.last4 != null) "${entity.issuer} \u2022\u2022${entity.last4}"
    else entity.issuer

private fun prettyKind(kind: String): String = when (kind) {
    "SAVINGS" -> "Savings"
    "CREDIT_CARD" -> "Credit card"
    "INVESTMENT" -> "Investment"
    "OTHER" -> "Other"
    else -> kind
}
