package com.yutori.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRuleMatching
import com.yutori.database.dao.AccountDao
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.RuleSuggestionDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.entities.AccountEntity
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.parser.Classification
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.launch

/** Spec: plans/settings-spec.md §3.5–§3.7. Mockup: v15-manual-rule-form.html. */

/**
 * Classifications that a recipient rule can target. Matches settings-spec §3.5
 * — subset of [Classification] that's "rule-addressable".
 */
private val RECLASSIFY_OPTIONS = listOf(
    Classification.CC_BILL_PAYMENT,
    Classification.SELF_TRANSFER,
    Classification.REFUND,
    Classification.INCOMING_CREDIT,
    Classification.NON_FINANCIAL,
)

private const val TEST_MERCHANT_LIMIT = 100
private const val TEST_MAX_SHOWN = 20

@Composable
fun AddEditRecipientRuleScreen(
    ruleId: Long?,
    prefillSuggestionId: Long?,
    recipientRuleDao: RecipientRuleDao,
    ruleSuggestionDao: RuleSuggestionDao,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = YutoriTheme.colors
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()

    val existingRule: RecipientRuleEntity? by produceState<RecipientRuleEntity?>(null, ruleId) {
        value = ruleId?.let { recipientRuleDao.getById(it) }
    }
    val prefillSuggestion: RuleSuggestionEntity? by produceState<RuleSuggestionEntity?>(
        null, prefillSuggestionId,
    ) {
        value = prefillSuggestionId?.let { ruleSuggestionDao.getById(it) }
    }
    val accounts: List<AccountEntity> by produceState<List<AccountEntity>>(emptyList()) {
        value = accountDao.getAll()
    }
    val recentMerchants: List<String> by produceState<List<String>>(emptyList()) {
        value = transactionDao.findRecentUpiMerchants(TEST_MERCHANT_LIMIT)
    }

    val isEdit = ruleId != null
    val isReadOnly = existingRule?.source == "SEED"

    var pattern by remember { mutableStateOf("") }
    var patternKind by remember { mutableStateOf(PatternKind.LITERAL) }
    var reclassifyAs by remember { mutableStateOf<Classification?>(null) }
    var linkedAccountId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var duplicateError by remember { mutableStateOf(false) }
    var seededFromLoad by remember { mutableStateOf(false) }

    LaunchedEffect(existingRule, prefillSuggestion) {
        if (seededFromLoad) return@LaunchedEffect
        existingRule?.let { r ->
            pattern = r.pattern
            patternKind = runCatching { PatternKind.valueOf(r.patternKind) }
                .getOrDefault(PatternKind.LITERAL)
            reclassifyAs = runCatching { Classification.valueOf(r.reclassifyAs) }.getOrNull()
            linkedAccountId = r.accountId
            note = r.note.orEmpty()
            enabled = r.isEnabled
            seededFromLoad = true
        }
        prefillSuggestion?.let { sg ->
            pattern = sg.pattern
            patternKind = runCatching { PatternKind.valueOf(sg.patternKind) }
                .getOrDefault(PatternKind.LITERAL)
            reclassifyAs = sg.inferredClassification
                ?.let { runCatching { Classification.valueOf(it) }.getOrNull() }
            linkedAccountId = sg.inferredAccountId
            seededFromLoad = true
        }
    }

    val patternTrimmed = pattern.trim()
    val draftEval: RecipientRuleMatching.DraftEval = remember(patternTrimmed, patternKind, recentMerchants) {
        RecipientRuleMatching.evalDraft(patternTrimmed, patternKind, recentMerchants)
    }
    val regexError = (draftEval as? RecipientRuleMatching.DraftEval.Invalid)?.error

    val needsAccount = reclassifyAs == Classification.SELF_TRANSFER
    val canSave = patternTrimmed.isNotEmpty() &&
        reclassifyAs != null &&
        regexError == null &&
        (!needsAccount || linkedAccountId != null) &&
        !isReadOnly &&
        !duplicateError

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding() + 8.dp)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BackRow(label = "Recipient rules", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isEdit) "Edit rule" else "Add rule",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(16.dp))

            if (prefillSuggestion != null) {
                PrefillBanner(prefillSuggestion!!)
                Spacer(Modifier.height(12.dp))
            }
            if (isReadOnly) {
                SeedReadOnlyBanner()
                Spacer(Modifier.height(12.dp))
            }

            if (isEdit && !isReadOnly) {
                EnabledToggle(
                    enabled = enabled,
                    onChange = { enabled = it },
                )
                Spacer(Modifier.height(6.dp))
            }

            FieldLabel("Pattern")
            ThemedTextField(
                value = pattern,
                onValueChange = {
                    pattern = it
                    duplicateError = false
                },
                placeholder = "e.g. cheq@axisbank",
                mono = true,
                error = regexError != null || duplicateError,
                readOnly = isReadOnly,
            )
            when {
                duplicateError -> InlineError("A rule with this pattern and kind already exists.")
                regexError != null -> InlineError("Regex won't compile: $regexError")
                else -> InlineHelp(
                    "Match against the recipient VPA or merchant name that appears in the SMS.",
                )
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("Pattern kind")
            EnumDropdown(
                value = patternKind.name.lowercase().replaceFirstChar { it.uppercase() },
                options = PatternKind.values().toList(),
                optionLabel = ::patternKindLabel,
                onPick = { patternKind = it },
                enabled = !isReadOnly,
            )
            InlineHelp(patternKindHelp(patternKind))

            Spacer(Modifier.height(14.dp))
            FieldLabel("Reclassify as")
            EnumDropdown(
                value = reclassifyAs?.let(::classificationLabel) ?: "Pick a classification",
                placeholder = reclassifyAs == null,
                options = RECLASSIFY_OPTIONS,
                optionLabel = ::classificationLabel,
                onPick = {
                    reclassifyAs = it
                    if (it != Classification.SELF_TRANSFER) linkedAccountId = null
                },
                enabled = !isReadOnly,
            )

            if (needsAccount) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("Linked account")
                if (accounts.isEmpty()) {
                    InlineHelp("No accounts registered. Add one in My accounts first.")
                } else {
                    EnumDropdown(
                        value = accounts.firstOrNull { it.id == linkedAccountId }
                            ?.let(::accountLabel)
                            ?: "Pick an account",
                        placeholder = linkedAccountId == null,
                        options = accounts,
                        optionLabel = ::accountLabel,
                        onPick = { linkedAccountId = it.id },
                        enabled = !isReadOnly,
                    )
                    InlineHelp(
                        "Both legs of a self-transfer drop from budget. " +
                            "Only your own accounts are eligible.",
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("Note (optional)")
            ThemedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "A short label for yourself",
                readOnly = isReadOnly,
            )

            Spacer(Modifier.height(18.dp))
            TestPanel(
                eval = draftEval,
                totalScanned = recentMerchants.size,
            )

            Spacer(Modifier.height(22.dp))
            ActionRow(
                showDelete = isEdit &&
                    (existingRule?.source == "USER" || existingRule?.source == "LEARNED"),
                canSave = canSave,
                onCancel = onBack,
                onDelete = {
                    scope.launch {
                        existingRule?.let { recipientRuleDao.delete(it) }
                        onSaved()
                    }
                },
                onSave = {
                    scope.launch {
                        val target = reclassifyAs ?: return@launch
                        val draft = RecipientRuleEntity(
                            id = existingRule?.id ?: 0L,
                            pattern = patternTrimmed,
                            patternKind = patternKind.name,
                            reclassifyAs = target.name,
                            accountId = linkedAccountId?.takeIf { needsAccount },
                            source = existingRule?.source
                                ?: if (prefillSuggestion != null) "LEARNED" else "USER",
                            note = note.trim().ifEmpty { null },
                            isEnabled = enabled,
                        )
                        val ok = runCatching {
                            if (existingRule == null) {
                                recipientRuleDao.insert(draft)
                            } else {
                                recipientRuleDao.update(draft)
                            }
                        }.onFailure { duplicateError = true }.isSuccess
                        if (!ok) return@launch
                        prefillSuggestionId?.let { ruleSuggestionDao.deleteById(it) }
                        onSaved()
                    }
                },
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---- Composable helpers -------------------------------------------------

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onFaint,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun InlineHelp(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = YutoriTheme.colors.onFaint,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun InlineError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = YutoriTheme.colors.negative,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false,
    error: Boolean = false,
    readOnly: Boolean = false,
) {
    val colors = YutoriTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                style = if (mono) YutoriTextStyles.Mono else MaterialTheme.typography.bodyLarge,
                color = colors.onFaint,
            )
        },
        singleLine = true,
        isError = error,
        readOnly = readOnly,
        textStyle = if (mono) YutoriTextStyles.Mono else MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = colors.divider,
            errorBorderColor = colors.negative,
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            errorContainerColor = colors.surfaceElevated,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> EnumDropdown(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    placeholder: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = YutoriTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (placeholder) colors.onFaint else colors.onMuted.run { colors.onMuted },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onFaint,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = {
                        onPick(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PrefillBanner(suggestion: RuleSuggestionEntity) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "PREFILLED FROM SUGGESTION",
                style = YutoriTextStyles.Caps,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (suggestion.inferredClassification == null) {
                    "We saw ${suggestion.pattern} " +
                        "${suggestion.matchCount} times in your last 60 days " +
                        "but couldn't guess the type. Pick one to save."
                } else {
                    "We saw ${suggestion.pattern} " +
                        "${suggestion.matchCount} times in your last 60 days. " +
                        "Adjust anything, then save."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
    }
}

@Composable
private fun SeedReadOnlyBanner() {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Text(
            text = "Built-in rule — read-only. Disable it from the list if you don't want it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMuted,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EnabledToggle(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Off means the rule stays on disk but stops firing on new SMS.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onFaint,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = colors.onMuted,
                    uncheckedTrackColor = colors.surfaceElevated2,
                    uncheckedBorderColor = colors.divider,
                ),
            )
        }
    }
}

@Composable
private fun TestPanel(
    eval: RecipientRuleMatching.DraftEval,
    totalScanned: Int,
) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "TEST · RECENT UPI MERCHANTS",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                val (countText, countColor) = when (eval) {
                    is RecipientRuleMatching.DraftEval.Invalid ->
                        "— invalid pattern —" to colors.negative
                    is RecipientRuleMatching.DraftEval.Valid ->
                        "${eval.matches.size} match${if (eval.matches.size == 1) "" else "es"} / $totalScanned" to
                            if (eval.matches.isEmpty()) colors.onFaint
                            else MaterialTheme.colorScheme.primary
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = countColor,
                )
            }

            Spacer(Modifier.height(8.dp))

            when (eval) {
                is RecipientRuleMatching.DraftEval.Invalid -> Text(
                    text = "Fix the pattern to see matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onFaint,
                )
                is RecipientRuleMatching.DraftEval.Valid -> {
                    if (eval.matches.isEmpty()) {
                        Text(
                            text = if (totalScanned == 0) {
                                "No recent UPI transactions to test against yet."
                            } else {
                                "No matches in the last $totalScanned UPI recipients."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onFaint,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            eval.matches.take(TEST_MAX_SHOWN).forEach { m ->
                                Text(
                                    text = m,
                                    style = YutoriTextStyles.Mono,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                            if (eval.matches.size > TEST_MAX_SHOWN) {
                                Text(
                                    text = "…and ${eval.matches.size - TEST_MAX_SHOWN} more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onFaint,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    showDelete: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (showDelete) {
            Surface(
                modifier = Modifier.clickable(onClick = onDelete),
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.negative),
            ) {
                Text(
                    text = "Delete rule",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.negative,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        } else {
            Spacer(Modifier.height(1.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.clickable(onClick = onCancel),
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.divider),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Surface(
                modifier = Modifier.clickable(enabled = canSave, onClick = onSave),
                shape = RoundedCornerShape(10.dp),
                color = if (canSave) MaterialTheme.colorScheme.primary else colors.surfaceElevated2,
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) MaterialTheme.colorScheme.onPrimary else colors.onFaint,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ---- Plain helpers ------------------------------------------------------

private fun patternKindLabel(kind: PatternKind): String = when (kind) {
    PatternKind.LITERAL -> "Literal"
    PatternKind.PREFIX -> "Prefix"
    PatternKind.REGEX -> "Regex"
}

private fun patternKindHelp(kind: PatternKind): String = when (kind) {
    PatternKind.LITERAL -> "Exact string match, case-sensitive."
    PatternKind.PREFIX -> "startsWith match — good for merchant-name families."
    PatternKind.REGEX -> "Java Pattern syntax. Rule matches anywhere in the string — " +
        "anchor with ^/\$ yourself."
}

private fun classificationLabel(c: Classification): String = when (c) {
    Classification.CC_BILL_PAYMENT -> "CC bill payment"
    Classification.SELF_TRANSFER -> "Self-transfer"
    Classification.REFUND -> "Refund"
    Classification.INCOMING_CREDIT -> "Incoming credit"
    Classification.NON_FINANCIAL -> "Non-financial (drop)"
    else -> c.name
}

private fun accountLabel(a: AccountEntity): String {
    val last4 = a.last4?.let { " ••$it" } ?: ""
    val name = a.displayName?.let { " · $it" } ?: ""
    return "${a.issuer}$last4$name"
}
