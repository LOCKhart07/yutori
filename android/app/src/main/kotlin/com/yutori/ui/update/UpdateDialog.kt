package com.yutori.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yutori.ui.theme.YutoriTheme

/**
 * Mockup variant D (see mockups/v9-autoupdater.html):
 * [Dialog] + [DialogProperties] with `usePlatformDefaultWidth = false`
 * so the panel can fill ~85% of the screen. Title/subtitle are fixed,
 * action row is pinned at the bottom, and the release-notes body
 * scrolls between them with a bottom-edge fade as the "more below"
 * affordance.
 */
@Composable
fun UpdateDialog(
    state: UpdateScreenState,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    if (!state.dialogVisible) return
    val phase = state.phase
    val release = when (phase) {
        is UpdateScreenState.Phase.Available -> phase.release
        is UpdateScreenState.Phase.Downloading -> phase.release
        is UpdateScreenState.Phase.DownloadFailed -> phase.release
        is UpdateScreenState.Phase.InstallFailed -> phase.release
        else -> return
    }
    val targetVersion = release.tagName.removePrefix("v")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(18.dp))
                .background(YutoriTheme.colors.surfaceElevated)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            DialogHeader(
                title = if (phase is UpdateScreenState.Phase.Downloading) {
                    "Downloading $targetVersion"
                } else {
                    release.name.ifBlank { "Yutori $targetVersion" }
                },
                subtitle = when (phase) {
                    is UpdateScreenState.Phase.Downloading ->
                        "${humanBytes(phase.bytes)} of ${humanBytes(phase.total)}"
                    else -> "Current ${state.currentVersion}  →  New $targetVersion"
                },
            )
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (phase) {
                    is UpdateScreenState.Phase.Available -> ReleaseNotes(body = release.body)
                    is UpdateScreenState.Phase.Downloading -> DownloadingBody(phase = phase)
                    is UpdateScreenState.Phase.DownloadFailed -> DownloadErrorBody()
                    is UpdateScreenState.Phase.InstallFailed -> InstallErrorBody(phase = phase)
                    else -> Unit
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = YutoriTheme.colors.divider)
            Spacer(Modifier.height(10.dp))

            PinnedActions(
                phase = phase,
                onDismiss = onDismiss,
                onStartDownload = onStartDownload,
                onCancelDownload = onCancelDownload,
            )
        }
    }
}

@Composable
private fun DialogHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = YutoriTheme.colors.onMuted,
    )
}

@Composable
private fun ReleaseNotes(body: String) {
    val blocks = MarkdownRenderer.parse(body)
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> HeadingBlock(block)
                    is MdBlock.Bullet -> BulletBlock(block)
                    is MdBlock.Paragraph -> ParagraphBlock(block)
                    is MdBlock.Rule -> RuleBlock()
                }
            }
        }
        // Fade affordance — hints "more below" without a scrollbar.
        val bg = YutoriTheme.colors.surfaceElevated
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg))),
        )
    }
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleSmall
        2 -> MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        else -> MaterialTheme.typography.labelMedium
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = toAnnotated(block.text),
        style = style.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun BulletBlock(block: MdBlock.Bullet) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "•  ",
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.onMuted,
        )
        Text(
            text = toAnnotated(block.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ParagraphBlock(block: MdBlock.Paragraph) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = toAnnotated(block.text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun toAnnotated(inline: InlineText): AnnotatedString = buildAnnotatedString {
    append(inline.text)
    inline.spans.forEach { span ->
        val style = when (span.kind) {
            InlineText.SpanKind.Code -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = YutoriTheme.colors.surfaceElevated2,
                fontSize = 12.sp,
            )
            InlineText.SpanKind.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
            // Tint links info-blue. Not clickable in v1 — tap "See full
            // notes on GitHub" at the bottom for navigation.
            InlineText.SpanKind.Link -> SpanStyle(color = YutoriTheme.colors.info)
        }
        addStyle(style, span.start, span.end)
    }
}

@Composable
private fun RuleBlock() {
    Spacer(Modifier.height(6.dp))
    HorizontalDivider(color = YutoriTheme.colors.divider)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DownloadingBody(phase: UpdateScreenState.Phase.Downloading) {
    val safeTotal = phase.total.coerceAtLeast(1L)
    val progress = (phase.bytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, YutoriTheme.colors.divider, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = phase.release.asset?.name ?: "update.apk",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = YutoriTheme.colors.onMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = YutoriTheme.colors.info,
                    trackColor = YutoriTheme.colors.surfaceElevated2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Android's install confirmation will appear automatically when the download finishes. " +
                "If \"Install unknown apps\" is disabled for Yutori, the system will ask you to enable it first.",
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

@Composable
private fun DownloadErrorBody() {
    ErrorCard(
        heading = "Download interrupted",
        body = "Check your connection and try again. If the problem persists, download the APK " +
            "directly from GitHub Releases.",
    )
}

@Composable
private fun InstallErrorBody(phase: UpdateScreenState.Phase.InstallFailed) {
    ErrorCard(
        heading = installFailHeading(phase.status),
        body = installFailBody(phase.status, phase.message),
    )
}

@Composable
private fun ErrorCard(heading: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(YutoriTheme.colors.negative.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = YutoriTheme.colors.negative.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            color = YutoriTheme.colors.negative,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

// PackageInstaller status codes — see android.content.pm.PackageInstaller.
private fun installFailHeading(status: Int): String = when (status) {
    android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
    android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install blocked"
    android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT -> "Install conflict"
    android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
    android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Incompatible build"
    android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID -> "Install rejected"
    else -> "Install failed"
}

private fun installFailBody(status: Int, message: String?): String {
    val osLine = message?.takeIf { it.isNotBlank() }?.let { "\nSystem: $it" }.orEmpty()
    val primary = when (status) {
        android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED ->
            "You dismissed Android's install confirmation. Tap Try again to retry."
        android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "Android blocked the install — usually because \"Install unknown apps\" isn't " +
                "allowed for Yutori. Grant it in system settings and try again."
        android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Package conflicts with an existing install. Uninstall the old version and retry."
        android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE ->
            "Not enough free storage to install this update."
        android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "This APK isn't compatible with your device."
        android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID -> {
            // VERSION_DOWNGRADE shows up here — system embeds the
            // specific reason in the message; surface it verbatim.
            if (message?.contains("DOWNGRADE", ignoreCase = true) == true) {
                "The release APK is older than the build you're on — Android refuses downgrades. " +
                    "You're already ahead of the published version."
            } else {
                "Android rejected the APK as invalid."
            }
        }
        else -> "The install could not be completed."
    }
    return primary + osLine
}

@Composable
private fun PinnedActions(
    phase: UpdateScreenState.Phase,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        when (phase) {
            is UpdateScreenState.Phase.Available -> {
                GhostButton(label = "Later", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(label = "Update", onClick = onStartDownload)
            }
            is UpdateScreenState.Phase.Downloading -> {
                GhostButton(label = "Cancel", onClick = onCancelDownload)
            }
            is UpdateScreenState.Phase.DownloadFailed -> {
                GhostButton(label = "Dismiss", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(label = "Try again", onClick = onStartDownload)
            }
            is UpdateScreenState.Phase.InstallFailed -> {
                GhostButton(label = "Dismiss", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(label = "Try again", onClick = onStartDownload)
            }
            else -> Unit
        }
    }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = YutoriTheme.colors.onMuted,
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private fun humanBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}
