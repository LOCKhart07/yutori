package com.spendwise.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives `android.provider.Telephony.SMS_RECEIVED` broadcasts and
 * hands off to the [IngestionPipeline] via [IngestionCoordinator].
 *
 * The receiver itself is intentionally minimal — per ingestion-spec
 * §5.1, its contract is "receive → dispatch → return in <5 ms." All
 * DB work happens on a background scope.
 *
 * Registration is in this library's `AndroidManifest.xml`; AGP merges
 * it into the app manifest at build time.
 */
class SpendWiseSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract SMS messages from intent", e)
            return
        }
        if (messages.isNullOrEmpty()) return

        // Multipart SMSes are delivered as multiple SmsMessage objects
        // with the same originating address. Concatenate bodies in
        // order (the Telephony API already returns them in order).
        val senderToRaw: MutableMap<String, Pair<StringBuilder, Long>> = mutableMapOf()
        for (sms in messages) {
            val address = sms.displayOriginatingAddress ?: continue
            val body = sms.displayMessageBody ?: continue
            val ts = sms.timestampMillis
            val existing = senderToRaw[address]
            if (existing == null) {
                senderToRaw[address] = StringBuilder(body) to ts
            } else {
                existing.first.append(body)
            }
        }

        val coordinator = IngestionCoordinator.instance
        if (coordinator == null) {
            Log.w(TAG, "IngestionCoordinator not initialized; dropping SMS")
            return
        }

        for ((sender, bodyAndTs) in senderToRaw) {
            val raw = RawSms(
                androidSmsId = null,    // filled in by reconciler — §5.4
                sender = sender,
                body = bodyAndTs.first.toString(),
                receivedAtMs = bodyAndTs.second,
                source = SmsSource.SMS_REALTIME,
            )
            coordinator.scheduleIngest(raw)
        }
    }

    companion object {
        private const val TAG = "SpendWiseSmsReceiver"
    }
}

/**
 * Holder for the app-wide [IngestionPipeline] so the receiver can find
 * it without dependency injection. The [SpendWiseApp] sets
 * [instance] on process start.
 *
 * This is a service-locator pattern. It works for v1 MVP; we can
 * migrate to Hilt later without changing the receiver.
 */
class IngestionCoordinator(
    private val pipeline: IngestionPipeline,
    private val alertNotifier: AlertNotifier? = null,
    /**
     * Called after every successfully-ingested transaction (CreatedNew
     * or MergedInto). The :app wires this to fire a one-shot
     * [com.spendwise.forex.ForexConversionWorker] so new pending-forex
     * rows get resolved promptly instead of waiting for the periodic
     * (6h) worker or the next app launch.
     *
     * Kept module-agnostic — this interface holds no Android imports.
     */
    private val onTransactionIngested: (() -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    ),
) {

    /** Fire-and-forget background ingest. Used by the broadcast receiver. */
    fun scheduleIngest(raw: RawSms) {
        scope.launch {
            try {
                ingestAndNotify(raw)
            } catch (e: Throwable) {
                Log.e(TAG, "Ingestion failed for SMS from ${raw.sender}", e)
            }
        }
    }

    /**
     * Suspend entry point — awaits the ingest, dispatches alerts, and
     * returns the outcome. Used by the historical-import worker, which
     * needs to await per-SMS completion for progress updates.
     */
    suspend fun ingestAndNotify(raw: RawSms): IngestionOutcome {
        val outcome = pipeline.ingest(raw)
        dispatchAlertsIfNeeded(outcome)
        notifyTransactionIngestedIfNeeded(outcome)
        return outcome
    }

    private fun notifyTransactionIngestedIfNeeded(outcome: IngestionOutcome) {
        if (outcome !is IngestionOutcome.Ingested) return
        val decision = outcome.transactionDecision ?: return
        val isTxEvent = decision is com.spendwise.transactions.MergeDecision.CreatedNew ||
            decision is com.spendwise.transactions.MergeDecision.MergedInto
        if (isTxEvent) {
            try {
                onTransactionIngested?.invoke()
            } catch (e: Throwable) {
                Log.e(TAG, "onTransactionIngested callback failed", e)
            }
        }
    }

    private fun dispatchAlertsIfNeeded(outcome: IngestionOutcome) {
        if (outcome !is IngestionOutcome.Ingested) return
        val notifier = alertNotifier
        // Cumulative threshold alerts.
        outcome.alertEvaluation?.let { eval ->
            if (eval.isCurrentMonth && notifier != null) {
                for (pct in eval.newlyFired) {
                    try {
                        notifier.notify(pct, eval)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Alert dispatch failed for pct=$pct", e)
                    }
                }
            }
        }
        // Per-tx impact alert (independent of cumulative).
        outcome.impactNotification?.let { impact ->
            if (notifier != null) {
                try {
                    notifier.notifyImpact(impact)
                } catch (e: Throwable) {
                    Log.e(TAG, "Impact dispatch failed for tx=${impact.transactionId}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "IngestionCoordinator"

        /** Global instance — set by [SpendWiseApp.onCreate]. */
        @Volatile
        var instance: IngestionCoordinator? = null
    }
}
