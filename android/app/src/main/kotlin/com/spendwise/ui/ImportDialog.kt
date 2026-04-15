package com.spendwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Import range picker — rebuilt as a themed Dialog (not AlertDialog)
 * so we can style the option rows + CTAs to match the rest of the app.
 */
@Composable
fun ImportDialog(
    onConfirm: (sinceMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // `null` days → "everything" (sinceMs = 0 at confirm time).
    val options = remember {
        listOf<Pair<String, Int?>>(
            "Last 1 month" to 30,
            "Last 3 months" to 90,
            "Last 6 months" to 180,
            "Last 1 year" to 365,
            "Everything on this phone" to null,
        )
    }
    var selectedIndex by remember { mutableIntStateOf(1) }   // default: 3 months
    val colors = SpendWiseTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp, color = colors.divider,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "IMPORT PAST SMS",
                    style = SpendWiseTextStyles.Caps,
                    color = colors.onFaint,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Reads your SMS history and extracts transactions. " +
                        "Already-imported messages are skipped automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                )

                Spacer(Modifier.height(16.dp))

                options.forEachIndexed { i, (label, _) ->
                    RadioRow(
                        label = label,
                        selected = i == selectedIndex,
                        onClick = { selectedIndex = i },
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryActionButton(
                        text = "Import",
                        onClick = {
                            val days = options[selectedIndex].second
                            val sinceMs = if (days == null) {
                                0L
                            } else {
                                Instant.now()
                                    .atZone(ZoneId.systemDefault())
                                    .minus(days.toLong(), ChronoUnit.DAYS)
                                    .toInstant()
                                    .toEpochMilli()
                            }
                            onConfirm(sinceMs)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SpendWiseTheme.colors
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Custom radio glyph — Material3 RadioButton uses the primary
        // color but renders with a softer tint that reads muddy on our
        // dark surface. This draws a crisp amber ring with a filled
        // inner dot when selected.
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) accent else colors.onFaint,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                colors.onMuted
            },
        )
    }
}
