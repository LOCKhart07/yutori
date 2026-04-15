package com.spendwise.ingestion

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SmsInboxLookup] backed by Android's `content://sms/inbox`.
 *
 * Requires `READ_SMS` — if missing, the query throws and we return
 * null (reconciler treats that as "can't resolve, leave alone").
 */
class ContentProviderSmsInboxLookup(
    private val resolver: ContentResolver,
) : SmsInboxLookup {

    override suspend fun findId(
        sender: String,
        body: String,
        receivedAtMs: Long,
        toleranceMs: Long,
    ): Long? = withContext(Dispatchers.IO) {
        val lo = receivedAtMs - toleranceMs
        val hi = receivedAtMs + toleranceMs
        try {
            resolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.ADDRESS} = ? AND " +
                    "${Telephony.Sms.BODY} = ? AND " +
                    "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(sender, body, lo.toString(), hi.toString()),
                null,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS denied during reconciler lookup", e)
            null
        }?.use { c ->
            if (c.count != 1) null else {
                c.moveToFirst()
                c.getLong(0)
            }
        }
    }

    companion object {
        private const val TAG = "ContentProviderSmsInboxLookup"
    }
}
