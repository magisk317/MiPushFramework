package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.magisk317.mipush.common.notification.iconpack.IconPackSnapshot
import io.github.magisk317.mipush.common.notification.iconpack.IconPackSnapshotCodec
import java.io.File
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.mipush.common.ICON_PACK_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ICON_PACK_PREF_PATH_SNAPSHOT
import io.github.magisk317.mipush.common.ICON_PACK_PREF_READ_PERMISSION
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.platform.support.Global
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream

/**
 * Read-only bridge for the configured icon pack. Only SystemUI consumes this endpoint.
 *
 * The whole index ships as one snapshot file instead of a per-package query. A per-package query
 * could only answer "no icon", so the runtime's cold moments -- the one-time ANIP release load
 * failing -- were indistinguishable from "this package is not configured", and SystemUI cached the
 * wrong answer for 15 seconds and flipped the same notification between pack art and silhouette.
 * A snapshot makes the two answers different by construction: no file means not ready (never
 * cached), a file without the package means confirmed missing.
 */
class IconPackProvider : ContentProvider() {
    private companion object {
        const val TAG = "MiPushIconPackProvider"

        // Retry window for the one-time release load, so a cold failure cannot hammer the disk on
        // every SystemUI request the way the previous private-instance path did.
        const val GLOBAL_STORE_RETRY_INTERVAL_MS = 60_000L
        const val SNAPSHOT_FILE_NAME = "iconpack-snapshot.bin"
    }
    @Volatile
    private var loadedDirectory: String? = null

    private val loadLock = Any()

    @Volatile
    private var globalStoreLoaded = false

    @Volatile
    private var lastGlobalStoreAttemptMs = 0L

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (uri.authority != ICON_PACK_PREF_AUTHORITY || uri.lastPathSegment != ICON_PACK_PREF_PATH_SNAPSHOT) {
            return null
        }
        if (!isAuthorizedCaller()) return null
        val appContext = context?.applicationContext ?: return null
        ensureGlobalStoreReady(appContext)
        ensureConfigurationsLoaded(appContext)
        val stamp = runCatching { Global.iconConfigurations().releaseStamp() }.getOrNull()
        val file = File(appContext.filesDir, SNAPSHOT_FILE_NAME)
        if (!carriesRelease(file, stamp)) rebuildSnapshot(appContext, file, stamp)
        if (!file.isFile) {
            XLog.tagged(TAG).w { "snapshot unavailable stamp=$stamp" }
            return null
        }
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.onFailure {
            XLog.tagged(TAG).w { "snapshot open failed: ${it.javaClass.simpleName}" }
        }.getOrNull()
    }

    /** True when the file on disk was built from exactly the release currently held in memory. */
    private fun carriesRelease(file: File, stamp: Long?): Boolean {
        if (stamp == null || !file.isFile) return false
        return runCatching {
            file.inputStream().use(IconPackSnapshotCodec::readReleaseStamp)
        }.getOrNull() == stamp
    }

    /**
     * Writes the index as a new file and swaps it in atomically. An empty or failed build leaves the
     * previous snapshot in place: serving slightly stale art beats making every package look
     * unconfigured, and SystemUI keeps re-reading the file it already trusts.
     */
    private fun rebuildSnapshot(appContext: android.content.Context, target: File, stamp: Long?) {
        synchronized(loadLock) {
            if (carriesRelease(target, stamp)) return
            val entries = runCatching {
                runBlocking { Global.iconConfigurations().snapshotIcons(appContext) }
            }.getOrElse {
                XLog.tagged(TAG).w { "snapshot build failed: ${it.javaClass.simpleName}" }
                return
            }
            if (entries.isEmpty()) {
                XLog.tagged(TAG).w { "snapshot empty, keeping previous file entries=0" }
                return
            }
            val temporary = File(appContext.filesDir, "${target.name}.tmp")
            val written = runCatching {
                temporary.outputStream().use { output ->
                    IconPackSnapshotCodec.write(
                        IconPackSnapshot(
                            releaseStamp = stamp ?: System.currentTimeMillis(),
                            generatedAtMs = System.currentTimeMillis(),
                            entries = entries,
                        ),
                        output,
                    )
                }
            }.getOrDefault(-1)
            if (written <= 0 || !temporary.renameTo(target)) {
                XLog.tagged(TAG).w { "snapshot write rejected entries=${entries.size} written=$written" }
                temporary.delete()
                return
            }
            XLog.tagged(TAG).i { "snapshot published entries=$written bytes=${target.length()} stamp=$stamp" }
        }
    }

    /**
     * Serves the process-wide [io.github.magisk317.mipush.utils.IconConfigurations] store, never a
     * private instance.
     *
     * ANIP keeps its loaded release inside the [IconConfigurations] instance that initialized it
     * (`anipInstance` is an instance field), so a provider-owned second instance resolves nothing:
     * its `reload()` returned false on every query while the app-startup instance kept working.
     * That is why the icon-pack tier vanished for every package as soon as the runtime process was
     * replaced, and the status bar fell back to manufactured silhouettes.
     *
     * The user icon tree stays an override layer: it is only loaded when a directory is configured.
     */
    /**
     * Guarantees the shared store holds a loaded ANIP release.
     *
     * `IconConfigurations.get` needs an initialized engine *with a loaded release*. The app-startup
     * initialization does not cover every provider entry (measured: a process whose log never shows
     * "ANIP reload completed" answered "no icon" for every package), so the query path does it once
     * and then stops touching the disk.
     */
    private fun ensureGlobalStoreReady(appContext: android.content.Context) {
        if (globalStoreLoaded) return
        val nowMs = System.currentTimeMillis()
        synchronized(loadLock) {
            if (globalStoreLoaded || nowMs - lastGlobalStoreAttemptMs < GLOBAL_STORE_RETRY_INTERVAL_MS) {
                return
            }
            lastGlobalStoreAttemptMs = nowMs
            globalStoreLoaded = runCatching {
                Global.iconConfigurations().initFromAssets(appContext)
            }.getOrDefault(false)
            XLog.tagged(TAG).d { "ensure global ANIP release result=$globalStoreLoaded" }
        }
    }

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
            XLog.tagged(TAG).d { "load directory=$directory result=$loaded" }
            if (loaded) loadedDirectory = directory
        }
    }

    private fun isAuthorizedCaller(): Boolean {
        val appContext = context ?: return false
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid() || callingUid == Process.SYSTEM_UID || callingUid == Process.ROOT_UID) return true
        if (appContext.packageManager.getPackagesForUid(callingUid)?.contains("com.android.systemui") == true) return true
        return appContext.checkCallingPermission(ICON_PACK_PREF_READ_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /** Row queries are not served: the whole index is the only contract SystemUI consumes. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): android.database.Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
