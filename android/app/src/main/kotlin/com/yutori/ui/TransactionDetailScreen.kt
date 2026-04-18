package com.yutori.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.yutori.classifier.Classifier
import com.yutori.classifier.RecipientRuleMatching
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.SmsLogDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.dao.TransactionSourceDao
import com.yutori.database.entities.SmsLogEntity
import com.yutori.database.entities.TransactionEntity
import com.yutori.database.entities.TransactionSourceEntity
import com.yutori.database.mappers.RecipientRuleMapper
import com.yutori.parser.Category
import com.yutori.parser.Classification
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction detail — v2 per mockups/v2.html frames 5, 6, 10.
 * Hero amount in mono, merchant + when, classification pill with
 * effect color dot, forex "original + rate" block if applicable,
 * meta rows, source SMS block (monospace raw body).
 */
private data class Details(
    val tx: TransactionEntity,
    val sources: List<Pair<TransactionSourceEntity, SmsLogEntity?>>,
)

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    transactionDao: TransactionDao,
    recipientRuleDao: RecipientRuleDao,
    sourceDao: TransactionSourceDao,
    smsLogDao: SmsLogDao,
    onBack: () -> Unit,
) {
    // Bumped after a mutation (e.g. notes edited) so produceState
    // re-reads the row without flickering back to the loading state —
    // produceState keeps its previous value until the block emits.
    var refreshTick by remember { mutableIntStateOf(0) }
    val details: Details? by produceState<Details?>(null, transactionId, refreshTick) {
        val tx = transactionDao.getById(transactionId) ?: return@produceState
        val sources = sourceDao.findByTransactionId(transactionId)
        val withBodies = sources.map { src -> src to smsLogDao.getById(src.smsLogId) }
        value = Details(tx, withBodies)
    }
    val scope = rememberCoroutineScope()

    // Branch at the top with `when` — no early returns. The Compose
    // compiler wraps each branch in its own group, so the group count
    // stays balanced across the null → loaded state change.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val d = details) {
            null -> LoadingView(onBack = onBack)
            else -> ReadyView(
                details = d,
                onBack = onBack,
                onSaveNote = { newNote ->
                    scope.launch {
                        transactionDao.updateNote(d.tx.id, newNote)
                        refreshTick++
                    }
                },
                onSaveCategory = { selectedCategory ->
                    scope.launch {
                        if (selectedCategory != null) {
                            transactionDao.updateCategory(
                                id = d.tx.id,
                                category = selectedCategory,
                                isOverridden = true,
                            )
                        } else {
                            val rules = recipientRuleDao.getEnabled()
                                .map(RecipientRuleMapper::toDomain)
                            val matchedRule = RecipientRuleMatching.firstMatch(d.tx.merchant, rules)
                            val classification = runCatching {
                                Classification.valueOf(d.tx.classification)
                            }.getOrNull()
                            val fallback = classification?.let {
                                Classifier.resolveCategory(
                                    classification = it,
                                    parserAssignedCategory = null,
                                    merchantKey = d.tx.merchantKey,
                                    matchedRule = matchedRule,
                                )
                            }
                            transactionDao.updateCategory(
                                id = d.tx.id,
                                category = fallback?.name,
                                isOverridden = false,
                            )
                        }
                        refreshTick++
                    }
                },
            )
        }
    }
}

@Composable
private fun LoadingView(onBack: () -> Unit) {
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusInset.calculateTopPadding() + 8.dp)
            .padding(horizontal = 24.dp),
    ) {
        BackRow(label = "Back", onBack = onBack)
        LoadingSpinner()
    }
}

