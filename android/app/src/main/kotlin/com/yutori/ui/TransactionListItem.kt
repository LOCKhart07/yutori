package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.yutori.database.entities.TransactionEntity
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction row per mockups/v2.html frame 4. Merchant + sub-line
 * on the left, mono amount on the right. Color handles:
 *   - SPEND: default text
 *   - REFUND: positive green, `+` prefix
 *   - INCOME: info blue, `+` prefix
 *   - DROP:  strikethrough + faint (self-transfers, bill rails)
 *   - DROP self-transfer specifically: labeled as such in sub-line
 */
@Composable
fun TransactionListItem(
    entity: TransactionEntity,
    inr: NumberFormat,
    onClick: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    // Hoist @Composable getter reads — conditional access inside
    // if/else-if causes Compose group stack imbalance.
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entity.merchant?.takeIf { it.isNotBlank() } ?: "(no merchant)",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                if (entity.budgetEffect == "REFUND") {
                    Spacer(Modifier.size(8.dp))
                    EffectChip("refund", colors.positive)
                } else if (entity.budgetEffect == "INCOME") {
                    Spacer(Modifier.size(8.dp))
                    EffectChip("incoming", colors.info)
                } else if (entity.budgetEffect == "DROP") {
                    Spacer(Modifier.size(8.dp))
                    EffectChip("not counted", colors.onFaint)
                }
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = buildString {
                    append(dateFmt.format(Date(entity.occurredAtMs)))
                    entity.issuer?.let { append(" · ").append(it) }
                    entity.last4?.let { append(" ••").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onFaint,
            )
        }
        Text(
            text = formatAmount(entity, inr),
            style = YutoriTextStyles.Mono,
            color = amountColor(entity),
            textDecoration = if (entity.budgetEffect == "DROP") TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun EffectChip(label: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tint),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

private fun formatAmount(entity: TransactionEntity, inr: NumberFormat): String {
    val origAmt = entity.originalAmount
    val inrAmt = entity.inrAmount
    val raw = when {
        entity.originalCurrency != "INR" && origAmt != null ->
            "${entity.originalCurrency} ${"%.2f".format(origAmt)}"
        inrAmt != null -> inr.formatAmount(inrAmt, compact = true)
        else -> "pending"
    }
    return when (entity.budgetEffect) {
        "REFUND", "INCOME" -> "+$raw"
        else -> raw
    }
}

@Composable
private fun amountColor(entity: TransactionEntity): Color {
    // Read theme unconditionally — conditional @Composable getter access
    // inside `when` branches imbalances group stack on recomposition.
    val colors = YutoriTheme.colors
    val default = MaterialTheme.colorScheme.onBackground
    return when (entity.budgetEffect) {
        "SPEND"  -> default
        "REFUND" -> colors.positive
        "INCOME" -> colors.info
        "DROP"   -> colors.onFaint
        else     -> colors.onMuted
    }
}
