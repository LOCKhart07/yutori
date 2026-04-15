package com.spendwise.ui

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spendwise.ui.theme.SpendWiseTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.database.entities.RecipientRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Recipient rules list per settings-spec §3.4. v1 MVP supports
 * enable/disable for all rules and delete for user-source rules.
 * Adding new rules via a full editor is v1.1.
 */
@Composable
fun RecipientRulesScreen(
    rulesFlow: Flow<List<RecipientRuleEntity>>,
    onBack: () -> Unit,
    onToggleEnabled: (RecipientRuleEntity, Boolean) -> Unit,
    onDeleteUserRule: (RecipientRuleEntity) -> Unit,
) {
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val seed = rules.filter { it.source == "SEED" }
    val user = rules.filter { it.source == "USER" }

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

            Text("Recipient rules", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Rules reclassify transactions based on who you're paying. " +
                    "For example, payments to CRED aren't spend — they're bill " +
                    "payments for your credit card.",
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.onMuted,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (seed.isNotEmpty()) {
                    item(key = "seed-header") {
                        Text(
                            "Built-in",
                            style = MaterialTheme.typography.titleSmall,
                            color = SpendWiseTheme.colors.onMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(seed, key = { "s-${it.id}" }) { rule ->
                        RuleRow(rule, onToggle = onToggleEnabled, onDelete = null)
                        HorizontalDivider(color = SpendWiseTheme.colors.divider)
                    }
                }

                item(key = "user-header") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your rules",
                        style = MaterialTheme.typography.titleSmall,
                        color = SpendWiseTheme.colors.onMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (user.isEmpty()) {
                    item(key = "user-empty") {
                        Text(
                            "User-added rules will appear here. For now, UPI " +
                                "handles linked to an account (added in My " +
                                "accounts) become SELF_TRANSFER rules automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendWiseTheme.colors.onMuted,
                        )
                    }
                } else {
                    items(user, key = { "u-${it.id}" }) { rule ->
                        RuleRow(
                            rule,
                            onToggle = onToggleEnabled,
                            onDelete = { onDeleteUserRule(rule) },
                        )
                        HorizontalDivider(color = SpendWiseTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: RecipientRuleEntity,
    onToggle: (RecipientRuleEntity, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "${rule.patternKind} · → ${rule.reclassifyAs}" +
                    (rule.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = SpendWiseTheme.colors.onMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle(rule, it) },
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
