package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.classifier.AccountKind
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

data class AccountDraft(
    val id: Long,
    val kind: AccountKind,
    val issuer: String,
    /**
     * Blank / null means UPI-only (no card component). Identity for
     * those flows through [upiHandles] via recipient_rules. See
     * issue #6.
     */
    val last4: String?,
    val displayName: String?,
    val isDefaultSpend: Boolean,
    val upiHandles: List<String>,
)

@Composable
fun AccountEditScreen(
    initial: AccountDraft?,
    onSave: (AccountDraft) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var kind by remember { mutableStateOf(initial?.kind ?: AccountKind.SAVINGS) }
    var issuer by remember { mutableStateOf(initial?.issuer.orEmpty()) }
    var last4 by remember { mutableStateOf(initial?.last4.orEmpty()) }
    var displayName by remember { mutableStateOf(initial?.displayName.orEmpty()) }
    var isDefault by remember { mutableStateOf(initial?.isDefaultSpend ?: false) }
    val upiHandles = remember {
        mutableStateListOf<String>().apply { initial?.upiHandles?.let { addAll(it) } }
    }
    var newHandle by remember { mutableStateOf("") }

    // last4 is now optional — blank means "UPI-only" (issue #6). When
    // present it must still match the 4–6 alphanumeric shape.
    val last4Trimmed = last4.trim()
    val last4Blank = last4Trimmed.isEmpty()
    val last4Ok = last4Blank ||
        last4Trimmed.matches(Regex("""^[A-Za-z0-9]{4,6}$"""))
    val issuerOk = issuer.isNotBlank()
    // Must have at least one identifier so the account has *something*
    // to route transactions by — either a last-4 or a UPI handle.
    val hasIdentifier = !last4Blank || upiHandles.isNotEmpty()
    val saveEnabled = last4Ok && issuerOk && hasIdentifier

    val colors = YutoriTheme.colors

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
                BackRow(label = "Cancel", onBack = onCancel)
                Spacer(Modifier.height(16.dp))

                Text(
                    text = (if (initial == null) "NEW ACCOUNT" else "EDIT ACCOUNT"),
                    style = YutoriTextStyles.Caps,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (initial == null) "Add an account" else "Edit account",
                    style = MaterialTheme.typography.headlineLarge,
                )

                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                // Type chips
                SectionLabel("Type")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountKind.entries.forEach { k ->
                        TypeChip(
                            label = prettyKindLabel(k),
                            selected = k == kind,
                            onClick = { kind = k },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                SectionLabel("Issuer")
                Spacer(Modifier.height(8.dp))
                ThemedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    placeholder = "Kotak, Axis, ICICI…",
                )

                Spacer(Modifier.height(16.dp))

                SectionLabel("Last 4–6 digits (optional)")
                Spacer(Modifier.height(8.dp))
                ThemedTextField(
                    value = last4,
                    onValueChange = { new ->
                        if (new.length <= 6 && new.matches(Regex("""[A-Za-z0-9]*"""))) {
                            last4 = new
                        }
                    },
                    placeholder = "0000 or XX0000 (blank for UPI-only)",
                    mono = true,
                    error = last4.isNotEmpty() && !last4Ok,
                )
                if (last4.isNotEmpty() && !last4Ok) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "4–6 alphanumeric characters, or leave blank.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.negative,
                    )
                } else if (last4Blank && upiHandles.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add a last-4 or at least one UPI handle so we can " +
                            "route transactions to this account.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onMuted,
                    )
                }

                Spacer(Modifier.height(16.dp))

                SectionLabel("Display name (optional)")
                Spacer(Modifier.height(8.dp))
                ThemedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "e.g. Kotak Savings",
                )

                Spacer(Modifier.height(20.dp))

                // Default-spend switch
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Default spend account",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Used when an SMS doesn't name a specific account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onMuted,
                            )
                        }
                        Switch(
                            checked = isDefault,
                            onCheckedChange = { isDefault = it },
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

                Spacer(Modifier.height(24.dp))

                // UPI handles
                SectionLabel("UPI handles")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Any UPI VPA that sends to this account (e.g. " +
                        "examplename-4@oksbi). Transfers here will be " +
                        "marked as self-transfers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(10.dp))

                upiHandles.forEach { handle ->
                    HandleRow(
                        handle = handle,
                        onRemove = { upiHandles.remove(handle) },
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemedTextField(
                        value = newHandle,
                        onValueChange = { newHandle = it },
                        placeholder = "Add UPI handle",
                        mono = true,
                        modifier = Modifier.weight(1f),
                    )
                    val addEnabled = newHandle.trim().isNotEmpty()
                    SecondaryButton(
                        text = "Add",
                        onClick = {
                            val h = newHandle.trim()
                            if (h.isNotEmpty() && h !in upiHandles) upiHandles.add(h)
                            newHandle = ""
                        },
                        modifier = Modifier.size(width = 96.dp, height = 54.dp),
                    )
                    // Note: SecondaryButton doesn't take enabled arg — v1.1 nicety.
                    if (!addEnabled) { /* keeps compile clean */ }
                }

                Spacer(Modifier.height(28.dp))

                // Footer actions
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryActionButton(
                        text = "Save",
                        enabled = saveEnabled,
                        onClick = {
                            onSave(
                                AccountDraft(
                                    id = initial?.id ?: 0,
                                    kind = kind,
                                    issuer = issuer.trim(),
                                    last4 = last4.trim().ifBlank { null },
                                    displayName = displayName.trim().ifBlank { null },
                                    isDefaultSpend = isDefault,
                                    upiHandles = upiHandles.toList(),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Delete account",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.negative,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDelete)
                            .padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ──────────── helper composables ────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = YutoriTextStyles.Caps,
        color = YutoriTheme.colors.onFaint,
    )
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YutoriTheme.colors
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.15f) else colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) accent else colors.divider,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) accent else colors.onMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false,
    error: Boolean = false,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HandleRow(handle: String, onRemove: () -> Unit) {
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = handle,
            style = YutoriTextStyles.Mono,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Remove",
            style = MaterialTheme.typography.labelMedium,
            color = colors.negative,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun prettyKindLabel(kind: AccountKind): String = when (kind) {
    AccountKind.SAVINGS -> "Savings"
    AccountKind.CREDIT_CARD -> "Credit"
    AccountKind.INVESTMENT -> "Invest"
    AccountKind.OTHER -> "Other"
}
