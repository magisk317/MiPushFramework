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
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard

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
    fun getList(
        pkg: String?,
        requestedUserId: Int? = null,
    ): List<RuntimeRegisteredApplicationRow> = runBlocking {
        repository(requestedUserId?.let(::requireValidUserId) ?: currentUserId()).getList(pkg)
    }

    @JvmStatic
    fun update(application: RuntimeRegisteredApplicationRow): Long = runBlocking {
        repository(requireValidUserId(application.userId)).update(application)
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
    fun isClickFallbackEnabled(pkg: String): Boolean = runBlocking {
        repository().isClickFallbackEnabled(pkg)
    }

    @JvmStatic
    fun isSuppressWhenDisabled(pkg: String): Boolean = runBlocking {
        repository().isSuppressWhenDisabled(pkg)
    }

    /**
     * Per-app opt-in from the manager's app detail page: suppress this app's notifications
     * while the target is disabled or suspended. Defaults to off; a missing row or a store
     * failure keeps notifications flowing.
     */
    @JvmStatic
    fun shouldSuppressForInactiveApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (!runCatching { isSuppressWhenDisabled(pkg) }.getOrDefault(false)) return false
        return Utils.isAppDisabledOrSuspended(pkg)
    }

    @JvmStatic
    fun updateClickFallbackEnabled(id: Long, enabled: Boolean): Int = runBlocking {
        repository().updateClickFallbackEnabled(id, enabled)
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
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
        return runBlocking {
            repository(userId).getIslandSettings(pkg)
        }
    }

    @JvmStatic
    fun markUnregistered(pkg: String, requestedUserId: Int? = null): Boolean {
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
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

    /** Saves the original packet durably before the registration gate reports Ask. */
    @JvmStatic
    fun savePendingRegistration(request: RuntimePendingRegistrationRow) {
        requireValidUserId(request.userId)
        require(request.packageName.isNotBlank()) { "Empty registration package" }
        require(request.payload.isNotEmpty()) { "Empty registration payload" }
        runBlocking { DatabaseUtils.pendingRegistrationDao.save(request.copy(payload = request.payload.copyOf())) }
    }

    @JvmStatic
    fun getPendingRegistration(pkg: String, requestedUserId: Int? = null): RuntimePendingRegistrationRow? {
        require(pkg.isNotBlank())
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
        return runBlocking { DatabaseUtils.pendingRegistrationDao.get(pkg, userId) }
    }

    @JvmStatic
    fun getPendingRegistrations(requestedUserId: Int? = null): List<RuntimePendingRegistrationRow> {
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
        return runBlocking { DatabaseUtils.pendingRegistrationDao.getAll(userId) }
    }

    @JvmStatic
    fun clearPendingRegistration(pkg: String, requestedUserId: Int? = null): Int {
        require(pkg.isNotBlank())
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
        return runBlocking { DatabaseUtils.pendingRegistrationDao.clear(pkg, userId) }
    }

    /** ASK retains a real pending packet. ALLOW/DENY resolve it in the same transaction. */
    @JvmStatic
    fun setRegistrationPolicy(
        pkg: String,
        type: Int,
        requestedUserId: Int? = null,
        clearPending: Boolean = true,
    ): Boolean {
        require(pkg.isNotBlank())
        require(type == RegisteredAppType.ASK || type == RegisteredAppType.ALLOW || type == RegisteredAppType.DENY)
        val userId = requestedUserId?.let(::requireValidUserId) ?: currentUserId()
        return PackageRegistrationGuard.withPackage(pkg, userId) {
            val updated = runBlocking {
                DatabaseUtils.requireDatabase().useWriterConnection { connection ->
                    connection.immediateTransaction {
                        val changed = DatabaseUtils.registeredApplicationDao.updateRegistrationPolicy(
                            pkg, userId, type, type == RegisteredAppType.DENY,
                        ) > 0
                        if (changed && type != RegisteredAppType.ASK && clearPending) DatabaseUtils.pendingRegistrationDao.clear(pkg, userId)
                        changed
                    }
                }
            }
            if (updated) PackageRegistrationGuard.recordPolicyMutation(pkg, userId)
            updated
        }
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

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrNull()
        ?.takeIf { it >= 0 }
        ?: error("Unable to resolve current Android user id")

    private fun requireValidUserId(userId: Int): Int {
        require(userId >= 0) { "Invalid Android user id: $userId" }
        return userId
    }

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

        override suspend fun isClickFallbackEnabled(packageName: String, userId: Int): Boolean? =
            DatabaseUtils.registeredApplicationDao.isClickFallbackEnabled(packageName, userId)

        override suspend fun updateClickFallbackEnabled(id: Long, enabled: Boolean, userId: Int): Int =
            DatabaseUtils.registeredApplicationDao.updateClickFallbackEnabled(id, enabled, userId)

        override suspend fun isSuppressWhenDisabled(packageName: String, userId: Int): Boolean? =
            DatabaseUtils.registeredApplicationDao.isSuppressWhenDisabled(packageName, userId)
    }
}

private val RuntimeUnregistrationResult.telemetryReason: String
    get() = when (this) {
        RuntimeUnregistrationResult.Missing -> "missing"
        RuntimeUnregistrationResult.AlreadyUnregistered -> "already_unregistered"
        RuntimeUnregistrationResult.Updated -> "updated"
        RuntimeUnregistrationResult.UpdateFailed -> "update_failed"
    }
