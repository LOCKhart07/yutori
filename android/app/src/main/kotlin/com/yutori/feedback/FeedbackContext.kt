package com.yutori.feedback

import android.os.Build
import com.yutori.BuildConfig
import java.util.Locale

/**
 * Builds the trailer block auto-appended to every feedback body.
 * Strictly allowlisted — no DB data, no transaction metrics, no SMS
 * sender lists. Only fields shown to the user in the compose sheet
 * preview end up in the POST body.
 */
object FeedbackContext {

    /** Compose a fresh context block. Called at Send time. */
    fun render(
        versionName: String = BuildConfig.VERSION_NAME,
        versionCode: Int = BuildConfig.VERSION_CODE,
        commitSha: String = BuildConfig.COMMIT_SHA,
        androidRelease: String = Build.VERSION.RELEASE ?: "",
        sdkInt: Int = Build.VERSION.SDK_INT,
        locale: Locale = Locale.getDefault(),
        deviceModel: String = Build.MODEL ?: "",
        deviceManufacturer: String = Build.MANUFACTURER ?: "",
        deviceProduct: String = Build.PRODUCT ?: "",
    ): String {
        val localeTag = locale.toLanguageTag()
        val deviceLine = buildString {
            append(deviceModel.ifBlank { "unknown" })
            if (deviceManufacturer.isNotBlank() || deviceProduct.isNotBlank()) {
                append(" (")
                append(deviceManufacturer.ifBlank { "?" })
                append("/")
                append(deviceProduct.ifBlank { "?" })
                append(")")
            }
        }
        return buildString {
            appendLine("---")
            appendLine("App:     $versionName ($versionCode) · commit $commitSha")
            appendLine("Android: $androidRelease (API $sdkInt), $localeTag")
            append("Device:  $deviceLine")
        }
    }
}
