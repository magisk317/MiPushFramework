package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.ICON_PACK_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_BITMAP
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_USER
import io.github.magisk317.mipush.common.ICON_PACK_PREF_PATH_ICON
import io.github.magisk317.mipush.common.ICON_PACK_PREF_READ_PERMISSION
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.utils.IconConfigurations
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream

/** Read-only bridge for the configured icon pack. Only SystemUI consumes this endpoint. */
class IconPackProvider : ContentProvider() {
    private companion object {
        const val TAG = "MiPushIconPackProvider"
    }
    @Volatile
    private var loadedDirectory: String? = null

    private val loadLock = Any()
    private val embeddedConfigurations = IconConfigurations()
    @Volatile
    private var embeddedLoaded = false

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.authority != ICON_PACK_PREF_AUTHORITY || uri.lastPathSegment != ICON_PACK_PREF_PATH_ICON) {
            return null
        }
        if (!isAuthorizedCaller()) return null
        val targetPackage = uri.getQueryParameter(ICON_PACK_PREF_COLUMN_PACKAGE)
            ?.takeIf { it.isNotBlank() } ?: return null
        val userId = uri.getQueryParameter(ICON_PACK_PREF_COLUMN_USER)?.toIntOrNull()
            ?.takeIf { it >= 0 } ?: return null
        val appContext = context?.applicationContext ?: return null
        ensureEmbeddedConfigurationsLoaded(appContext)
        val embeddedConfig = embeddedConfigurations.get(targetPackage)
        val config = embeddedConfig ?: run {
            ensureConfigurationsLoaded(appContext)
            runCatching { Global.iconConfigurations().get(targetPackage) }.getOrNull()
        }
            ?: return emptyCursor().also {
                Logger.withTag(TAG).w { "configuration unavailable target=$targetPackage source=embedded-and-user" }
            }
        if (config.isEnabled != true) {
            Logger.withTag(TAG).d { "configuration disabled target=$targetPackage" }
            return emptyCursor()
        }
        val bitmap = config.bitmap()?.takeIf { !it.isRecycled }
            ?: return emptyCursor().also { Logger.withTag(TAG).w { "bitmap unavailable target=$targetPackage" } }
        val bytes = runCatching {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) return@runCatching null
                output.toByteArray()
            }
        }.getOrNull() ?: return emptyCursor()
        return MatrixCursor(arrayOf(ICON_PACK_PREF_COLUMN_PACKAGE, ICON_PACK_PREF_COLUMN_USER, ICON_PACK_PREF_COLUMN_BITMAP))
            .apply { addRow(arrayOf(targetPackage, userId, bytes)) }
    }

    private fun emptyCursor(): Cursor = MatrixCursor(
        arrayOf(ICON_PACK_PREF_COLUMN_PACKAGE, ICON_PACK_PREF_COLUMN_USER, ICON_PACK_PREF_COLUMN_BITMAP),
    )

    private fun ensureConfigurationsLoaded(appContext: android.content.Context) {
        val directory = runCatching {
            runBlocking { PreferenceRepository(appContext.dataStore).configDirectory.first() }
        }.getOrNull()
        if (directory.isNullOrBlank() || directory == loadedDirectory) return
        synchronized(loadLock) {
            if (directory == loadedDirectory) return
            val loaded = runCatching {
                Global.iconConfigurations().init(appContext, Uri.parse(directory))
            }.getOrDefault(false)
            Logger.withTag(TAG).d { "load directory=$directory result=$loaded" }
            if (loaded) loadedDirectory = directory
        }
    }

    private fun ensureEmbeddedConfigurationsLoaded(appContext: android.content.Context) {
        if (embeddedLoaded) return
        synchronized(loadLock) {
            if (embeddedLoaded) return
            embeddedLoaded = embeddedConfigurations.initFromAssets(appContext)
            Logger.withTag(TAG).d { "load embedded configurations result=$embeddedLoaded" }
        }
    }

    private fun isAuthorizedCaller(): Boolean {
        val appContext = context ?: return false
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid() || callingUid == Process.SYSTEM_UID || callingUid == Process.ROOT_UID) return true
        if (appContext.packageManager.getPackagesForUid(callingUid)?.contains("com.android.systemui") == true) return true
        return appContext.checkCallingPermission(ICON_PACK_PREF_READ_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
