package com.yutori.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed store for the three AI toggle/model keys
 * from `plans/ai-rules-spec.md` §3.4.
 *
 * All writes go through this class; every caller (settings screen,
 * model-download worker) talks to the same instance. That lets us use
 * a simple in-memory `MutableStateFlow` for observability — no need
 * for a `callbackFlow` over `OnSharedPreferenceChangeListener` because
 * there are no external writers.
 *
 * Lifetime: single instance held by `YutoriApp.aiSettingsRepository`
 * (see patterns used by `ImpactAlertSettings`).
 */
class AiSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<State> = _state.asStateFlow()

    fun get(): State = _state.value

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    /** Called by the download worker on successful SHA-256 verify. */
    fun markModelInstalled(sha256: String, timeMs: Long) {
        prefs.edit()
            .putString(KEY_SHA, sha256)
            .putLong(KEY_TIME, timeMs)
            .apply()
        _state.value = _state.value.copy(
            modelSha256 = sha256,
            modelInstallTimeMs = timeMs,
        )
    }

    /** Called when the user taps "Delete model" in Settings. */
    fun clearModel() {
        prefs.edit()
            .remove(KEY_SHA)
            .remove(KEY_TIME)
            .apply()
        _state.value = _state.value.copy(
            modelSha256 = null,
            modelInstallTimeMs = null,
        )
    }

    private fun readState() = State(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        modelSha256 = prefs.getString(KEY_SHA, null),
        modelInstallTimeMs = prefs.getLong(KEY_TIME, -1L).takeIf { it > 0 },
    )

    /** Snapshot of the three keys. */
    data class State(
        val enabled: Boolean,
        val modelSha256: String?,
        val modelInstallTimeMs: Long?,
    ) {
        val modelInstalled: Boolean get() = modelSha256 != null
    }

    companion object {
        private const val PREFS_NAME = "yutori_ai_settings"
        private const val KEY_ENABLED = "ai_rules_enabled"
        private const val KEY_SHA = "ai_model_installed_sha256"
        private const val KEY_TIME = "ai_model_install_time_ms"
    }
}
