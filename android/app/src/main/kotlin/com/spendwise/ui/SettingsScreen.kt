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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme

/**
 * Settings hub — v2 per mockups/v2.html frame 7. Grouped sections
 * with section-head caps, description-below-title list rows,
 * chevron on the right.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onRecipientRules: () -> Unit,
    accountSuggestionCount: Int = 0,
) {
    val backup = rememberBackupActions()
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = statusInset.calculateTopPadding() + 8.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                BackRow(label = "Dashboard", onBack = onBack)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(20.dp))
            }

            SettingsSection(title = "Data & accounts") {
                SettingsItem(
                    title = "My accounts",
                    subtitle = if (accountSuggestionCount > 0) {
                        "$accountSuggestionCount new suggestion(s) from recent SMSes."
                    } else {
                        "Banks and cards — register UPI handles so self-" +
                            "transfers don't count as spend."
                    },
                    onClick = onAccounts,
                    badge = accountSuggestionCount.takeIf { it > 0 },
                )
                SettingsItem(
                    title = "Recipient rules",
                    subtitle = "Reclassification rules for CC bill payments " +
                        "and self-transfers.",
                    onClick = onRecipientRules,
                )
            }

            SettingsSection(title = "Import / export") {
                SettingsItem(
                    title = "Export settings",
                    subtitle = "Save accounts and recipient rules to a " +
                        "JSON file. Transactions aren't included.",
                    onClick = backup.onExportClick,
                )
                SettingsItem(
                    title = "Import settings",
                    subtitle = "Restore accounts and recipient rules from a " +
                        "previously-exported file. Duplicates are skipped.",
                    onClick = backup.onImportClick,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = SpendWiseTextStyles.Caps,
            color = SpendWiseTheme.colors.onFaint,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                if (badge != null) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = badge.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.onMuted,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = SpendWiseTheme.colors.onFaint,
        )
    }
    HorizontalDivider(
        color = SpendWiseTheme.colors.divider,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
