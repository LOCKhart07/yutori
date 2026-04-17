package com.yutori.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import java.io.File

class UpdateInstaller(private val context: Context) {
    fun install(apk: File) {
        Log.i(TAG, "install: starting — apk=${apk.name} size=${apk.length()} exists=${apk.exists()}")
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            Log.i(TAG, "install: session created id=$sessionId")
            val session = packageInstaller.openSession(sessionId)
            try {
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                Log.i(TAG, "install: bytes written, committing")
                session.commit(buildResultIntent(sessionId).intentSender)
                Log.i(TAG, "install: commit returned — waiting for result broadcast")
            } finally {
                session.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "install: threw synchronously", t)
            UpdateInstallEvents.publish(
                UpdateInstallEvents.Outcome.Failure(status = -9, message = t.message),
            )
        }
    }

    private fun buildResultIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallResultReceiver::class.java).apply {
            action = UpdateInstallResultReceiver.ACTION_INSTALL_RESULT
        }
        // FLAG_MUTABLE is required — the installer writes the session's
        // extras (EXTRA_STATUS, EXTRA_STATUS_MESSAGE) into the intent
        // before firing. API 31+ enforces this flag explicitly.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    companion object {
        private const val TAG = "YutoriUpdater"
    }
}
