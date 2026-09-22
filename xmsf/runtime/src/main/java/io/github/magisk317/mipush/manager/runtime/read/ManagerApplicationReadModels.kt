package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow

/** A persisted row copied into a read-only value object. */
data class StoredApplicationSnapshot(
    val id: Long?,
    val userId: Int,
    val packageName: String,
    val type: Int,
    val notificationOnRegister: Boolean,
    val blocked: Boolean,
    val islandEnabled: Boolean,
    val islandFocusNotification: Boolean,
    val clickFallbackEnabled: Boolean,
    val registeredType: Int,
    val appName: String,
)

/** Package-manager data used to enrich a persisted row without changing storage. */
data class InstalledApplicationSnapshot(
    val packageName: String,
    val appName: String,
    val hasMiPushServices: Boolean,
)

data class ApplicationCatalogSnapshot(
    val totalCandidatePackages: Int,
    val applications: List<InstalledApplicationSnapshot>,
)

data class RegistrationEventSnapshot(
    val type: Int,
    val result: Int,
)

interface ManagerApplicationReadSource {
    suspend fun currentUserId(): Int

    suspend fun readStoredApplications(): List<StoredApplicationSnapshot>

    suspend fun readInstalledApplications(includeSystemApps: Boolean): ApplicationCatalogSnapshot

    suspend fun readInstalledApplication(packageName: String): InstalledApplicationSnapshot?

    suspend fun readLastReceiveTime(packageName: String): Long

    suspend fun readLastReceiveTimes(packageNames: Collection<String>): Map<String, Long> =
        packageNames.associateWith { readLastReceiveTime(it) }

    /** Reads registration artifacts only; implementations must not persist a reconciliation result. */
    suspend fun readLocallyRegisteredPackages(packageNames: Collection<String>): Set<String> = emptySet()

    /**
     * Returns tri-state local evidence. UNKNOWN means the probe did not complete and must not be
     * treated as a negative result or persisted in the short-lived cache.
     */
    suspend fun readLocalRegistrationStates(
        packageNames: Collection<String>,
    ): Map<String, LocalRegistrationProbeState> {
        val registered = readLocallyRegisteredPackages(packageNames)
        return packageNames.distinct().associateWith { packageName ->
            if (packageName in registered) {
                LocalRegistrationProbeState.REGISTERED
            } else {
                LocalRegistrationProbeState.NOT_REGISTERED
            }
        }
    }

    suspend fun hasLocalRegistration(packageName: String): Boolean =
        readLocalRegistrationStates(listOf(packageName))[packageName] ==
            LocalRegistrationProbeState.REGISTERED

    suspend fun readRegSecCount(packageName: String): Int = 0

    suspend fun readLatestRegistrationEvent(packageName: String): RegistrationEventSnapshot? = null
}

data class ManagerApplicationReadQuery(
    val schemaVersion: Int = 1,
    val query: String = "",
    val filterMode: Int = FILTER_ALL,
    val includeSystemApps: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val pageToken: String? = null,
    val userId: Int,
) {
    companion object {
        const val FILTER_ALL = 0
        const val FILTER_REGISTERED = 1
        const val FILTER_NOT_REGISTERED = 2
        const val FILTER_UNREGISTERED = 3
        const val DEFAULT_PAGE_SIZE = 100
    }
}

data class ManagerApplicationReadStats(
    val total: Int,
    val usingMiPush: Int,
    val notUsingMiPush: Int,
    val registered: Int,
    val notRegistered: Int,
)

data class ManagerApplicationReadPage(
    val userId: Int,
    val items: List<ManagerApplication>,
    val stats: ManagerApplicationReadStats,
    val nextPageToken: String?,
)

fun StoredApplicationSnapshot.toManagerApplication(
    installed: InstalledApplicationSnapshot?,
    lastReceiveTimeMs: Long,
    locallyRegistered: Boolean,
    fallbackToInstalledName: Boolean = true,
    deriveAppNamePinYin: Boolean = true,
): ManagerApplication {
    val displayName = (
        appName.takeIf { it.isNotBlank() || !fallbackToInstalledName }
            ?: installed?.appName.orEmpty()
        )
        .take(MAX_APPLICATION_LABEL_LENGTH)
    val registeredType = RegistrationStateResolver.resolveRegisteredType(
        storedType = registeredType,
        localState = if (locallyRegistered) {
            LocalRegistrationProbeState.REGISTERED
        } else {
            LocalRegistrationProbeState.NOT_REGISTERED
        },
    )
    return ManagerApplication(
        id = id,
        userId = userId,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
        clickFallbackEnabled = clickFallbackEnabled,
        registeredType = registeredType,
        existServices = installed?.hasMiPushServices == true,
        appName = displayName,
        appNamePinYin = if (deriveAppNamePinYin) displayName.lowercase(java.util.Locale.ROOT) else "",
        lastReceiveTimeMs = lastReceiveTimeMs,
    )
}

fun InstalledApplicationSnapshot.toTransientManagerApplication(
    locallyRegistered: Boolean,
    lastReceiveTimeMs: Long,
    userId: Int,
    notificationOnRegister: Boolean = true,
    deriveAppNamePinYin: Boolean = true,
): ManagerApplication {
    val displayName = appName.take(MAX_APPLICATION_LABEL_LENGTH)
    return ManagerApplication(
        userId = requireValidManagerApplicationUserId(userId),
        packageName = packageName,
        notificationOnRegister = notificationOnRegister,
        registeredType = RegistrationStateResolver.resolveRegisteredType(
            storedType = null,
            localState = if (locallyRegistered) {
                LocalRegistrationProbeState.REGISTERED
            } else {
                LocalRegistrationProbeState.NOT_REGISTERED
            },
        ),
        existServices = hasMiPushServices,
        appName = displayName,
        appNamePinYin = if (deriveAppNamePinYin) displayName.lowercase(java.util.Locale.ROOT) else "",
        lastReceiveTimeMs = lastReceiveTimeMs,
    )
}

fun RuntimeRegisteredApplicationRow.toStoredApplicationSnapshot(): StoredApplicationSnapshot =
    StoredApplicationSnapshot(
        id = id,
        userId = userId,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
        clickFallbackEnabled = clickFallbackEnabled,
        registeredType = registeredType,
        appName = appName,
    )

private fun requireValidManagerApplicationUserId(userId: Int): Int {
    require(userId >= 0) { "Invalid Android user id: $userId" }
    return userId
}

private const val MAX_APPLICATION_LABEL_LENGTH = 4_096
