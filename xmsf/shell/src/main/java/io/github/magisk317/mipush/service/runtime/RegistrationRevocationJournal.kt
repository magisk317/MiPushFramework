package io.github.magisk317.mipush.service

import android.content.Context
import io.github.magisk317.xposed.logging.XLog

/** Only unfinished local revocation work lives here; server registration state stays in its registry. */
internal object RegistrationRevocationJournal {
    private const val PREFERENCES = "pref_pending_registration_revocation"

    fun pendingAppId(context: Context, packageName: String, userId: Int): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(key(packageName, userId), null)

    fun hasPending(context: Context, packageName: String, userId: Int): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).contains(key(packageName, userId))

    fun begin(context: Context, packageName: String, userId: Int, appId: String): Boolean {
        require(appId.isNotBlank())
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key(packageName, userId), appId).commit()
    }

    fun complete(context: Context, packageName: String, userId: Int): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = key(packageName, userId)
        val appId = preferences.getString(key, null) ?: return true
        if (preferences.edit().remove(key).commit()) return true
        // SharedPreferences updates its in-memory map even when the disk write fails.
        // Restore the fence immediately so the same process cannot admit a new registration.
        if (!preferences.edit().putString(key, appId).commit()) {
            XLog.w("Unable to persist the pending registration revocation fence")
        }
        return false
    }

    private fun key(packageName: String, userId: Int): String {
        require(userId >= 0)
        require(packageName.isNotBlank())
        return "$userId:$packageName"
    }
}
