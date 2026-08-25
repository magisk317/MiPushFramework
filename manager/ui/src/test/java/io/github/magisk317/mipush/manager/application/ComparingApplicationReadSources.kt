package io.github.magisk317.mipush.manager.application

import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics

class ComparingApplicationListSource internal constructor(
    private val primaryLoader: suspend (ApplicationListRequest) -> ApplicationListSnapshot,
    private val remoteLoader: suspend (ApplicationListRequest) -> ApplicationReadResult<ApplicationListSnapshot>,
    private val enableRemoteCompare: Boolean = false,
) {
    constructor(
        inProcessSource: GatewayApplicationListSource,
        remoteSource: RemoteApplicationListSource,
        enableRemoteCompare: Boolean = false,
    ) : this(
        primaryLoader = inProcessSource::load,
        remoteLoader = remoteSource::load,
        enableRemoteCompare = enableRemoteCompare,
    )

    suspend fun loadPrimary(request: ApplicationListRequest): ApplicationListSnapshot = primaryLoader(request)

    suspend fun compareRemote(
        request: ApplicationListRequest,
        primary: ApplicationListSnapshot,
    ): ApplicationListComparison {
        if (!enableRemoteCompare) return ApplicationListComparison.NotStarted
        return when (val remote = remoteLoader(request)) {
            is ApplicationReadResult.Available -> compareApplicationLists(primary, remote.value)
            is ApplicationReadResult.Unavailable -> ApplicationListComparison.Skipped(remote.status)
        }
    }
}

