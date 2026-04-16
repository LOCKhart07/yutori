package com.spendwise.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendwise.MainActivity
import com.spendwise.budget.MonthSnapshot
import com.spendwise.ingestion.AlertEvaluation
import com.spendwise.ingestion.AlertNotifier
import com.spendwise.ingestion.ImpactNotification
import com.spendwise.ui.formatAmount
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Real [AlertNotifier] — posts system notifications when a budget
 * threshold is crossed.
 *
 * Channel importance is Low per decision 2026-04-15 #U1 — budget
 * alerts appear silently in the shade rather than interrupting. Users
 * can raise the importance in system settings.
 */
class AndroidAlertNotifier(private val context: Context) : AlertNotifier {

    init {
        ensureChannel(CHANNEL_ID, CHANNEL_NAME, CHANNEL_DESCRIPTION)
        ensureChannel(IMPACT_CHANNEL_ID, IMPACT_CHANNEL_NAME, IMPACT_CHANNEL_DESCRIPTION)
    }

    override fun notify(thresholdPct: Int, evaluation: AlertEvaluation) {
        if (!hasPostNotificationsPermission()) return

        val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val snap = evaluation.snapshot
        val paceDays = computePaceDeltaDays(
            monthKey = snap.monthKey,
            actualPct = snap.percentUsed,
            nowMs = System.currentTimeMillis(),
        )
        val title = buildBudgetAlertTitle(thresholdPct, snap, inr)
        val body = buildBudgetAlertBody(snap, inr, paceDays)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Unique ID per (month, threshold) so re-dispatching won't stack.
        val id = (evaluation.monthKey.hashCode() * 31) + thresholdPct
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager?.getNotificationChannel(id)
            if (existing == null) {
                val channel = NotificationChannel(
                    id, name, NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    this.description = description
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun notifyImpact(impact: ImpactNotification) {
        if (!hasPostNotificationsPermission()) return
        val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        val merchantBit = impact.merchantLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { " at $it" } ?: ""
        val title = "${inr.format(impact.txInrAmount)}$merchantBit"

        // Pace applies only when still under budget — the runway line
        // already reads "· over by ₹X" once remaining < 0 and pace is
        // redundant noise at that point.
        val cumulativePct = if (impact.effectiveBudgetInr > 0.0) {
            100.0 * (impact.effectiveBudgetInr - impact.remainingInr) /
                impact.effectiveBudgetInr
        } else null
        val paceDays = cumulativePct?.let {
            computePaceDeltaDays(
                monthKey = impact.monthKey,
                actualPct = it,
                nowMs = System.currentTimeMillis(),
            )
        }
        val body = buildImpactBody(impact, inr, paceDays)

        val contentIntent = PendingIntent.getActivity(
            context,
            impact.transactionId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, IMPACT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Unique per tx — multiple impacts coexist instead of overwriting.
        val id = IMPACT_NOTIFICATION_ID_BASE + impact.transactionId.toInt()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
        private const val CHANNEL_NAME = "Budget alerts"
        private const val CHANNEL_DESCRIPTION =
            "Alerts when monthly spend crosses a threshold."

        const val IMPACT_CHANNEL_ID = "impact_alerts"
        private const val IMPACT_CHANNEL_NAME = "Big-spend alerts"
        private const val IMPACT_CHANNEL_DESCRIPTION =
            "Notifies when a single transaction is a meaningful chunk of your monthly budget."

        // Offset to keep impact IDs from colliding with cumulative-alert IDs.
        private const val IMPACT_NOTIFICATION_ID_BASE = 1_000_000
    }
}

/**
 * Budget-alert notification title.
 *
 * For over-budget thresholds (pct ≥ 100) the overshoot amount is
 * promoted into the title so the collapsed notification shade — which
 * truncates contentText around ~35 characters and loses the body's
 * `· ₹X over` suffix — still shows the actionable "how bad" info. See
 * #86 for the screenshots that motivated this.
 *
 * Compact-formatted (no paise) so titles stay scannable; if the rounded
 * overshoot is ₹0 (threshold fires at the exact boundary) we fall back
 * to the bare "Over limit" / "$pct% of limit" copy so we don't post a
 * misleading "Over by ₹0".
 *
 * Top-level so unit tests can exercise it without Robolectric.
 */
internal fun buildBudgetAlertTitle(
    pct: Int,
    snap: MonthSnapshot,
    inr: NumberFormat,
): String = when {
    pct <= 50 -> "Budget: Half used"
    pct < 100 -> "Budget: Approaching limit"
    else -> {
        val over = (snap.netSpendInr - snap.effectiveBudgetInr).coerceAtLeast(0.0)
        val overIsZero = kotlin.math.round(over).toLong() == 0L
        when {
            overIsZero && pct == 100 -> "Budget: Over limit"
            overIsZero -> "Budget: $pct% of limit"
            pct == 100 -> "Budget: Over by ${inr.formatAmount(over, compact = true)}"
            else -> "Budget: $pct% · ${inr.formatAmount(over, compact = true)} over"
        }
    }
}

/**
 * Budget-alert notification body. Expanded (BigText) view shows the
 * full string; the collapsed row truncates at ~35 chars and relies on
 * the title (above) for actionable info.
 *
 * `paceDelta` is the output of [computePaceDeltaPp] — when present and
 * noticeably off-pace (|Δ| > 5pp), it's appended as a `· +Xpp over pace`
 * suffix so the user knows whether the threshold was crossed early
 * ("got here too fast") or late ("on schedule for the month"). See #17.
 */
internal fun buildBudgetAlertBody(
    snap: MonthSnapshot,
    inr: NumberFormat,
    paceDelta: Int?,
): String = buildString {
    append("Spent ${inr.format(snap.netSpendInr)} of ${inr.format(snap.effectiveBudgetInr)}")
    val remaining = snap.effectiveBudgetInr - snap.netSpendInr
    if (remaining >= 0) append(" · ${inr.format(remaining)} remaining")
    else append(" · ${inr.format(-remaining)} over")
    append(paceSuffix(paceDelta))
}

/**
 * Impact-notification body. Leads with the single-tx percent of the
 * month's budget, then remaining runway. When the user is still under
 * budget (`remainingInr ≥ 0`) a pace suffix is appended so the alert
 * doubles as a pacing check — "this one tx was 12% of your budget AND
 * you're already running +15pp over pace" is much sharper than the
 * per-tx percent alone. When already over budget, the runway line
 * already reads "· over by ₹X" so pace is redundant noise.
 */
internal fun buildImpactBody(
    impact: ImpactNotification,
    inr: NumberFormat,
    paceDelta: Int?,
): String {
    val pctRounded = kotlin.math.round(impact.percentOfBudget).toInt()
    val daysLeftBit = if (impact.daysLeft > 0) " for ${impact.daysLeft} days" else ""
    return if (impact.remainingInr >= 0) {
        "$pctRounded% of this month · ${inr.format(impact.remainingInr)} left$daysLeftBit" +
            paceSuffix(paceDelta)
    } else {
        "$pctRounded% of this month · over by ${inr.format(-impact.remainingInr)}"
    }
}

/**
 * Pace delta expressed in calendar days — the number of days' worth of
 * budget the user is ahead of ("over pace") or behind ("under pace") a
 * perfectly-proportional burn. Positive = spending fast, negative =
 * spending slow. Returns null when the month isn't the current one
 * (past/future months have no "pace") or when `monthKey` won't parse.
 *
 * Days are reader-friendly — an end-user who wouldn't know what "pp"
 * means still groks "12 days over pace." The pp-precision version is
 * tracked separately for a future detailed/analysis surface.
 *
 * Formula:
 *   expected_pct = 100 × dayOfMonth / daysInMonth
 *   delta_pp     = actualPct − expected_pct
 *   delta_days   = delta_pp × daysInMonth / 100
 */
internal fun computePaceDeltaDays(
    monthKey: String,
    actualPct: Double,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Int? {
    val ym = runCatching { YearMonth.parse(monthKey) }.getOrNull() ?: return null
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    if (today.year != ym.year || today.monthValue != ym.monthValue) return null
    val daysInMonth = ym.lengthOfMonth()
    val expected = 100.0 * today.dayOfMonth / daysInMonth
    val deltaDays = (actualPct - expected) * daysInMonth / 100.0
    return kotlin.math.round(deltaDays).toInt()
}

/**
 * Formats a pace-delta (days) as a suffix for the notification body.
 * Hides the suffix when the delta is null or within ±2 days — day-to-
 * day fluctuations aren't actionable and pinning the deadband at 2
 * sidesteps the "1 days" grammar-error case.
 */
internal fun paceSuffix(deltaDays: Int?): String = when {
    deltaDays == null -> ""
    deltaDays >= 2 -> " · $deltaDays days over pace"
    deltaDays <= -2 -> " · ${-deltaDays} days under pace"
    else -> ""
}
