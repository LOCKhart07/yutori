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
import com.spendwise.ingestion.AlertEvaluation
import com.spendwise.ingestion.AlertNotifier
import java.text.NumberFormat
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
        ensureChannel()
    }

    override fun notify(thresholdPct: Int, evaluation: AlertEvaluation) {
        if (!hasPostNotificationsPermission()) return

        val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val snap = evaluation.snapshot
        val title = titleFor(thresholdPct)
        val body = bodyFor(thresholdPct, snap, inr)

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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = CHANNEL_DESCRIPTION
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

    private fun titleFor(pct: Int): String = when {
        pct <= 50 -> "Budget: Half used"
        pct < 100 -> "Budget: Approaching limit"
        pct == 100 -> "Budget: Over limit"
        else -> "Budget: $pct% of limit"
    }

    private fun bodyFor(
        pct: Int,
        snap: com.spendwise.budget.MonthSnapshot,
        inr: NumberFormat,
    ): String = buildString {
        append("Spent ${inr.format(snap.netSpendInr)} of ${inr.format(snap.effectiveBudgetInr)}")
        val remaining = snap.effectiveBudgetInr - snap.netSpendInr
        if (remaining >= 0) {
            append(" · ${inr.format(remaining)} remaining")
        } else {
            append(" · ${inr.format(-remaining)} over")
        }
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
        private const val CHANNEL_NAME = "Budget alerts"
        private const val CHANNEL_DESCRIPTION =
            "Alerts when monthly spend crosses a threshold."
    }
}
