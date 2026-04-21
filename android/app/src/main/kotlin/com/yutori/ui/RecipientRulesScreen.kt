package com.yutori.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yutori.ai.AiSettingsRepository
import com.yutori.ai.RulePrefill
import com.yutori.ai.RuleExtractor
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A tx shown in the review sheet's match preview. Kept minimal — the sheet
 * only needs what the spec's mockup renders (date, amount, current class).
 */
data class TxMatchRow(
    val id: Long,
    val merchant: String,
    val occurredAtMs: Long,
    val inrAmount: Double?,
    val classification: String,
)

@Composable
fun RecipientRulesScreen(
    rulesFlow: Flow<List<RecipientRuleEntity>>,
    suggestionsFlow: Flow<List<RuleSuggestionEntity>>,
    scanningFlow: StateFlow<Boolean>,
    onBack: () -> Unit,
    onToggleEnabled: (RecipientRuleEntity, Boolean) -> Unit,
    onDeleteUserRule: (RecipientRuleEntity) -> Unit,
    onAcceptSuggestion: (RuleSuggestionEntity) -> Unit,
    onDismissSuggestion: (Long) -> Unit,
    onRescan: () -> Unit,
    loadMatches: suspend (String) -> List<TxMatchRow>,
    onAddNewRule: () -> Unit,
    onEditRule: (RecipientRuleEntity) -> Unit,
    // AI-assisted rules (#64 part 2). The describe-this-rule sheet
    // lives inside this screen per plans/ai-rules-spec.md + 1a
    // decision. Nulls short-circuit the feature for callers (tests,
    // previews) that don't wire it.
    aiState: StateFlow<AiSettingsRepository.State>? = null,
    ruleExtractor: RuleExtractor? = null,
    onOpenAiRuleEdit: (RulePrefill) -> Unit = {},
    onGoToAiSettings: () -> Unit = {},
) {
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by suggestionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scanning by scanningFlow.collectAsStateWithLifecycle()

    val currentAiState = aiState?.collectAsStateWithLifecycle()?.value
    val aiReady = currentAiState?.enabled == true && currentAiState.modelInstalled
    var describeSheetSeed by remember { mutableStateOf<String?>(null) }
    var aiUnavailableSheetVisible by remember { mutableStateOf(false) }

    val onDescribeFromUnsure: ((String) -> Unit)? = if (ruleExtractor != null) {
        { merchantKey ->
            if (aiReady) {
                describeSheetSeed = "anything from $merchantKey is…"
            } else {
                aiUnavailableSheetVisible = true
            }
        }
    } else {
        null
    }
    val seed = rules.filter { it.source == "SEED" }
    val user = rules.filter {
        it.source == "USER" || it.source == "LEARNED" || it.source == "AI"
    }
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = YutoriTheme.colors

    var reviewTarget by remember { mutableStateOf<RuleSuggestionEntity?>(null) }

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
                // Always render so Rescan is reachable on fresh installs.
                item(key = "suggestions-header") {
                    SuggestedHeader(
                        count = suggestions.size,
                        scanning = scanning,
                        onRescan = onRescan,
                    )
                }
                if (suggestions.isEmpty()) {
                    item(key = "suggestions-empty") {
                        Text(
                            text = "No repeat merchants crossed the threshold yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onMuted,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                } else {
                    items(suggestions, key = { "sg-${it.id}" }) { sg ->
                        SuggestionCard(
                            suggestion = sg,
                            onAccept = { onAcceptSuggestion(sg) },
                            onReview = { reviewTarget = sg },
                            onDismiss = { onDismissSuggestion(sg.id) },
                            onDescribeThisRule = onDescribeFromUnsure,
                        )
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }

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
                if (ruleExtractor != null) {
                    item(key = "describe-a-rule") {
                        DescribeRuleEntry(onClick = {
                            if (aiReady) {
                                describeSheetSeed = ""
                            } else {
                                aiUnavailableSheetVisible = true
                            }
                        })
                    }
                }
                item(key = "add-new-rule") {
                    AddRuleEntry(onClick = onAddNewRule)
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
                            onDelete = if (
                                rule.source == "USER" ||
                                rule.source == "LEARNED" ||
                                rule.source == "AI"
                            ) {
                                { onDeleteUserRule(rule) }
                            } else null,
                            onClick = { onEditRule(rule) },
                        )
                    }
                }
            }
        }
    }

    reviewTarget?.let { target ->
        SuggestionReviewSheet(
            suggestion = target,
            loadMatches = loadMatches,
            onDismiss = { reviewTarget = null },
            onAccept = {
                onAcceptSuggestion(target)
                reviewTarget = null
            },
        )
    }

    // AI "Describe this rule" sheet. ruleExtractor-nullability guards
    // callers that don't opt into the feature from ever creating a VM.
    if (ruleExtractor != null) {
        describeSheetSeed?.let { seed ->
            val vm: DescribeRuleViewModel = viewModel(
                key = "describe-rule",
                factory = DescribeRuleViewModel.Factory(ruleExtractor),
            )
            DescribeRuleSheet(
                viewModel = vm,
                seedText = seed,
                onDismiss = { describeSheetSeed = null },
                onExtracted = { prefill ->
                    describeSheetSeed = null
                    onOpenAiRuleEdit(prefill)
                },
            )
        }
    }

    if (aiUnavailableSheetVisible) {
        AiUnavailableSheet(
            onDismiss = { aiUnavailableSheetVisible = false },
            onGoToSettings = {
                aiUnavailableSheetVisible = false
                onGoToAiSettings()
            },
        )
    }
}

