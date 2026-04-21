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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.classifier.BudgetEffect
import com.yutori.classifier.budgetEffectForClassification
import com.yutori.parser.Classification
import com.yutori.parser.displayName
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Burial screen for the per-SMS ingestion outcomes that used to render
 * on the dashboard (#146). Scrollable list of the latest N messages with
 * their parsed classification outcome; reached from Settings → Data &
 * accounts → Message log.
 */
@Composable
fun MessageLogScreen(
    messagesFlow: Flow<List<LatestIngestedMessage>>,
    totalCountFlow: Flow<Int>,
    onBack: () -> Unit,
) {
    val messages: List<LatestIngestedMessage> by messagesFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val totalCount: Int by totalCountFlow.collectAsStateWithLifecycle(initialValue = 0)

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
            Text("Message log", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = metaLine(shown = messages.size, total = totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = YutoriTheme.colors.onMuted,
            )
            Spacer(Modifier.height(16.dp))

            if (messages.isEmpty()) {
                EmptyLogState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = messages, key = { it.id }) { msg ->
                        MessageLogRow(msg)
                        HorizontalDivider(color = YutoriTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Yutori hasn't ingested any SMS on this device yet. As bank " +
                "and UPI messages arrive, they'll show up here with their " +
                "classification.",
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

@Composable
private fun MessageLogRow(message: LatestIngestedMessage) {
    val colors = YutoriTheme.colors
    val label = message.classification?.displayName ?: Classification.UNMATCHED.displayName
    val (textTone, pillTone) = pillTones(message.classification, colors)
    val timestamp = remember(message.receivedAtMs) {
        formatReceivedAt(message.receivedAtMs)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onFaint,
            )
        }
        Text(
            text = message.bodyPreview,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMuted,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = pillTone,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textTone,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun pillTones(
    classification: Classification?,
    colors: com.yutori.ui.theme.YutoriColorExtras,
): Pair<Color, Color> {
    // UNMATCHED (parser gap) deserves its own tone so it stays scannable —
    // its BudgetEffect is DROP, but visually it's not "the app decided to
    // drop this", it's "the app couldn't decide". Same treatment for null
    // (legacy string that no longer decodes).
    if (classification == null || classification == Classification.UNMATCHED) {
        return colors.warn to colors.warn.copy(alpha = 0.2f)
    }
    return when (budgetEffectForClassification(classification)) {
        BudgetEffect.SPEND, BudgetEffect.REFUND ->
            colors.positive to colors.positive.copy(alpha = 0.2f)
        BudgetEffect.INCOME ->
            colors.info to colors.info.copy(alpha = 0.2f)
        BudgetEffect.DROP ->
            colors.onMuted to colors.surfaceElevated2
    }
}

private fun metaLine(shown: Int, total: Int): String {
    if (total == 0) return "No ingested SMS yet."
    val suffix = if (total == 1) "1 ingested SMS" else "$total ingested SMS"
    return when {
        shown >= total -> "Newest first · showing all $suffix."
        else -> "Newest first · showing last $shown of $suffix."
    }
}

private val MESSAGE_TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.getDefault())

private fun formatReceivedAt(receivedAtMs: Long): String =
    MESSAGE_TIMESTAMP_FORMAT.format(
        Instant.ofEpochMilli(receivedAtMs).atZone(ZoneId.systemDefault()),
    )