@Composable
private fun ReadyView(
    details: Details,
    onBack: () -> Unit,
    onSaveNote: (String?) -> Unit,
    onSaveCategory: (String?) -> Unit,
) {
    var editingNote by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
                .padding(horizontal = 24.dp),
        ) {
            BackRow(label = "Back", onBack = onBack)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Hero(details.tx)
            Spacer(Modifier.height(24.dp))
            MetaSection(details.tx)
            if (!details.tx.notes.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                NotesCard(note = details.tx.notes!!, onEdit = { editingNote = true })
            }
            Spacer(Modifier.height(16.dp))
            ActionRow(
                hasNote = !details.tx.notes.isNullOrBlank(),
                onEditNote = { editingNote = true },
                canEditCategory = details.tx.budgetEffect == "SPEND" || details.tx.budgetEffect == "REFUND",
                onEditCategory = { editingCategory = true },
            )
            Spacer(Modifier.height(24.dp))
            if (details.sources.isNotEmpty()) {
                SourcesSection(details.sources)
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (editingNote) {
        EditNoteSheet(
            initialNote = details.tx.notes,
            onDismiss = { editingNote = false },
            onSave = { newNote ->
                onSaveNote(newNote)
                editingNote = false
            },
        )
    }
    if (editingCategory) {
        EditCategorySheet(
            initialCategory = details.tx.category,
            initiallyOverridden = details.tx.categoryOverride,
            onDismiss = { editingCategory = false },
            onSave = { selected ->
                onSaveCategory(selected)
                editingCategory = false
            },
        )
    }
}

// ───────────────────────── Hero ─────────────────────────

@Composable
private fun Hero(tx: TransactionEntity) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val timeFmt = remember {
        SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
    }
    Spacer(Modifier.height(16.dp))

    Text(
        text = timeFmt.format(Date(tx.occurredAtMs)).uppercase(),
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onMuted,
    )
    Spacer(Modifier.height(10.dp))

    // Primary amount — original currency if forex, INR otherwise.
    // Pull into locals — cross-module `val` isn't smart-cast-safe.
    val originalAmt = tx.originalAmount
    val inrAmt = tx.inrAmount
    val primary = when {
        tx.originalCurrency != "INR" && originalAmt != null ->
            "${tx.originalCurrency} ${"%.2f".format(originalAmt)}"
        inrAmt != null -> inr.formatAmount(inrAmt)
        else -> "Pending"
    }
    // Read theme tokens unconditionally — reading @Composable getters
    // inside `when` branches imbalances the Compose group stack.
    val colors = YutoriTheme.colors
    val onBackground = MaterialTheme.colorScheme.onBackground
    val primaryColor = when (tx.budgetEffect) {
        "REFUND" -> colors.positive
        "INCOME" -> colors.info
        "DROP"   -> colors.onFaint
        else     -> onBackground
    }
    Text(
        text = primary,
        style = MaterialTheme.typography.displayMedium,
        color = primaryColor,
    )

    // Forex sub-line
    val rate = tx.exchangeRate
    if (tx.originalCurrency != "INR" && inrAmt != null && rate != null) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "≈ ${inr.formatAmount(inrAmt)}  at  ${"%.4f".format(rate)}  ",
                style = MaterialTheme.typography.bodySmall,
                color = YutoriTheme.colors.onMuted,
            )
            Text(
                text = tx.rateSource ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else if (tx.rateSource == "pending") {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "INR conversion pending.",
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.info,
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        text = tx.merchant?.takeIf { it.isNotBlank() } ?: "(no merchant)",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    )

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClassificationPill(tx.classification, tx.budgetEffect)
        if (tx.originalCurrency != "INR") {
            InfoPill(label = "Forex", tint = YutoriTheme.colors.info)
        }
    }
    val origClass = tx.classificationOriginal
    if (origClass != null && origClass != tx.classification) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "was ${prettyClassification(origClass)}",
            style = MaterialTheme.typography.labelSmall,
            color = YutoriTheme.colors.onFaint,
        )
    }
}

@Composable
private fun ClassificationPill(classification: String, budgetEffect: String) {
    val colors = YutoriTheme.colors
    val tint = when (budgetEffect) {
        "SPEND"  -> colors.onMuted
        "REFUND" -> colors.positive
        "INCOME" -> colors.info
        "DROP"   -> colors.onFaint
        else     -> colors.onMuted
    }
    Pill(
        label = "${prettyClassification(classification)} · $budgetEffect",
        tint = tint,
    )
}

@Composable
private fun InfoPill(label: String, tint: Color) {
    Pill(label = label, tint = tint)
}

