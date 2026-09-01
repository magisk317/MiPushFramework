package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import io.github.magisk317.xposed.logging.PackageCallerGuard
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_USER
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.DUAL_APP_ENABLED_KEY
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.notification.IslandOptionsSnapshotReader

class IslandPreferenceProvider : ContentProvider() {
    private companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val AMAP_PACKAGE = "com.autonavi.minimap"
        private val systemUiCallerGuard = PackageCallerGuard(setOf(SYSTEM_UI_PACKAGE))
        private val amapCallerGuard = PackageCallerGuard(
            allowedPackages = setOf(AMAP_PACKAGE),
            requireSystemPackage = false,
        )
    }

    private enum class CallerAccess {
        FULL,
        FOCUS_BYPASS_ONLY,
        DENIED,
    }

    private val keys = listOf(
        ISLAND_PREF_ENABLED,
        ISLAND_PREF_TIMEOUT,
        ISLAND_PREF_FIRST_FLOAT,
        ISLAND_PREF_ENABLE_FLOAT,
        ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
        ISLAND_PREF_FOCUS_NOTIF,
        COLOR_STATUS_BAR_ICON_KEY,
        COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
        DUAL_APP_ENABLED_KEY,
        LOG_SANITIZATION_ENABLED_KEY,
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
        val appContext = context?.applicationContext ?: return null
        val callerAccess = resolveCallerAccess(appContext)
        if (callerAccess == CallerAccess.DENIED) {
            return null
        }
        val requestedKeys = preferenceKeysForCaller(
            focusBypassOnly = callerAccess == CallerAccess.FOCUS_BYPASS_ONLY,
            selectionArgs = selectionArgs,
        )
        val packageName = if (callerAccess == CallerAccess.FOCUS_BYPASS_ONLY) {
            // The app-process bridge needs only the global authorization switch. Never expose
            // registered-app focus state through a caller-controlled package query.
            null
        } else {
            uri.getQueryParameter(ISLAND_PREF_COLUMN_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("package")
                    ?.takeIf { it.isNotBlank() }
        }
        val userId = uri.getQueryParameter(ISLAND_PREF_COLUMN_USER)
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }

        if (requiresExplicitUserScope(packageName, userId)) {
            // A package-scoped read must identify the notification owner explicitly. Falling back
            // to the XMSF process user can return primary-user settings for a cloned notification.
            return null
        }

        return MatrixCursor(arrayOf(ISLAND_PREF_COLUMN_KEY, ISLAND_PREF_COLUMN_VALUE)).apply {
            val snapshot = IslandOptionsSnapshotReader.read(appContext, packageName, userId)
            val flags = snapshot.options.toPreferenceFlags(snapshot.logSanitizationEnabled)
            requestedKeys.forEach { key ->
                addRow(arrayOf(key, flags.getValue(key)))
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    private fun resolveCallerAccess(appContext: android.content.Context): CallerAccess {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.ROOT_UID || systemUiCallerGuard.isCallerAllowed(appContext)) {
            return CallerAccess.FULL
        }
        if (appContext.checkCallingPermission(ISLAND_PREF_READ_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            return CallerAccess.FULL
        }
        return if (amapCallerGuard.isCallerAllowed(appContext)) {
            CallerAccess.FOCUS_BYPASS_ONLY
        } else {
            CallerAccess.DENIED
        }
    }

    internal fun preferenceKeysForCaller(
        focusBypassOnly: Boolean,
        selectionArgs: Array<out String>?,
    ): List<String> {
        val allowedKeys = if (focusBypassOnly) listOf(ISLAND_PREF_FOCUS_NOTIF) else keys
        return if (selectionArgs.isNullOrEmpty()) {
            allowedKeys
        } else {
            selectionArgs.filter { it in allowedKeys }
        }
    }

    internal fun isTrustedSystemUiPackage(packageName: String, flags: Int): Boolean =
        systemUiCallerGuard.isPackageAllowed(packageName, flags)

    internal fun isTrustedAmapPackage(packageName: String, flags: Int): Boolean =
        amapCallerGuard.isPackageAllowed(packageName, flags)

    internal fun requiresExplicitUserScope(packageName: String?, userId: Int?): Boolean =
        !packageName.isNullOrBlank() && userId == null
}
