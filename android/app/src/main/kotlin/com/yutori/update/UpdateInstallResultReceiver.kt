package com.yutori.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

// Passive observer for PackageInstaller session outcomes. Logging only;
// the system's own install/confirmation UI is what the user sees.
class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive: action=${intent.action} extras=${intent.extras?.keySet()}")
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "onReceive: status=$status message=$message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System needs the user to confirm via its install UI.
                // The Intent to launch is stashed in EXTRA_INTENT.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                Log.i(TAG, "pending user action — launching confirm=${confirm?.component}")
                if (confirm == null) {
                    Log.e(TAG, "no EXTRA_INTENT on pending_user_action — treating as failure")
                    UpdateInstallEvents.publish(
                        UpdateInstallEvents.Outcome.Failure(status, "no confirm intent"),
                    )
                    return
                }
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                } catch (t: Throwable) {
                    Log.e(TAG, "startActivity for confirm threw", t)
                    UpdateInstallEvents.publish(
                        UpdateInstallEvents.Outcome.Failure(status, t.message),
                    )
                }
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
        const val ACTION_INSTALL_RESULT = "com.yutori.update.INSTALL_RESULT"
        private const val TAG = "YutoriUpdater"
    }
}
