package com.spendwise.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-controlled config for the per-transaction "impact" notification
 * (mockup `mockups/v3-behavioral.html` §2). Lives in SharedPreferences
 * because there's no other typed config store yet and adding a Room
 * settings table for two scalars would be overkill.
 *
 * Defaults: OFF / 10%. Spec calls for opt-in to avoid first-install
 * notification spam.
 */
class ImpactAlertSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(currentConfig())
    val state: StateFlow<Config> = _state.asStateFlow()

    fun get(): Config = _state.value

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setThresholdPct(pct: Int) {
        val clamped = pct.coerceIn(MIN_PCT, MAX_PCT)
        prefs.edit().putInt(KEY_THRESHOLD_PCT, clamped).apply()
        _state.value = _state.value.copy(thresholdPct = clamped)
    }

    private fun currentConfig() = Config(
        enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
        thresholdPct = prefs.getInt(KEY_THRESHOLD_PCT, DEFAULT_THRESHOLD_PCT),
    )

    data class Config(
        val enabled: Boolean,
        val thresholdPct: Int,
    )

    companion object {
        private const val PREFS_NAME = "spendwise_impact_alerts"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_THRESHOLD_PCT = "threshold_pct"
        private const val DEFAULT_ENABLED = false
        private const val DEFAULT_THRESHOLD_PCT = 10
        const val MIN_PCT = 1
        const val MAX_PCT = 50
    }
}
