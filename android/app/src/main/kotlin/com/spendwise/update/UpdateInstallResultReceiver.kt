package com.spendwise.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

// Passive observer for PackageInstaller session outcomes. Logging only;
// the system's own install/confirmation UI is what the user sees.
class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System needs the user to confirm via its install UI.
                // The Intent to launch is stashed in EXTRA_INTENT.
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.also { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "install succeeded")
                UpdateInstallEvents.publish(UpdateInstallEvents.Outcome.Success)
            }
            else -> {
                Log.w(TAG, "install failed: status=$status, message=$message")
                UpdateInstallEvents.publish(UpdateInstallEvents.Outcome.Failure(status, message))
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.spendwise.update.INSTALL_RESULT"
        private const val TAG = "UpdateInstall"
    }
}
