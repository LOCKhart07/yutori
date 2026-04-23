package com.yutori.onboarding

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pure-function checks on [nextStepAfter] — the heart of the flow's
 * routing. The composable wires this into runtime state, but the
 * branching logic itself shouldn't need a Compose harness to verify.
 *
 * Spec source: plans/ui-spec.md §4 (Import only when READ_SMS granted).
 */
class OnboardingFlowRoutingTest {

    @Test
    fun `welcome always advances to permissions, regardless of READ_SMS state`() {
        nextStepAfter(OnboardingStep.Welcome, hasReadSms = false) shouldBe OnboardingStep.Permissions
        nextStepAfter(OnboardingStep.Welcome, hasReadSms = true) shouldBe OnboardingStep.Permissions
    }

    @Test
    fun `permissions advances to import when READ_SMS was granted`() {
        nextStepAfter(OnboardingStep.Permissions, hasReadSms = true) shouldBe OnboardingStep.Import
    }

    @Test
    fun `permissions skips Import when READ_SMS was denied or skipped`() {
        // The whole point of the conditional step — without READ_SMS
        // there's nothing to import, so the flow drops the user
        // straight on Budget.
        nextStepAfter(OnboardingStep.Permissions, hasReadSms = false) shouldBe OnboardingStep.Budget
    }

    @Test
    fun `import always advances to budget`() {
        nextStepAfter(OnboardingStep.Import, hasReadSms = true) shouldBe OnboardingStep.Budget
        // hasReadSms shouldn't matter once the user is past Permissions —
        // they only reach Import if READ_SMS was granted in the first
        // place — but the function should be defensive about it.
        nextStepAfter(OnboardingStep.Import, hasReadSms = false) shouldBe OnboardingStep.Budget
    }

    @Test
    fun `budget completes the flow -- no further step`() {
        nextStepAfter(OnboardingStep.Budget, hasReadSms = true).shouldBeNull()
        nextStepAfter(OnboardingStep.Budget, hasReadSms = false).shouldBeNull()
    }
}
