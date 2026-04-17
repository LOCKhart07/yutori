package com.spendwise.update

import android.content.Context
import android.content.SharedPreferences

class UpdatePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()

    var checkOnOpenEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_ON_OPEN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_ON_OPEN_ENABLED, value).apply()

    var dismissedTag: String?
        get() = prefs.getString(KEY_DISMISSED_TAG, null)
        set(value) {
            prefs.edit().run {
                if (value == null) remove(KEY_DISMISSED_TAG) else putString(KEY_DISMISSED_TAG, value)
                apply()
            }
        }

    // Used to clear `dismissedTag` when the installed version changes
    // (e.g. user installed manually over a dismissed update) — otherwise
    // an old "Later" tap would suppress the dialog forever. See spec §10.
    var lastSeenVersionName: String?
        get() = prefs.getString(KEY_LAST_SEEN_VERSION_NAME, null)
        set(value) {
            prefs.edit().run {
                if (value == null) remove(KEY_LAST_SEEN_VERSION_NAME) else putString(KEY_LAST_SEEN_VERSION_NAME, value)
                apply()
            }
        }

    companion object {
        private const val FILE_NAME = "autoupdater_prefs"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_CHECK_ON_OPEN_ENABLED = "check_on_open_enabled"
        private const val KEY_DISMISSED_TAG = "dismissed_tag"
        private const val KEY_LAST_SEEN_VERSION_NAME = "last_seen_version_name"
    }
}
