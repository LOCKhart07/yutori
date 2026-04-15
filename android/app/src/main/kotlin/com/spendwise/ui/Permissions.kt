package com.spendwise.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Permissions the ingestion + alert layers need. */
object Permissions {

    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
    )

    /** True when both RECEIVE_SMS and READ_SMS are granted. */
    fun hasSmsPermissions(context: Context): Boolean =
        SMS_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * True when at least RECEIVE_SMS is granted. Historical import
     * additionally needs READ_SMS but real-time capture works with
     * just RECEIVE.
     */
    fun hasRealtimePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * The runtime-grantable permissions this app needs: SMS (always)
     * plus POST_NOTIFICATIONS on Android 13+. Older APIs auto-grant
     * notification posting at install time.
     */
    fun runtimePermissionsToRequest(): Array<String> {
        val list = SMS_PERMISSIONS.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }
}