@Composable
private fun Pill(label: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(YutoriTheme.colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tint),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

// ───────────────────────── Meta rows ─────────────────────────

@Composable
private fun MetaSection(tx: TransactionEntity) {
    Column {
        HorizontalDivider(color = YutoriTheme.colors.divider)
        MetaRow(
            label = "Account",
            value = buildString {
                tx.issuer?.let { append(it) }
                if (tx.issuer != null && tx.last4 != null) append(" ")
                tx.last4?.let { append("••").append(it) }
                if (tx.issuer == null && tx.last4 == null) append("—")
            },
            mono = true,
        )
        tx.category?.let { MetaRow("Category", prettyCategory(it)) }
        MetaRow("Month", prettyMonth(tx.monthKey))
        val origAmt = tx.originalAmount
        if (tx.originalCurrency != "INR" && origAmt != null) {
            MetaRow(
                label = "Original",
                value = "${tx.originalCurrency} ${"%.2f".format(origAmt)}",
                mono = true,
            )
        }
        val rate = tx.exchangeRate
        if (rate != null && tx.originalCurrency != "INR") {
            MetaRow(
                label = "Rate",
                value = "${"%.4f".format(rate)} INR/${tx.originalCurrency}",
                mono = true,
            )
        }
        if (tx.manuallyAdjusted) MetaRow("Manually adjusted", "Yes")
    }
}

@Composable
private fun MetaRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = YutoriTheme.colors.onMuted,
        )
        Text(
            text = value,
            style = if (mono) YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyMedium,
        )
    }
}

// ───────────────────────── Notes + actions ─────────────────────────

/**
 * Full-width card hosting the free-text note for a transaction.
 * Rendered only when the note is non-blank; empty state is handled
 * by the action row below.
 */
@Composable
private fun NotesCard(note: String, onEdit: () -> Unit) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NOTE",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Action row for detail-level edits.
 */
