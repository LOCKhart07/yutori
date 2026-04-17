package com.yutori.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.flow.Flow

/**
 * Recipient rules list. v2-styled: status-bar inset, caps section
 * heads, card-wrapped rule rows, mono pattern text, themed switch.
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
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = YutoriTheme.colors

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
            Text(
                text = "Recipient rules",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Reclassify transactions based on who you're paying. " +
                    "Payments to CRED, for example, aren't spend — they're " +
                    "credit-card bill payments.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )
            Spacer(Modifier.height(20.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (seed.isNotEmpty()) {
                    item(key = "seed-header") {
                        CapsHeader("BUILT-IN (${seed.size})")
                    }
                    items(seed, key = { "s-${it.id}" }) { rule ->
                        RuleCard(rule, onToggle = onToggleEnabled, onDelete = null)
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                item(key = "user-header") {
                    CapsHeader(
                        if (user.isEmpty()) "YOUR RULES" else "YOUR RULES (${user.size})",
                    )
                }
                if (user.isEmpty()) {
                    item(key = "user-empty") {
                        Text(
                            text = "User rules will appear here. UPI handles " +
                                "linked to an account (in My accounts) become " +
                                "SELF_TRANSFER rules automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onMuted,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                } else {
                    items(user, key = { "u-${it.id}" }) { rule ->
                        RuleCard(
                            rule,
                            onToggle = onToggleEnabled,
                            onDelete = { onDeleteUserRule(rule) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsHeader(text: String) {
    Text(
        text = text,
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onFaint,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun RuleCard(
    rule: RecipientRuleEntity,
    onToggle: (RecipientRuleEntity, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rule.pattern,
                    style = YutoriTextStyles.Mono,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle(rule, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = colors.onMuted,
                        uncheckedTrackColor = colors.surfaceElevated2,
                        uncheckedBorderColor = colors.divider,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${rule.patternKind} · → ${rule.reclassifyAs}" +
                    (rule.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onMuted,
            )
            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Delete",
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.negative,
                )
            }
        }
    }
}
