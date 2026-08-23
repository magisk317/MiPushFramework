package io.github.magisk317.mipush.runtime.archive

import android.content.Context
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeStoreDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Diagnostic archive repository backed by the KMP RuntimeStoreDatabase.
 *
 * This is a Phase B-2 pilot feature: writes event snapshots to the KMP shadow
 * database to validate cross-platform schema stability without affecting the
 * production AppDatabase. The two databases run in parallel with separate files.
 *
 * Phase B-3 validation: 1-week device run confirming no schema conflicts.
 */
class RuntimeArchiveRepository(
    private val context: Context,
    private val kmpDatabase: RuntimeStoreDatabase,
) : KoinComponent {
    private val eventDao = kmpDatabase.eventDao()
    private val appDao = kmpDatabase.registeredApplicationDao()

    /**
     * Archive a simplified event snapshot to the KMP shadow database.
     * This is a diagnostic copy; the production Event is still written to AppDatabase.
     */
    suspend fun archiveEvent(
        packageName: String,
        userId: Int,
        type: Int,
        result: Int,
        timestamp: Long,
    ): Long = runCatching {
        eventDao.insert(
            RuntimeEventRow(
                pkg = packageName,
                userId = userId,
                type = type,
                result = result,
                date = timestamp,
            )
        )
    }.onFailure {
        logW("KMP archive: failed to archive event for $packageName", it)
    }.getOrDefault(-1L)

    /**
     * Query archived events by user from the KMP shadow database.
     */
    suspend fun getArchivedEvents(userId: Int): List<RuntimeEventRow> = runCatching {
        eventDao.findByUser(userId)
    }.onFailure {
        logW("KMP archive: failed to query events for user $userId", it)
    }.getOrDefault(emptyList())

    /**
     * Archive a registered application snapshot.
     */
    suspend fun archiveApplication(
        packageName: String,
        userId: Int,
        appName: String,
        registeredType: Int,
        islandEnabled: Boolean = true,
    ): Long = runCatching {
        appDao.insert(
            RuntimeRegisteredApplicationRow(
                packageName = packageName,
                userId = userId,
                type = registeredType,
                notificationOnRegister = false,
                islandEnabled = islandEnabled,
                registeredType = registeredType,
                appName = appName,
            )
        )
    }.onFailure {
        logW("KMP archive: failed to archive app $packageName", it)
    }.getOrDefault(-1L)

    companion object {
        private const val TAG = "RuntimeArchiveRepo"
    }
}
