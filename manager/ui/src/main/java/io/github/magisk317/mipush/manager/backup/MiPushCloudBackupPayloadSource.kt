package io.github.magisk317.mipush.manager.backup

import android.content.Context
import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.data.PreferenceOwner
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.xposed.backup.BackupPayloadSource
import io.github.magisk317.xposed.logging.XLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * MiPush data plane for the cloud backup stack: exports the manager-owned
 * preference entries as a versioned JSON archive into a cache file and applies
 * a downloaded archive back through the same typed preference import the
 * manager migration uses. The produced file name carries [ARCHIVE_PREFIX] plus
 * a timestamp because the transport uploads it verbatim and filters remote
 * listings by that prefix.
 *
 * Scope is deliberately the manager-owned plane (theme, layout, configuration
 * sources, wizard state); runtime-owned push policy lives under XMSF authority
 * and is restored through the runtime upload path, not the manager DataStore.
 * [MiPushBackupArchive.formatVersion] leaves room to add such sections later.
 */
class MiPushCloudBackupPayloadSource(
    private val preferences: PreferenceRepository,
) : BackupPayloadSource {

    override val archivePrefix: String = ARCHIVE_PREFIX

    override suspend fun produceBackup(context: Context): File = withContext(Dispatchers.IO) {
        val archive = MiPushBackupArchive(
            formatVersion = ARCHIVE_FORMAT_VERSION,
            appVersion = currentVersionName(context),
            preferences = preferences.exportOwnedPreferences(PreferenceOwner.MANAGER).map { entry ->
                MiPushBackupPreference(key = entry.key, type = entry.type, value = entry.value)
            },
        )
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val payload = File(context.cacheDir, "$ARCHIVE_PREFIX$timestamp.json")
        runCatching { payload.writeText(Json.encodeToString(archive)) }.onFailure { error ->
            payload.delete()
            throw IllegalStateException("Local backup export failed", error)
        }
        if (!payload.isFile || payload.length() <= 0L) {
            payload.delete()
            throw IllegalStateException("Local backup file missing or empty")
        }
        XLog.i(
            message = "Cloud backup export ready: file=%s entries=%d",
            payload.name,
            archive.preferences.size,
        )
        payload
    }

    override suspend fun applyRestore(context: Context, backup: File) = withContext(Dispatchers.IO) {
        val archive = readArchive(backup)
        requireCompatibleVersion(archive, context)
        if (archive.preferences.isEmpty()) {
            throw IllegalStateException("Restore failed: backup contains no manager settings")
        }
        val entries = archive.preferences.map { entry ->
            OwnedPreferenceValue(
                key = entry.key,
                type = entry.type,
                value = entry.value,
                owner = PreferenceOwner.MANAGER,
            )
        }
        val restored = preferences.importOwnedPreferences(
            entries = entries,
            owner = PreferenceOwner.MANAGER,
            onlyMissing = false,
        )
        XLog.i(message = "Cloud backup restore applied: entries=%d", restored)
    }

    private fun readArchive(backup: File): MiPushBackupArchive {
        val raw = runCatching { backup.readText() }.getOrElse { error ->
            throw IllegalStateException("Restore failed: backup unreadable", error)
        }
        return runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<MiPushBackupArchive>(raw) }
            .getOrElse { error ->
                throw IllegalStateException("Restore failed: backup is not a MiPush backup archive", error)
            }
    }

    private fun requireCompatibleVersion(archive: MiPushBackupArchive, context: Context) {
        if (archive.formatVersion != ARCHIVE_FORMAT_VERSION) {
            throw IllegalStateException(
                "Restore failed: unsupported backup format ${archive.formatVersion}",
            )
        }
        val currentVersion = currentVersionName(context)
        if (archive.appVersion.substringBefore('.') != currentVersion.substringBefore('.')) {
            throw IllegalStateException(
                "Restore failed: backup created by ${archive.appVersion}, current app is $currentVersion",
            )
        }
    }

    private fun currentVersionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0.0.0"

    private companion object {
        const val ARCHIVE_FORMAT_VERSION = 1

        /** Marks cloud archives on the WebDAV server and scopes remote listings. */
        const val ARCHIVE_PREFIX = "MiPush-Cloud-"
    }
}
