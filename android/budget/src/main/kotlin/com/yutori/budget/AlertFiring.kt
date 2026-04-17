package com.yutori.budget

/**
 * Records that a given [thresholdPct] has fired for [monthKey].
 * Mirrors schema.md `budget_alert_state`.
 *
 * The state-machine contract: once a threshold is present in the
 * "already fired" set for a month, it is never fired again, even if
 * spend later drops below it (§7.5).
 */
data class AlertFiring(
    val monthKey: String,
    val thresholdPct: Int,
)