class ComparingApplicationDetailSource internal constructor(
    private val primaryLoader: suspend (String, Boolean) -> ManagerApplication?,
    private val primaryDiagnosticsLoader: suspend (String, Int) -> ManagerApplicationDiagnostics,
    private val remoteLoader: suspend (String, Boolean) -> ApplicationReadResult<ManagerApplication?>,
    private val remoteDiagnosticsLoader: suspend (String, Int) -> ApplicationReadResult<ManagerApplicationDiagnostics>,
    private val enableRemoteCompare: Boolean = false,
) {
    constructor(
        inProcessSource: GatewayApplicationDetailSource,
        remoteSource: RemoteApplicationDetailSource,
        enableRemoteCompare: Boolean = false,
    ) : this(
        primaryLoader = inProcessSource::load,
        primaryDiagnosticsLoader = inProcessSource::loadDiagnostics,
        remoteLoader = remoteSource::load,
        remoteDiagnosticsLoader = remoteSource::loadDiagnostics,
        enableRemoteCompare = enableRemoteCompare,
    )

    suspend fun loadPrimary(packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? =
        primaryLoader(packageName, ignoreNotRegistered)

    suspend fun loadPrimaryDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics =
        primaryDiagnosticsLoader(packageName, registeredType)

    suspend fun compareRemote(
        packageName: String,
        ignoreNotRegistered: Boolean,
        primary: ManagerApplication?,
    ): ApplicationDetailComparison {
        if (!enableRemoteCompare) return ApplicationDetailComparison.NotStarted
        return when (val remote = remoteLoader(packageName, ignoreNotRegistered)) {
            is ApplicationReadResult.Available -> compareApplicationDetails(primary, remote.value)
            is ApplicationReadResult.Unavailable -> ApplicationDetailComparison.Skipped(remote.status)
        }
    }

    suspend fun compareRemoteDiagnostics(
        packageName: String,
        registeredType: Int,
        primary: ManagerApplicationDiagnostics,
    ): ApplicationDiagnosticsComparison {
        if (!enableRemoteCompare) return ApplicationDiagnosticsComparison.NotStarted
        return when (val remote = remoteDiagnosticsLoader(packageName, registeredType)) {
            is ApplicationReadResult.Available -> compareApplicationDiagnostics(primary, remote.value)
            is ApplicationReadResult.Unavailable -> ApplicationDiagnosticsComparison.Skipped(remote.status)
        }
    }
}

sealed interface ApplicationListComparison {
    data object NotStarted : ApplicationListComparison
    data object Comparing : ApplicationListComparison
    data object Matched : ApplicationListComparison
    data class Different(val fields: Set<ApplicationListField>) : ApplicationListComparison
    data class Skipped(val status: ApplicationReadStatus) : ApplicationListComparison
}

enum class ApplicationListField {
    TOTAL_PACKAGE_COUNT,
    STATS,
    PACKAGE_SET,
    ORDER,
    APPLICATION_FIELDS,
}

sealed interface ApplicationDetailComparison {
    data object NotStarted : ApplicationDetailComparison
    data object Comparing : ApplicationDetailComparison
    data object Matched : ApplicationDetailComparison
    data class Different(val fields: Set<ApplicationField>) : ApplicationDetailComparison
    data class Skipped(val status: ApplicationReadStatus) : ApplicationDetailComparison
}

enum class ApplicationField {
    PRESENCE,
    ID,
    PACKAGE_NAME,
    TYPE,
    NOTIFICATION_ON_REGISTER,
    BLOCKED,
    ISLAND_ENABLED,
    ISLAND_FOCUS_NOTIFICATION,
    REGISTERED_TYPE,
    EXIST_SERVICES,
    APP_NAME,
    APP_NAME_PINYIN,
    LAST_RECEIVE_TIME,
}

sealed interface ApplicationDiagnosticsComparison {
    data object NotStarted : ApplicationDiagnosticsComparison
    data object Comparing : ApplicationDiagnosticsComparison
    data object Matched : ApplicationDiagnosticsComparison
    data class Different(val fields: Set<ApplicationDiagnosticsField>) : ApplicationDiagnosticsComparison
    data class Skipped(val status: ApplicationReadStatus) : ApplicationDiagnosticsComparison
}

enum class ApplicationDiagnosticsField {
    HAS_LOCAL_REGISTRATION,
    REG_SEC_COUNT,
    LATEST_REGISTRATION_EVENT_RESULT,
    REGISTERED_TYPE,
    INFERENCE_REASON,
}

private fun compareApplicationLists(
    primary: ApplicationListSnapshot,
    remote: ApplicationListSnapshot,
): ApplicationListComparison {
    val primaryItems = primary.applications.items
    val remoteItems = remote.applications.items
    val fields = buildSet {
        if (primary.applications.totalPkg != remote.applications.totalPkg) {
            add(ApplicationListField.TOTAL_PACKAGE_COUNT)
        }
        if (primary.stats != remote.stats) add(ApplicationListField.STATS)
        val primaryByPackage = primaryItems.associateBy(ManagerApplication::packageName)
        val remoteByPackage = remoteItems.associateBy(ManagerApplication::packageName)
        if (primaryByPackage.keys != remoteByPackage.keys ||
            primaryByPackage.size != primaryItems.size || remoteByPackage.size != remoteItems.size
        ) {
            add(ApplicationListField.PACKAGE_SET)
        }
        if (primaryItems.map(ManagerApplication::packageName) != remoteItems.map(ManagerApplication::packageName)) {
            add(ApplicationListField.ORDER)
        }
        if (primaryByPackage.keys.intersect(remoteByPackage.keys).any { packageName ->
                primaryByPackage.getValue(packageName) != remoteByPackage.getValue(packageName)
            }
        ) {
            add(ApplicationListField.APPLICATION_FIELDS)
        }
    }
    return if (fields.isEmpty()) ApplicationListComparison.Matched else ApplicationListComparison.Different(fields)
}

private fun compareApplicationDetails(
    primary: ManagerApplication?,
    remote: ManagerApplication?,
): ApplicationDetailComparison {
    if (primary == null || remote == null) {
        return if (primary == remote) {
            ApplicationDetailComparison.Matched
        } else {
            ApplicationDetailComparison.Different(setOf(ApplicationField.PRESENCE))
        }
    }
    val fields = buildSet {
        if (primary.id != remote.id) add(ApplicationField.ID)
        if (primary.packageName != remote.packageName) add(ApplicationField.PACKAGE_NAME)
        if (primary.type != remote.type) add(ApplicationField.TYPE)
        if (primary.notificationOnRegister != remote.notificationOnRegister) {
            add(ApplicationField.NOTIFICATION_ON_REGISTER)
        }
        if (primary.blocked != remote.blocked) add(ApplicationField.BLOCKED)
        if (primary.islandEnabled != remote.islandEnabled) add(ApplicationField.ISLAND_ENABLED)
        if (primary.islandFocusNotification != remote.islandFocusNotification) {
            add(ApplicationField.ISLAND_FOCUS_NOTIFICATION)
        }
        if (primary.registeredType != remote.registeredType) add(ApplicationField.REGISTERED_TYPE)
        if (primary.existServices != remote.existServices) add(ApplicationField.EXIST_SERVICES)
        if (primary.appName != remote.appName) add(ApplicationField.APP_NAME)
        if (primary.appNamePinYin != remote.appNamePinYin) add(ApplicationField.APP_NAME_PINYIN)
        if (primary.lastReceiveTimeMs != remote.lastReceiveTimeMs) add(ApplicationField.LAST_RECEIVE_TIME)
    }
    return if (fields.isEmpty()) ApplicationDetailComparison.Matched else ApplicationDetailComparison.Different(fields)
}

private fun compareApplicationDiagnostics(
    primary: ManagerApplicationDiagnostics,
    remote: ManagerApplicationDiagnostics,
): ApplicationDiagnosticsComparison {
    val fields = buildSet {
        if (primary.hasLocalRegistration != remote.hasLocalRegistration) {
            add(ApplicationDiagnosticsField.HAS_LOCAL_REGISTRATION)
        }
        if (primary.regSecCount != remote.regSecCount) add(ApplicationDiagnosticsField.REG_SEC_COUNT)
        if (primary.latestRegistrationEventResult != remote.latestRegistrationEventResult) {
            add(ApplicationDiagnosticsField.LATEST_REGISTRATION_EVENT_RESULT)
        }
        if (primary.registeredType != remote.registeredType) {
            add(ApplicationDiagnosticsField.REGISTERED_TYPE)
        }
        if (primary.inferenceReason != remote.inferenceReason) add(ApplicationDiagnosticsField.INFERENCE_REASON)
    }
    return if (fields.isEmpty()) {
        ApplicationDiagnosticsComparison.Matched
    } else {
        ApplicationDiagnosticsComparison.Different(fields)
    }
}