@Composable
private fun ActionRow(
    hasNote: Boolean,
    onEditNote: () -> Unit,
    canEditCategory: Boolean,
    onEditCategory: () -> Unit,
) {
    val context = LocalContext.current
    val showSoonToast: () -> Unit = {
        Toast.makeText(context, "Soon\u2122", Toast.LENGTH_SHORT).show()
    }
    Column {
        HorizontalDivider(color = YutoriTheme.colors.divider)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = if (hasNote) "Edit note" else "Add note",
                onClick = onEditNote,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "Edit category",
                onClick = if (canEditCategory) onEditCategory else showSoonToast,
                ghost = !canEditCategory,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "Add rule",
                onClick = showSoonToast,
                ghost = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
) {
    val colors = YutoriTheme.colors
    val bg = if (ghost) Color.Transparent else colors.surfaceElevated
    val fg = if (ghost) colors.onFaint else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

internal const val NOTE_MAX_LEN = 500

/**
 * Pure decision logic for the EditNoteSheet — split out so it's
 * unit-testable without spinning up Compose. Save is gated on:
 *  - respecting [NOTE_MAX_LEN] (by raw character count, so trimming
 *    whitespace can't smuggle a too-long note past the cap), and
 *  - having actually changed the note (trim-equivalence with the
 *    initial value, so a user typing and re-deleting a character
 *    can't save a no-op).
 */
internal fun noteSaveEnabled(text: String, initial: String?): Boolean {
    if (text.length > NOTE_MAX_LEN) return false
    return text.trim() != initial.orEmpty().trim()
}

/** Value to persist — blank → null so cleared notes don't linger as "". */
internal fun noteSavePayload(text: String): String? =
    text.trim().ifBlank { null }

/**
 * Modal bottom sheet wrapping a multi-line note editor. Saves
 * blank → null so cleared notes don't linger as empty strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNoteSheet(
    initialNote: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initialNote.orEmpty()) }
    val overLimit = text.length > NOTE_MAX_LEN
    val saveEnabled = noteSaveEnabled(text, initialNote)
    val colors = YutoriTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (initialNote.isNullOrBlank()) "Add note" else "Edit note",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        text = "e.g. \"dentist\", \"birthday gift\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onFaint,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 3,
                maxLines = 6,
                isError = overLimit,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colors.divider,
                    errorBorderColor = colors.negative,
                    focusedContainerColor = colors.surfaceElevated2,
                    unfocusedContainerColor = colors.surfaceElevated2,
                    errorContainerColor = colors.surfaceElevated2,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${text.length} / $NOTE_MAX_LEN",
                style = YutoriTextStyles.MonoSmall,
                color = if (overLimit) colors.negative else colors.onFaint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryActionButton(
                    text = "Save",
                    enabled = saveEnabled,
                    onClick = { onSave(noteSavePayload(text)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCategorySheet(
    initialCategory: String?,
    initiallyOverridden: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = YutoriTheme.colors
    var selected by remember { mutableStateOf(initialCategory) }
    val options: List<String?> = remember { listOf(null) + Category.entries.map { it.name } }
    val saveEnabled = if (selected == null) {
        initiallyOverridden
    } else {
        selected != initialCategory
    }
    val currentModeLabel = if (initiallyOverridden) {
        "Currently: overridden"
    } else {
        "Currently: automatic"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Edit category",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentModeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onFaint,
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val label = option?.let(::prettyCategory) ?: "Use automatic category"
                    val selectedNow = selected == option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = option },
                        color = if (selectedNow) colors.surfaceElevated2 else Color.Transparent,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryActionButton(
                    text = "Save",
                    enabled = saveEnabled,
                    onClick = { onSave(selected) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ───────────────────────── Sources ─────────────────────────

@Composable
private fun SourcesSection(sources: List<Pair<TransactionSourceEntity, SmsLogEntity?>>) {
    Text(
        text = if (sources.size == 1) "SOURCE SMS" else "BUILT FROM ${sources.size} SOURCES",
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onFaint,
    )
    Spacer(Modifier.height(10.dp))
    sources.forEach { (src, log) ->
        SourceBlock(src, log)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SourceBlock(src: TransactionSourceEntity, log: SmsLogEntity?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = YutoriTheme.colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = YutoriTheme.colors.divider,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prettyRole(src.role),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = YutoriTheme.colors.onMuted,
                )
                if (src.isPrimary) {
                    Text(
                        text = "PRIMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (log != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = log.body,
                    style = YutoriTextStyles.MonoSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "From ${log.sender} · ${prettyClassification(log.classification)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = YutoriTheme.colors.onFaint,
                )
            } else {
                Text(
                    text = "(sms_log row ${src.smsLogId} not found)",
                    style = MaterialTheme.typography.labelSmall,
                    color = YutoriTheme.colors.onFaint,
                )
            }
        }
    }
}

// Classification / role enums use upper-snake tokens that include
// acronyms (UPI, CC, ATM, OTP). Default Title-Casing mangles them —
// "UPI_PAYMENT" → "Upi Payment" — so preserve the known acronyms.
private val CLASSIFICATION_ACRONYMS = setOf("UPI", "CC", "ATM", "OTP")
private val ROLE_ACRONYMS = setOf("UPI", "CC", "ATM", "OTP", "ACK", "NOTIF")

private fun prettyClassification(name: String): String =
    name.split("_").joinToString(" ") { tok ->
        if (tok in CLASSIFICATION_ACRONYMS) tok
        else tok.lowercase().replaceFirstChar { c -> c.titlecase() }
    }

// SMS-source card header: sentence-case with acronyms preserved.
// BANK_DEBIT → "Bank debit", CC_PAYMENT_RECEIPT → "CC payment receipt",
// MERCHANT_ACK → "Merchant ACK".
private fun prettyRole(role: String): String =
    role.split("_").mapIndexed { i, tok ->
        when {
            tok in ROLE_ACRONYMS -> tok
            i == 0 -> tok.lowercase().replaceFirstChar { c -> c.titlecase() }
            else -> tok.lowercase()
        }
    }.joinToString(" ")

private fun prettyMonth(monthKey: String): String = try {
    val (y, m) = monthKey.split("-").let { it[0] to it[1].toInt() }
    val name = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[m - 1]
    "$name $y"
} catch (_: Exception) { monthKey }
