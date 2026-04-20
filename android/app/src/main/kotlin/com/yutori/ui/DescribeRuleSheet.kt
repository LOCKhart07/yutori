package com.yutori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.ai.RulePrefill
import com.yutori.ai.ValidationFailure
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Modal bottom sheet that lets the user describe a rule in free text
 * and routes the extracted draft to the nav layer as a [RulePrefill].
 *
 * Four visible states per mockup §C:
 * - Idle: empty or typed input; Extract disabled/enabled.
 * - Extracting: spinner on Extract; input stays visible but locked.
 * - Validation error: single generic banner + Retry.
 * - Model unavailable: warning banner + Close, toggle disables
 *   elsewhere (the ViewModel emits no Event in this state).
 *
 * See `plans/ai-rules-spec.md` §6.3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescribeRuleSheet(
    viewModel: DescribeRuleViewModel,
    seedText: String = "",
    onDismiss: () -> Unit,
    onExtracted: (RulePrefill) -> Unit,
) {
    val colors = YutoriTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(seedText) {
        if (seedText.isNotBlank() && input.isBlank()) {
            viewModel.seed(seedText)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DescribeRuleViewModel.Event.Extracted -> onExtracted(event.prefill)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Describe a rule",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The AI will suggest a merchant pattern and a category. " +
                    "You'll still pick the classification yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("anything from cred is a credit-card bill") },
                enabled = state != DescribeRuleViewModel.SheetState.Extracting,
                minLines = 3,
                maxLines = 5,
            )

            Spacer(Modifier.height(10.dp))

            when (val s = state) {
                DescribeRuleViewModel.SheetState.Idle -> {
                    Text(
                        text = """Examples: "treat cheq as a cc bill", "netflix is entertainment"""",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onFaint,
                    )
                }

                DescribeRuleViewModel.SheetState.Extracting -> {
                    Text(
                        text = "Working on it — usually about 10 seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onMuted,
                    )
                }

                is DescribeRuleViewModel.SheetState.ValidationError -> {
                    ValidationErrorBanner(s.reason)
                }

                is DescribeRuleViewModel.SheetState.ModelUnavailable -> {
                    ModelUnavailableBanner(detail = s.detail)
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetActions(
                state = state,
                inputPresent = input.isNotBlank(),
                onDismiss = onDismiss,
                onExtract = viewModel::onExtract,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActions(
    state: DescribeRuleViewModel.SheetState,
    inputPresent: Boolean,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state) {
            DescribeRuleViewModel.SheetState.Idle -> {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onExtract,
                    enabled = inputPresent,
                    modifier = Modifier.weight(1f),
                ) { Text("Extract") }
            }

            DescribeRuleViewModel.SheetState.Extracting -> {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Extracting…")
                    }
                }
            }

            is DescribeRuleViewModel.SheetState.ValidationError -> {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onExtract, modifier = Modifier.weight(1f)) {
                    Text("Retry")
                }
            }

            is DescribeRuleViewModel.SheetState.ModelUnavailable -> {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun ValidationErrorBanner(reason: ValidationFailure) {
    val colors = YutoriTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "Couldn't extract a rule from that",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = bannerHint(reason),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMuted,
        )
    }
}

@Composable
private fun ModelUnavailableBanner(detail: String?) {
    val colors = YutoriTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "AI is unavailable on this device",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "The on-device model failed to initialise. You can still write rules " +
                "manually from the Recipient rules screen.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMuted,
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onFaint,
            )
        }
    }
}

/**
 * Single user-facing message per spec §4.5 / mockup C3, but the copy
 * nudges toward a fix specific to the failure mode when that's cheap.
 */
private fun bannerHint(reason: ValidationFailure): String = when (reason) {
    ValidationFailure.PARSE_FAILED ->
        "The AI didn't return a usable answer. Try rephrasing — e.g. " +
            "\"treat payments to cred as a credit card bill\"."
    ValidationFailure.PATTERN_MISSING,
    ValidationFailure.PATTERN_TOO_SHORT,
    ValidationFailure.PATTERN_TOO_LONG,
    ValidationFailure.PATTERN_NOT_IN_INPUT ->
        "Try naming a specific merchant or UPI handle — e.g. " +
            "\"treat cred as a credit card bill\"."
    ValidationFailure.CATEGORY_TOO_LONG ->
        "The AI returned an unusually long category. Try a simpler description."
}
