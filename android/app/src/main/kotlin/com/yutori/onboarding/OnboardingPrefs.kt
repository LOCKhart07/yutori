package com.yutori.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * One-bit "has the user finished the first-launch flow?" store. The
 * spec contract (`plans/ui-spec.md` §4.2) requires onboarding to never
 * re-fire after completion unless app data is cleared, so completion
 * needs persistence outside the permission grant (which can be revoked
 * later without re-triggering Welcome / Import / Budget).
 *
 * Backfill: existing installs already past the original
 * permission-only gate are treated as onboarded — see
 * [backfillIfPreviouslyGranted].
 */
class OnboardingPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun completedAt(): Long = prefs.getLong(KEY_COMPLETED_AT, 0L)

    fun isCompleted(): Boolean = completedAt() > 0L

    fun markCompleted(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_COMPLETED_AT, nowMs).apply()
    }

    /**
     * Existing installs that already granted the realtime SMS
     * permission predate this flag — treat them as onboarded so the
     * Welcome / Import / Budget steps never appear retroactively. Safe
     * to call repeatedly; no-op once the flag is set.
     */
    fun backfillIfPreviouslyGranted(alreadyGranted: Boolean) {
        if (!isCompleted() && alreadyGranted) markCompleted()
    }

    private companion object {
        const val FILE_NAME = "yutori_onboarding"
        const val KEY_COMPLETED_AT = "completed_at"
    }
}
