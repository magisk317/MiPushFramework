package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

/** A persisted row copied into a read-only value object. */
data class StoredApplicationSnapshot(
    val id: Long?,
    val packageName: String,
    val type: Int,
    val notificationOnRegister: Boolean,
    val blocked: Boolean,
    val islandEnabled: Boolean,
    val islandFocusNotification: Boolean,
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
    fun readStoredApplications(): List<StoredApplicationSnapshot>

    fun readInstalledApplications(includeSystemApps: Boolean): ApplicationCatalogSnapshot

    fun readInstalledApplication(packageName: String): InstalledApplicationSnapshot?

    fun readLastReceiveTime(packageName: String): Long

    fun readLastReceiveTimes(packageNames: Collection<String>): Map<String, Long> =
        packageNames.associateWith { readLastReceiveTime(it) }

    /** Reads registration artifacts only; implementations must not persist a reconciliation result. */
    fun readLocallyRegisteredPackages(packageNames: Collection<String>): Set<String> = emptySet()

    fun hasLocalRegistration(packageName: String): Boolean =
        packageName in readLocallyRegisteredPackages(listOf(packageName))

    fun readRegSecCount(packageName: String): Int = 0

    fun readLatestRegistrationEvent(packageName: String): RegistrationEventSnapshot? = null
}

data class ManagerApplicationReadQuery(
    val schemaVersion: Int = 1,
    val query: String = "",
    val filterMode: Int = FILTER_ALL,
    val includeSystemApps: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val pageToken: String? = null,
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
    val registeredType = if (
        locallyRegistered && registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED
    ) {
        ManagerApplication.RegisteredType.REGISTERED
    } else {
        registeredType
    }
    return ManagerApplication(
        id = id,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
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
    notificationOnRegister: Boolean = true,
    deriveAppNamePinYin: Boolean = true,
): ManagerApplication {
    val displayName = appName.take(MAX_APPLICATION_LABEL_LENGTH)
    return ManagerApplication(
        packageName = packageName,
        notificationOnRegister = notificationOnRegister,
        registeredType = if (locallyRegistered) {
            ManagerApplication.RegisteredType.REGISTERED
        } else {
            ManagerApplication.RegisteredType.NOT_REGISTERED
        },
        existServices = hasMiPushServices,
        appName = displayName,
        appNamePinYin = if (deriveAppNamePinYin) displayName.lowercase(java.util.Locale.ROOT) else "",
        lastReceiveTimeMs = lastReceiveTimeMs,
    )
}

internal fun RegisteredApplication.toStoredApplicationSnapshot(): StoredApplicationSnapshot =
    StoredApplicationSnapshot(
        id = id,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
        registeredType = registeredType,
        appName = appName,
    )

private const val MAX_APPLICATION_LABEL_LENGTH = 4_096
