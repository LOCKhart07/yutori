package com.yutori.ui.feedback

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.feedback.FeedbackContext
import com.yutori.feedback.FeedbackViewModel
import com.yutori.feedback.Phase
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

/**
 * Settings → Send feedback sheet (#113). Full-screen compose UI —
 * title + description + read-only context preview. Submit flows
 * through [FeedbackViewModel] to the GitHub Issues API.
 *
 * On success the sheet flips to a confirmation state with "View
 * issue" / "Done" — the parent never has to host a snackbar. On
 * failure an inline error row appears above the context card with a
 * Retry action; typed content stays intact.
 */
@Composable
fun SendFeedbackScreen(
    vm: FeedbackViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding() + 8.dp)
                .imePadding(),
        ) {
            TopBar(
                canSend = state.canSend,
                sending = state.phase is Phase.Sending,
                onClose = onClose,
                onSend = { vm.submit() },
            )
            HorizontalDivider(color = YutoriTheme.colors.divider)

            when (val phase = state.phase) {
                is Phase.Sent -> SentConfirmation(
                    number = phase.number,
                    onView = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(phase.htmlUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    onDone = {
                        vm.reset()
                        onClose()
                    },
                )
                else -> ComposeBody(
                    title = state.title,
                    description = state.description,
                    errorMessage = (phase as? Phase.Failed)?.message,
                    onTitleChange = vm::setTitle,
                    onDescriptionChange = vm::setDescription,
                    onRetry = { vm.submit() },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    canSend: Boolean,
    sending: Boolean,
    onClose: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = YutoriTheme.colors.onMuted,
        )
        Spacer(Modifier.fillMaxWidth(0.07f))
        Text(
            text = "Send feedback",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 18.dp, max = 18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "Send",
                modifier = Modifier
                    .clickable(enabled = canSend, onClick = onSend)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    YutoriTheme.colors.onFaint
                },
            )
        }
    }
}

@Composable
private fun ComposeBody(
    title: String,
    description: String,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(),
        )
        Spacer(Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            minLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            colors = textFieldColors(),
        )
        if (errorMessage != null) {
            Spacer(Modifier.padding(vertical = 8.dp))
            ErrorRow(message = errorMessage, onRetry = onRetry)
        }
        Spacer(Modifier.padding(vertical = 12.dp))
        ContextPreview()
        Spacer(Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun ContextPreview() {
    val previewText = remember { FeedbackContext.render() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(YutoriTheme.colors.surfaceElevated)
            .border(
                width = 1.dp,
                color = YutoriTheme.colors.divider,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "INCLUDED AUTOMATICALLY (no personal data)",
            style = YutoriTextStyles.Caps,
            color = YutoriTheme.colors.onFaint,
        )
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = YutoriTheme.colors.onMuted,
        )
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(YutoriTheme.colors.negative.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = YutoriTheme.colors.negative.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = YutoriTheme.colors.negative,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = "Retry",
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SentConfirmation(
    number: Int,
    onView: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayMedium,
            color = YutoriTheme.colors.positive,
        )
        Spacer(Modifier.padding(vertical = 10.dp))
        Text(
            text = "Thanks — sent.",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.padding(vertical = 6.dp))
        Text(
            text = "Issue #$number opened.",
            style = MaterialTheme.typography.bodyMedium,
            color = YutoriTheme.colors.onMuted,
        )
        Spacer(Modifier.padding(vertical = 24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "View issue",
                modifier = Modifier
                    .clickable(onClick = onView)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = YutoriTheme.colors.onFaint,
                )
            }
            Text(
                text = "Done",
                modifier = Modifier
                    .clickable(onClick = onDone)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = YutoriTheme.colors.onMuted,
            )
        }
    }
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = YutoriTheme.colors.divider,
    focusedLabelColor = YutoriTheme.colors.onMuted,
    unfocusedLabelColor = YutoriTheme.colors.onFaint,
)
