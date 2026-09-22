package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Platform-neutral persistence boundary for the REGISTERED_APPLICATION aggregate.
 *
 * Android owns the Room DAO adapter, user id lookup, application-name resolution,
 * logging, and telemetry. This repository owns the aggregate's state transitions
 * and user-scoped persistence semantics.
 */
interface RuntimeRegisteredApplicationStore {
    suspend fun getByPackageName(packageName: String, userId: Int): RuntimeRegisteredApplicationRow?

    suspend fun getAll(userId: Int): List<RuntimeRegisteredApplicationRow>

    suspend fun insert(application: RuntimeRegisteredApplicationRow): Long

    suspend fun insertOrReplace(application: RuntimeRegisteredApplicationRow): Long

    suspend fun update(application: RuntimeRegisteredApplicationRow): Int

    suspend fun updateBlocked(id: Long, blocked: Boolean, userId: Int): Int

    suspend fun isBlocked(packageName: String, userId: Int): Boolean?

    suspend fun isClickFallbackEnabled(packageName: String, userId: Int): Boolean?

    suspend fun updateClickFallbackEnabled(id: Long, enabled: Boolean, userId: Int): Int
}

data class RuntimeRegisteredApplicationRegistration(
    val application: RuntimeRegisteredApplicationRow,
    val created: Boolean,
)

enum class RuntimeUnregistrationResult {
    Missing,
    AlreadyUnregistered,
    Updated,
    UpdateFailed,
}

class RuntimeRegisteredApplicationRepository(
    private val store: RuntimeRegisteredApplicationStore,
    private val userId: Int,
    private val appNameForPackage: (String) -> String,
) {
    suspend fun registerApplicationOutcome(
        packageName: String,
    ): RuntimeRegisteredApplicationRegistration {
        val existing = store.getByPackageName(packageName, userId)
        if (existing != null) {
            return RuntimeRegisteredApplicationRegistration(
                application = existing,
                created = false,
            )
        }

        val created = RuntimeRegisteredApplicationRow(
            packageName = packageName,
            userId = userId,
            type = RegisteredAppType.ASK,
            notificationOnRegister = true,
            registeredType = RegisteredAppRegisteredType.NotRegistered,
            appName = appNameForPackage(packageName),
        )
        val id = store.insert(created)
        return RuntimeRegisteredApplicationRegistration(
            application = created.copy(id = id),
            created = true,
        )
    }

    suspend fun registerApplication(packageName: String): RuntimeRegisteredApplicationRow =
        registerApplicationOutcome(packageName).application

    suspend fun getRegisteredApplication(
        packageName: String,
    ): RuntimeRegisteredApplicationRow? =
        store.getByPackageName(packageName, userId)

    suspend fun getList(packageName: String?): List<RuntimeRegisteredApplicationRow> =
        if (packageName.isNullOrEmpty()) {
            store.getAll(userId)
        } else {
            store.getByPackageName(packageName, userId)?.let(::listOf).orEmpty()
        }

    suspend fun update(application: RuntimeRegisteredApplicationRow): Long {
        // Never let a stale object id turn REPLACE into a cross-user delete.
        val existingId = store.getByPackageName(application.packageName, userId)?.id
        val scoped = application.copy(
            id = existingId,
            userId = userId,
        )
        val id = store.insertOrReplace(scoped)
        return if (scoped.id == null || scoped.id == 0L) id else scoped.id
    }

    suspend fun updateBlocked(id: Long, blocked: Boolean): Int =
        store.updateBlocked(id, blocked, userId)

    suspend fun isBlocked(packageName: String): Boolean =
        store.isBlocked(packageName, userId) ?: false

    suspend fun updateClickFallbackEnabled(id: Long, enabled: Boolean): Int =
        store.updateClickFallbackEnabled(id, enabled, userId)

    suspend fun isClickFallbackEnabled(packageName: String): Boolean =
        store.isClickFallbackEnabled(packageName, userId) ?: false

    suspend fun getIslandSettings(packageName: String): RuntimeIslandSettings? =
        store.getByPackageName(packageName, userId)?.let {
            RuntimeIslandSettings(
                enabled = it.islandEnabled,
                focusNotification = it.islandFocusNotification,
            )
        }

    suspend fun markUnregistered(packageName: String): RuntimeUnregistrationResult {
        val application = store.getByPackageName(packageName, userId)
            ?: return RuntimeUnregistrationResult.Missing
        if (application.registeredType == RegisteredAppRegisteredType.Unregistered) {
            return RuntimeUnregistrationResult.AlreadyUnregistered
        }

        val updated = application.copy(
            registeredType = RegisteredAppRegisteredType.Unregistered,
            userId = userId,
        )
        return if (store.update(updated) > 0) {
            RuntimeUnregistrationResult.Updated
        } else {
            RuntimeUnregistrationResult.UpdateFailed
        }
    }
}