/**
 * Shown when the user taps "Describe this rule…" while the AI feature
 * is off or the model isn't downloaded. Single sheet for both cases —
 * landing on Settings surfaces the right next step either way (toggle
 * on → opt-in + download; toggle on but no file → Download button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiUnavailableSheet(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "AI-assisted rules isn't on yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Turn it on in Settings to describe rules in plain English. " +
                    "You'll be asked to download a one-time 2.58 GB model.",
                style = MaterialTheme.typography.bodyMedium,
                color = YutoriTheme.colors.onMuted,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Not now") }
                androidx.compose.material3.Button(
                    onClick = onGoToSettings,
                    modifier = Modifier.weight(1f),
                ) { Text("Go to Settings") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SuggestedHeader(count: Int, scanning: Boolean, onRescan: () -> Unit) {
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (count > 0) "SUGGESTED · $count NEW" else "SUGGESTED",
            style = YutoriTextStyles.Caps,
            color = colors.onFaint,
        )
        Row(
            modifier = Modifier
                .clickable(enabled = !scanning, onClick = onRescan)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconColor = if (scanning) MaterialTheme.colorScheme.primary else colors.onMuted
            SpinningIcon(scanning = scanning, tint = iconColor)
            Spacer(Modifier.padding(horizontal = 3.dp))
            Text(
                text = "Rescan",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = iconColor,
            )
        }
    }
}

@Composable
private fun SpinningIcon(scanning: Boolean, tint: Color) {
    val angle: Float = if (scanning) {
        val transition = rememberInfiniteTransition(label = "rescan-spin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rescan-angle",
        )
        value
    } else 0f
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .height(14.dp)
            .rotate(angle),
    )
}

@Composable
private fun SuggestionCard(
    suggestion: RuleSuggestionEntity,
    onAccept: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onDescribeThisRule: ((String) -> Unit)? = null,
) {
    val colors = YutoriTheme.colors
    val confident = suggestion.inferredClassification != null
    val accent = if (confident) MaterialTheme.colorScheme.primary else colors.onMuted

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = suggestion.pattern,
                    style = YutoriTextStyles.Mono,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${suggestion.matchCount} txs · ₹${formatInr(suggestion.totalInr)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            val targetLabel = suggestion.inferredClassification?.let { c ->
                "Reclassify as  →  ${c.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}"
            } ?: "No default — pick a type"
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionActionButton(
                    label = if (confident) "Add rule" else "Pick type…",
                    onClick = onAccept,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                SuggestionActionButton(
                    label = "Review",
                    onClick = onReview,
                    primary = false,
                )
                SuggestionActionButton(
                    label = "Dismiss",
                    onClick = onDismiss,
                    primary = false,
                )
            }

            // Describe-this-rule surface only appears on unsure cards —
            // confident ones already have a concrete reclassify target
            // so the AI wouldn't add anything.
            if (!confident && onDescribeThisRule != null) {
                Spacer(Modifier.height(6.dp))
                SuggestionActionButton(
                    label = "✦ Describe this rule…",
                    onClick = { onDescribeThisRule(suggestion.merchantKey) },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SuggestionActionButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YutoriTheme.colors
    val bg = when {
        primary && enabled -> MaterialTheme.colorScheme.primary
        primary -> colors.surfaceElevated2
        else -> Color.Transparent
    }
    val fg = when {
        primary && enabled -> MaterialTheme.colorScheme.onPrimary
        primary -> colors.onMuted
        else -> colors.onMuted
    }
    Surface(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = if (!primary) BorderStroke(1.dp, colors.divider) else null,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = fg,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionReviewSheet(
    suggestion: RuleSuggestionEntity,
    loadMatches: suspend (String) -> List<TxMatchRow>,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    val colors = YutoriTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var matches by remember { mutableStateOf<List<TxMatchRow>?>(null) }

    LaunchedEffect(suggestion.id) {
        matches = loadMatches(suggestion.merchantKey)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Review suggestion",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            val whyText = reasonCodeLabel(suggestion.reasonCode)
            Text(
                text = "Accepting this rule would cover ${suggestion.matchCount} past " +
                    "transactions going forward.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
            Spacer(Modifier.height(14.dp))

            MetaGrid(
                rows = listOf(
                    "Pattern" to suggestion.pattern,
                    "Kind" to suggestion.patternKind,
                    "Reclassify as" to (suggestion.inferredClassification ?: "— user picks —"),
                    "Why" to whyText,
                ),
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "PAST MATCHES",
                style = YutoriTextStyles.Caps,
                color = colors.onFaint,
            )
            Spacer(Modifier.height(6.dp))

            when (val m = matches) {
                null -> Text(
                    text = "Loading matches…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )
                else -> MatchesList(m)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SuggestionActionButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
                SuggestionActionButton(
                    label = if (suggestion.inferredClassification != null) "Add rule" else "Pick type…",
                    onClick = onAccept,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetaGrid(rows: List<Pair<String, String>>) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceElevated2,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            rows.forEachIndexed { index, (k, v) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onFaint,
                        modifier = Modifier.weight(0.35f),
                    )
                    Text(
                        text = v,
                        style = YutoriTextStyles.Mono,
                        modifier = Modifier.weight(0.65f),
                    )
                }
                if (index < rows.size - 1) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MatchesList(matches: List<TxMatchRow>) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceElevated2,
    ) {
        LazyColumn {
            items(matches, key = { it.id }) { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.merchant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "${shortDate(tx.occurredAtMs)} · ${tx.classification}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onMuted,
                        )
                    }
                    Text(
                        text = tx.inrAmount?.let { "₹${formatInr(it)}" } ?: "—",
                        style = YutoriTextStyles.Mono,
                    )
                }
            }
            if (matches.isEmpty()) {
                item {
                    Text(
                        text = "No past matches found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onMuted,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun reasonCodeLabel(code: String): String = when (code) {
    "OWN_HANDLE_SHAPE" -> "Matches a registered own-handle's local-part"
    "KEYWORD_MIDDLEMAN" -> "Matches a known CC-bill middleman keyword"
    "REPEAT_NO_DEFAULT" -> "Repeat merchant — no automatic default"
    else -> code
}

private fun formatInr(amount: Double): String {
    val rounded = kotlin.math.abs(amount).toLong()
    return "%,d".format(rounded)
}

private fun shortDate(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ms))
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
private fun AddRuleEntry(onClick: () -> Unit) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = "Add a new rule",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )
        }
    }
}

@Composable
private fun DescribeRuleEntry(onClick: () -> Unit) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "\u2726",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.info,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = "Describe a rule in plain English",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: RecipientRuleEntity,
    onToggle: (RecipientRuleEntity, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onClick: (() -> Unit)? = null,
) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
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
            val targetText = rule.reclassifyAs?.let { "→ $it" } ?: "tag only"
            Text(
                text = "${rule.patternKind} · $targetText" +
                    (rule.assignedCategory?.let { " · ${prettyCategory(it)}" } ?: "") +
                    (rule.note?.let { " · $it" } ?: "") +
                    if (rule.source == "LEARNED") " · learned" else "",
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
