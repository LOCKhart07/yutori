package com.yutori.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yutori.R
import com.yutori.ui.BackRow
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

/**
 * Settings → About Yutori (#66). Ship-blocker for going public — tells
 * the user what the app is, what it stands for, and how to reach the
 * repo. See `plans/about-screen-spec.md` for the full layout and
 * `mockups/v12-about.html` for the visual reference.
 *
 * Stateless: all inputs are the app's current versionName / commitSha
 * plus navigation callbacks. Update checking lives on top-level
 * Settings via [com.yutori.ui.update.AppUpdatesSection] — About only
 * surfaces the build identity.
 */
@Composable
fun AboutScreen(
    versionName: String,
    commitSha: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenRepo: () -> Unit,
    loadStats: suspend () -> AllTimeStats = { AllTimeStats(0, 0, null, 0, 0.0, 0) },
) {
    // Easter egg (#79): 7 taps on the Version row opens an all-time
    // stats dialog. Counter resets on dialog open and on leaving About.
    var tapCount by remember { mutableStateOf(0) }
    var versionTinted by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var stats: AllTimeStats? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showStats) {
        if (showStats && stats == null) {
            stats = loadStats()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp)
                    .padding(horizontal = 24.dp),
            ) {
                BackRow(label = "Settings", onBack = onBack)
                Spacer(Modifier.height(16.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Hero()

                Spacer(Modifier.height(28.dp))
                SectionHead("Why \"Yutori\"")
                WhyYutoriCard()

                Spacer(Modifier.height(20.dp))
                SectionHead("Principles")
                PrinciplesList()

                Spacer(Modifier.height(20.dp))
                SectionHead("Build")
                BuildCard(
                    versionName = versionName,
                    commitSha = commitSha,
                    versionTinted = versionTinted,
                    onVersionTap = {
                        scope.launch {
                            versionTinted = true
                            delay(VERSION_TAP_TINT_MS)
                            versionTinted = false
                        }
                        val next = tapCount + 1
                        if (next >= VERSION_TAPS_TO_UNLOCK) {
                            tapCount = 0
                            showStats = true
                        } else {
                            tapCount = next
                        }
                    },
                )

                Spacer(Modifier.height(20.dp))
                SectionHead("More")
                LinkRow(
                    leadingIcon = Icons.AutoMirrored.Filled.List,
                    label = "Open-source licenses",
                    subtitle = "Libraries this app depends on.",
                    onClick = onOpenLicenses,
                )
                Spacer(Modifier.height(8.dp))
                LinkRow(
                    leadingIcon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "View on GitHub",
                    subtitle = "github.com/LOCKhart07/yutori",
                    onClick = onOpenRepo,
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    val currentStats = stats
    if (showStats && currentStats != null) {
        AllTimeStatsDialog(
            stats = currentStats,
            onDismiss = { showStats = false },
        )
    }
}

private const val VERSION_TAPS_TO_UNLOCK = 7
private const val VERSION_TAP_TINT_MS = 120L

@Composable
private fun Hero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        // Brand identity rebuilt in Compose: teal circle + foreground
        // ゆ VectorDrawable overlay. The launcher's adaptive XML
        // (`R.mipmap.ic_launcher_round`) isn't supported by
        // `painterResource` (only VectorDrawables and raster types are),
        // so we compose the visual from its two source layers — both
        // in-sync with `res/drawable/ic_launcher_{background,foreground}.xml`.
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(YutoriTheme.colors.brandTeal),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Yutori logo",
                modifier = Modifier.size(112.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Yutori",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "You don't want to spend less.\nYou want to spend confidently.",
            style = MaterialTheme.typography.bodyMedium,
            color = YutoriTheme.colors.onMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHead(label: String) {
    Text(
        text = label.uppercase(),
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onFaint,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun WhyYutoriCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = YutoriTheme.colors.surfaceElevated,
    ) {
        Text(
            text = buildYutoriParagraph(),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

/**
 * Builds the "Why 'Yutori'" paragraph as an AnnotatedString so the
 * two brand-highlight words ("Yutori", "ゆ") render on-background
 * instead of on-muted.
 */
@Composable
private fun buildYutoriParagraph(): AnnotatedString {
    val strong = SpanStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
    )
    return buildAnnotatedString {
        withStyle(strong) { append("Yutori") }
        append(" (余裕) is Japanese for \"breathing room\": financial margin, mental ease, room to spend without friction. The logo is the hiragana ")
        withStyle(strong) { append("ゆ") }
        append(" (\"yu\") rendered as negative space; the space itself is the design.")
    }
}

@Composable
private fun PrinciplesList() {
    PrincipleCard(
        num = "1",
        title = "SMS-first, human-assist second",
        body = "Bank SMS is the source of truth. Manual entry stays light.",
    )
    PrincipleCard(
        num = "2",
        title = "Margin, not micromanagement",
        body = "Answers \"how much room do I have?\" without moralising spend.",
    )
    PrincipleCard(
        num = "3",
        title = "On-device by default",
        body = "You own your data.",
    )
    PrincipleCard(
        num = "4",
        title = "Opinionated about scope",
        body = "Personal monthly spend from SMS. Nothing more.",
    )
    PrincipleCard(
        num = "5",
        title = "Side-loaded, open source",
        body = "GitHub releases, in-app updates. Code is public, audit it.",
    )
}

@Composable
private fun PrincipleCard(num: String, title: String, body: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = YutoriTheme.colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(YutoriTheme.colors.surfaceElevated2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = num,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = YutoriTheme.colors.onFaint,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = YutoriTheme.colors.onMuted,
            )
        }
    }
}

@Composable
private fun BuildCard(
    versionName: String,
    commitSha: String,
    versionTinted: Boolean,
    onVersionTap: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = YutoriTheme.colors.surfaceElevated,
    ) {
        Column {
            BuildInfoRow(
                label = "Version",
                value = versionName,
                rowModifier = Modifier
                    .clickable(onClick = onVersionTap)
                    .background(
                        if (versionTinted) {
                            YutoriTheme.colors.info.copy(alpha = 0.10f)
                        } else {
                            Color.Transparent
                        },
                    ),
                valueColor = if (versionTinted) YutoriTheme.colors.info else null,
            )
            HorizontalDivider(color = YutoriTheme.colors.divider)
            BuildInfoRow(label = "Commit", value = commitSha)
        }
    }
}

@Composable
private fun BuildInfoRow(
    label: String,
    value: String,
    rowModifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = valueColor ?: YutoriTheme.colors.onMuted,
        )
    }
}

@Composable
private fun AllTimeStatsDialog(
    stats: AllTimeStats,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = YutoriTheme.colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text(
                    text = "✦ ALL-TIME YUTORI STATS",
                    style = YutoriTextStyles.Caps,
                    color = YutoriTheme.colors.info,
                )
                Spacer(Modifier.height(14.dp))
                StatsGrid(stats)
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = YutoriTheme.colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = YutoriTheme.colors.info,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: AllTimeStats) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatRow(
            left = "SMS processed" to stats.smsProcessed.toString(),
            right = "Transactions" to stats.transactions.toString(),
        )
        StatRow(
            left = "First tracked" to stats.firstTrackedMonth.formatShort(),
            right = "Months tracked" to stats.monthsTracked.toString(),
        )
        StatRow(
            left = "Lifetime tracked" to formatInrShort(stats.lifetimeSpendInr),
            right = "Zero-spend days" to stats.zeroSpendDays.toString(),
        )
    }
}

@Composable
private fun StatRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StatCell(label = left.first, value = left.second, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        StatCell(label = right.first, value = right.second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = YutoriTextStyles.Caps,
            color = YutoriTheme.colors.onFaint,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LinkRow(
    leadingIcon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = YutoriTheme.colors.surfaceElevated,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = YutoriTheme.colors.onMuted,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = YutoriTheme.colors.onMuted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = YutoriTheme.colors.onFaint,
            )
        }
    }
}
