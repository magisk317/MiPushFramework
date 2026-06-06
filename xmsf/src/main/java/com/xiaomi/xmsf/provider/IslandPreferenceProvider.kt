package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class IslandPreferenceProvider : ContentProvider() {
    private companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    private val keys = listOf(
        ISLAND_PREF_ENABLED,
        ISLAND_PREF_TIMEOUT,
        ISLAND_PREF_FIRST_FLOAT,
        ISLAND_PREF_ENABLE_FLOAT,
        ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
        ISLAND_PREF_FOCUS_NOTIF,
    )

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (uri.authority != ISLAND_PREF_AUTHORITY || uri.lastPathSegment != ISLAND_PREF_PATH_FLAGS) {
            return null
        }
        if (!isAuthorizedCaller()) {
            return null
        }
        val appContext = context?.applicationContext ?: return null
        val repository = PreferenceRepository(appContext.dataStore)
        val requestedKeys = selectionArgs?.filter { it in keys }?.takeIf { it.isNotEmpty() } ?: keys
        val packageName = uri.getQueryParameter(ISLAND_PREF_COLUMN_PACKAGE)
            ?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("package")
                ?.takeIf { it.isNotBlank() }

        return MatrixCursor(arrayOf(ISLAND_PREF_COLUMN_KEY, ISLAND_PREF_COLUMN_VALUE)).apply {
            val flags = runCatching {
                runBlocking {
                    val globalEnabled = repository.islandEnabled.first()
                    val globalFocusNotification = repository.islandFocusNotification.first()
                    val appEnabled = packageName?.let(RegisteredApplicationDb::getIslandEnabled)
                    val appFocusNotification = packageName?.let(
                        RegisteredApplicationDb::getIslandFocusNotificationEnabled
                    )
                    mapOf(
                        ISLAND_PREF_ENABLED to (globalEnabled && (appEnabled ?: true)).toFlagValue(),
                        ISLAND_PREF_TIMEOUT to repository.islandTimeout.first().coerceAtLeast(1).toString(),
                        ISLAND_PREF_FIRST_FLOAT to repository.islandFirstFloat.first().toFlagValue(),
                        ISLAND_PREF_ENABLE_FLOAT to repository.islandEnableFloat.first().toFlagValue(),
                        ISLAND_PREF_SHOW_NOTIFICATION to repository.islandShowNotification.first().toFlagValue(),
                        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to repository.islandShowOriginalNotification.first().toFlagValue(),
                        ISLAND_PREF_FOCUS_NOTIF to (
                            globalFocusNotification &&
                                (appFocusNotification ?: true)
                            ).toFlagValue(),
                    )
                }
            }.getOrDefault(defaultFlags())
            requestedKeys.forEach { key ->
                addRow(arrayOf(key, flags.getValue(key)))
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    private fun isAuthorizedCaller(): Boolean {
        val appContext = context ?: return false
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid() || callingUid == Process.SYSTEM_UID || callingUid == Process.ROOT_UID) {
            return true
        }
        val packages = appContext.packageManager.getPackagesForUid(callingUid).orEmpty()
        if (SYSTEM_UI_PACKAGE in packages) {
            return true
        }
        return appContext.checkCallingPermission(ISLAND_PREF_READ_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun Boolean.toFlagValue(): String = if (this) "1" else "0"

    private fun defaultFlags(): Map<String, String> = mapOf(
        ISLAND_PREF_ENABLED to true.toFlagValue(),
        ISLAND_PREF_TIMEOUT to "5",
        ISLAND_PREF_FIRST_FLOAT to true.toFlagValue(),
        ISLAND_PREF_ENABLE_FLOAT to true.toFlagValue(),
        ISLAND_PREF_SHOW_NOTIFICATION to true.toFlagValue(),
            ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to true.toFlagValue(),
        ISLAND_PREF_FOCUS_NOTIF to true.toFlagValue(),
    )
}
