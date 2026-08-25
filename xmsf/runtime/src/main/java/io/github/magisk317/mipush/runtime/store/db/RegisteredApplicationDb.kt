package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.common.BuildConfig.DEBUG
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeIslandSettings
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRepository
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationStore
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeUnregistrationResult
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.runBlocking

/**
 * Android facade for the commonMain REGISTERED_APPLICATION aggregate repository.
 *
 * Room, Android user lookup, package-name resolution, and telemetry remain here;
 * registration state transitions and user-scoped persistence semantics live in KMP.
 */
object RegisteredApplicationDb {
    @JvmStatic
    fun registerApplication(pkg: String): RuntimeRegisteredApplicationRow {
        val startedAt = System.nanoTime()
        logD("registerApplication() called for: $pkg")
        val outcome = runBlocking { repository().registerApplicationOutcome(pkg) }
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "push",
                "stage" to "app_db_register",
                "reason" to if (outcome.created) "created" else "existing",
                "target_package" to pkg,
            ),
            statusOk = true,
        )
        return outcome.application
    }

    @JvmStatic
    fun getRegisteredApplication(pkg: String): RuntimeRegisteredApplicationRow? {
        val application = runBlocking { repository().getRegisteredApplication(pkg) }
        if (DEBUG) {
            logD("register -> existing application = $application")
        }
        return application
    }

    @JvmStatic
    fun getList(pkg: String?): List<RuntimeRegisteredApplicationRow> = runBlocking {
        repository().getList(pkg)
    }

    @JvmStatic
    fun update(application: RuntimeRegisteredApplicationRow): Long = runBlocking {
        repository().update(application)
    }

    @JvmStatic
    fun updateBlocked(id: Long, blocked: Boolean): Int = runBlocking {
        repository().updateBlocked(id, blocked)
    }

    @JvmStatic
    fun isBlocked(pkg: String): Boolean = runBlocking {
        repository().isBlocked(pkg)
    }

    @JvmStatic
    fun getIslandEnabled(pkg: String): Boolean? = runCatching {
        getIslandSettings(pkg)?.enabled
    }.getOrNull()

    @JvmStatic
    fun getIslandFocusNotificationEnabled(pkg: String): Boolean? = runCatching {
        getIslandSettings(pkg)?.focusNotification
    }.getOrNull()

    @JvmStatic
    fun getIslandSettings(pkg: String, requestedUserId: Int? = null): RuntimeIslandSettings? {
        val userId = requestedUserId?.takeIf { it >= 0 } ?: currentUserId()
        return runBlocking {
            repository(userId).getIslandSettings(pkg)
        }
    }

    @JvmStatic
    fun markUnregistered(pkg: String, requestedUserId: Int? = null): Boolean {
        val userId = requestedUserId?.takeIf { it >= 0 } ?: currentUserId()
        val startedAt = System.nanoTime()
        val result = runBlocking { repository(userId).markUnregistered(pkg) }
        val updated = result == RuntimeUnregistrationResult.Updated
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to if (updated) "ok" else "skip",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "push",
                "stage" to "app_db_unregister",
                "reason" to result.telemetryReason,
                "target_package" to pkg,
            ),
            statusOk = result != RuntimeUnregistrationResult.UpdateFailed,
        )
        return updated
    }

    private fun repository(userId: Int = currentUserId()): RuntimeRegisteredApplicationRepository =
        RuntimeRegisteredApplicationRepository(
            store = DatabaseRegisteredApplicationStore,
            userId = userId,
            appNameForPackage = { pkg ->
                ApplicationNameCache.getAppName(requireNotNull(Utils.getApplication()), pkg)
                    .toString()
            },
        )

    private fun currentUserId(): Int = Utils.myUserId().coerceAtLeast(0)

    private object DatabaseRegisteredApplicationStore : RuntimeRegisteredApplicationStore {
        override suspend fun getByPackageName(
            packageName: String,
            userId: Int,
        ): RuntimeRegisteredApplicationRow? =
            DatabaseUtils.registeredApplicationDao.getByPackageName(packageName, userId)

        override suspend fun getAll(userId: Int): List<RuntimeRegisteredApplicationRow> =
            DatabaseUtils.registeredApplicationDao.getAll(userId)

        override suspend fun insert(application: RuntimeRegisteredApplicationRow): Long =
            DatabaseUtils.registeredApplicationDao.insert(application)

        override suspend fun insertOrReplace(application: RuntimeRegisteredApplicationRow): Long =
            DatabaseUtils.registeredApplicationDao.insertOrReplace(application)

        override suspend fun update(application: RuntimeRegisteredApplicationRow): Int =
            DatabaseUtils.registeredApplicationDao.update(application)

        override suspend fun updateBlocked(id: Long, blocked: Boolean, userId: Int): Int =
            DatabaseUtils.registeredApplicationDao.updateBlocked(id, blocked, userId)

        override suspend fun isBlocked(packageName: String, userId: Int): Boolean? =
            DatabaseUtils.registeredApplicationDao.isBlocked(packageName, userId)
    }
}

private val RuntimeUnregistrationResult.telemetryReason: String
    get() = when (this) {
        RuntimeUnregistrationResult.Missing -> "missing"
        RuntimeUnregistrationResult.AlreadyUnregistered -> "already_unregistered"
        RuntimeUnregistrationResult.Updated -> "updated"
        RuntimeUnregistrationResult.UpdateFailed -> "update_failed"
    }
