package com.yutori.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yutori.budget.Budget
import com.yutori.ui.BudgetPromptScreen
import com.yutori.ui.ImportPromptScreen
import com.yutori.ui.OnboardingFlowContext
import com.yutori.ui.PermissionScreen
import com.yutori.ui.WelcomeScreen

/**
 * 4-step first-launch flow (issue #39, mockups/v24-onboarding-flow.html).
 *
 * Step transitions live in [nextStepAfter] so the routing is unit-
 * testable without standing up a Compose harness. The composable
 * itself owns only the runtime state machine + screen wiring.
 *
 * The Import step is skipped when READ_SMS wasn't granted in step 2 —
 * either because the user denied at the OS dialog or hit "Skip for
 * now" before granting. In that case the progress bar collapses to
 * 3 dots so the user doesn't see a permanently-dim phantom step.
 */
@Composable
fun OnboardingFlow(
    monthKey: String,
    hasSmsReadProvider: () -> Boolean,
    onPermissionsAccepted: () -> Unit,
    onStartImport: (sinceMs: Long) -> Unit,
    onSaveBudget: (Budget) -> Unit,
    onComplete: () -> Unit,
) {
    var step: OnboardingStep by remember { mutableStateOf(OnboardingStep.Welcome) }
    var hasReadSms: Boolean by remember { mutableStateOf(false) }

    val totalSteps = if (hasReadSms) FULL_STEPS else FULL_STEPS - 1

    fun advance() {
        val next = nextStepAfter(step, hasReadSms = hasReadSms)
        if (next == null) {
            onComplete()
        } else {
            step = next
        }
    }

    when (step) {
        OnboardingStep.Welcome -> WelcomeScreen(
            stepNumber = 1,
            // Welcome optimistically shows the maximum step count —
            // we don't yet know whether the user will grant READ_SMS.
            stepCount = FULL_STEPS,
            onGetStarted = { advance() },
        )

        OnboardingStep.Permissions -> PermissionScreen(
            onGranted = {
                onPermissionsAccepted()
                hasReadSms = hasSmsReadProvider()
                advance()
            },
            flowContext = OnboardingFlowContext(
                stepNumber = 2,
                stepCount = FULL_STEPS,
                onSkip = {
                    hasReadSms = hasSmsReadProvider()
                    advance()
                },
            ),
        )

        OnboardingStep.Import -> ImportPromptScreen(
            stepNumber = 3,
            stepCount = totalSteps,
            onStartImport = { sinceMs ->
                onStartImport(sinceMs)
                advance()
            },
            onSkip = { advance() },
        )

        OnboardingStep.Budget -> BudgetPromptScreen(
            monthKey = monthKey,
            stepNumber = totalSteps,
            stepCount = totalSteps,
            onSave = { budget ->
                onSaveBudget(budget)
                advance()
            },
            onSkip = { advance() },
        )
    }
}

sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Permissions : OnboardingStep
    data object Import : OnboardingStep
    data object Budget : OnboardingStep
}

/**
 * Pure transition function. Returns the next step, or `null` when the
 * flow is complete. Extracted from the composable so the routing
 * logic can be exercised without a Compose harness.
 *
 *   Welcome      → Permissions (always)
 *   Permissions  → Import      (if READ_SMS granted)
 *                → Budget      (otherwise — skip the import step)
 *   Import       → Budget      (always)
 *   Budget       → null        (flow complete)
 */
fun nextStepAfter(current: OnboardingStep, hasReadSms: Boolean): OnboardingStep? =
    when (current) {
        OnboardingStep.Welcome     -> OnboardingStep.Permissions
        OnboardingStep.Permissions -> if (hasReadSms) OnboardingStep.Import else OnboardingStep.Budget
        OnboardingStep.Import      -> OnboardingStep.Budget
        OnboardingStep.Budget      -> null
    }

private const val FULL_STEPS = 4
