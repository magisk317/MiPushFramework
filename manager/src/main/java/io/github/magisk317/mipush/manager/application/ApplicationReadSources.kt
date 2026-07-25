package io.github.magisk317.mipush.manager.application

import android.content.Context
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerApplications
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationStatsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.common.utils.logW
import kotlinx.coroutines.CancellationException

data class ApplicationListRequest(
    val query: String = "",
    val filterMode: Int = 0,
    val includeSystemApps: Boolean = false,
)

data class ApplicationListSnapshot(
    val applications: ManagerApplications,
    val stats: ApplicationListStats,
)

data class ApplicationListStats(
    val total: Int,
    val usingMiPush: Int,
    val notUsingMiPush: Int,
    val registered: Int,
    val notRegistered: Int,
)

sealed interface ApplicationReadResult<out T> {
    data class Available<T>(val value: T) : ApplicationReadResult<T>
    data class Unavailable(val status: ApplicationReadStatus) : ApplicationReadResult<Nothing>
}

enum class ApplicationReadStatus {
    UNSUPPORTED,
    DISCONNECTED,
    BINDING,
    RUNTIME_MISSING,
    PERMISSION_DENIED,
    TIMED_OUT,
    INCOMPATIBLE,
    TEMPORARILY_DISCONNECTED,
    FAILED,
}

/** Gateway-backed list source (ManagerApplicationGateway; remote under standalone host). */
class GatewayApplicationListSource(
    context: Context,
    private val applicationGateway: ManagerApplicationGateway,
) {
    private val appContext = context.applicationContext ?: context

    fun load(request: ApplicationListRequest): ApplicationListSnapshot {
        val applications = applicationGateway.loadApplications(
            context = appContext,
            query = request.query,
            filterMode = request.filterMode,
            includeSystemApps = request.includeSystemApps,
        )
        return ApplicationListSnapshot(
            applications = applications,
            stats = applications.toApplicationListStats(),
        )
    }
}

class RemoteApplicationListSource internal constructor(
    private val pageLoader: suspend (ManagerApplicationQueryDto) -> ManagerRuntimeResult<ManagerApplicationPageDto>,
    private val pageSizeProvider: () -> Int,
) {
    constructor(client: ManagerRuntimeClient) : this(
        pageLoader = client::getApplicationPage,
        pageSizeProvider = {
            val negotiated = (client.availability.value as? ManagerRuntimeAvailability.Available)
                ?.handshake
                ?.maxPageSize
                ?: ManagerProtocol.DEFAULT_MAX_PAGE_SIZE
            minOf(negotiated, ManagerProtocol.DEFAULT_MAX_PAGE_SIZE)
        },
    )

    suspend fun load(request: ApplicationListRequest): ApplicationReadResult<ApplicationListSnapshot> = try {
        loadPages(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
    }

    private suspend fun loadPages(
        request: ApplicationListRequest,
    ): ApplicationReadResult<ApplicationListSnapshot> {
        val pageSize = pageSizeProvider().coerceAtLeast(1)
        val items = mutableListOf<ManagerApplication>()
        val seenPackages = mutableSetOf<String>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        var expectedStats: ManagerApplicationStatsDto? = null
        var pageCount = 0

        do {
            if (++pageCount > MAX_PAGE_REQUESTS) {
                return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
            }
            val result = pageLoader(
                ManagerApplicationQueryDto(
                    query = request.query,
                    filterMode = request.filterMode,
                    includeSystemApps = request.includeSystemApps,
                    pageSize = pageSize,
                    pageToken = token,
                ),
            )
            val page = when (result) {
                is ManagerRuntimeResult.Success -> result.value
                is ManagerRuntimeResult.Unsupported -> return ApplicationReadResult.Unavailable(
                    ApplicationReadStatus.UNSUPPORTED,
                )

                is ManagerRuntimeResult.Unavailable -> {
                    logW(
                        "RemoteApplicationListSource unavailable " +
                            "availability=${result.availability} page=$pageCount",
                    )
                    return ApplicationReadResult.Unavailable(
                        result.availability.toApplicationReadStatus(),
                    )
                }

                is ManagerRuntimeResult.Failed -> return ApplicationReadResult.Unavailable(
                    ApplicationReadStatus.FAILED,
                )
            }
            val stats = expectedStats ?: page.stats.also { expectedStats = it }
            if (page.stats != stats || page.items.any { !seenPackages.add(it.packageName) }) {
                return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
            }
            items += page.items.map(ManagerApplicationSummaryDto::toManagerApplication)
            if (items.size > stats.usingMiPush) {
                return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
            }
            token = page.nextPageToken
            if (token != null && (!seenTokens.add(token) || items.size >= stats.usingMiPush)) {
                return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
            }
        } while (token != null)

        val stats = expectedStats ?: return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
        if (items.size != stats.usingMiPush) {
            return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
        }
        return ApplicationReadResult.Available(
            ApplicationListSnapshot(
                applications = ManagerApplications(
                    items = items,
                    totalPkg = stats.total,
                ),
                stats = stats.toApplicationListStats(),
            ),
        )
    }

    private companion object {
        const val MAX_PAGE_REQUESTS = 1_000
    }
}

class ComparingApplicationListSource internal constructor(
    private val primaryLoader: (ApplicationListRequest) -> ApplicationListSnapshot,
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

    fun loadPrimary(request: ApplicationListRequest): ApplicationListSnapshot = primaryLoader(request)

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

class GatewayApplicationDetailSource(
    context: Context,
    private val applicationGateway: ManagerApplicationGateway,
) {
    private val appContext = context.applicationContext ?: context

    fun load(packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? =
        applicationGateway.getApplication(appContext, packageName, ignoreNotRegistered)

    fun loadDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics =
        applicationGateway.getDiagnostics(packageName, registeredType)
}

class RemoteApplicationDetailSource internal constructor(
    private val detailLoader: suspend (String, Boolean) -> ManagerRuntimeResult<ManagerApplicationDetailDto?>,
    private val diagnosticsLoader: suspend (String, Int) -> ManagerRuntimeResult<ManagerApplicationDiagnosticsDto>,
) {
    constructor(client: ManagerRuntimeClient) : this(
        detailLoader = client::getApplicationDetail,
        diagnosticsLoader = client::getApplicationDiagnostics,
    )

    suspend fun load(
        packageName: String,
        ignoreNotRegistered: Boolean,
    ): ApplicationReadResult<ManagerApplication?> = safelyLoadRemote {
        mapRemoteResult(
            result = detailLoader(packageName, ignoreNotRegistered),
            mapper = { it?.toManagerApplication() },
        )
    }

    suspend fun loadDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ApplicationReadResult<ManagerApplicationDiagnostics> = safelyLoadRemote {
        mapRemoteResult(
            result = diagnosticsLoader(packageName, registeredType),
            mapper = ManagerApplicationDiagnosticsDto::toManagerApplicationDiagnostics,
        )
    }
}

class ComparingApplicationDetailSource internal constructor(
    private val primaryLoader: (String, Boolean) -> ManagerApplication?,
    private val primaryDiagnosticsLoader: (String, Int) -> ManagerApplicationDiagnostics,
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

    fun loadPrimary(packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? =
        primaryLoader(packageName, ignoreNotRegistered)

    fun loadPrimaryDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics =
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

private fun ManagerApplications.toApplicationListStats(): ApplicationListStats {
    val usingMiPush = items.size
    val registered = items.count { it.registeredType == ManagerApplication.RegisteredType.REGISTERED }
    return ApplicationListStats(
        total = totalPkg,
        usingMiPush = usingMiPush,
        notUsingMiPush = (totalPkg - usingMiPush).coerceAtLeast(0),
        registered = registered,
        notRegistered = (usingMiPush - registered).coerceAtLeast(0),
    )
}

private fun ManagerApplicationStatsDto.toApplicationListStats(): ApplicationListStats =
    ApplicationListStats(
        total = total,
        usingMiPush = usingMiPush,
        notUsingMiPush = notUsingMiPush,
        registered = registered,
        notRegistered = notRegistered,
    )

private fun ManagerApplicationSummaryDto.toManagerApplication(): ManagerApplication = ManagerApplication(
    id = id,
    packageName = packageName,
    type = type,
    notificationOnRegister = notificationOnRegister,
    blocked = blocked,
    islandEnabled = islandEnabled,
    islandFocusNotification = islandFocusNotification,
    registeredType = registeredType,
    existServices = existServices,
    appName = appName,
    appNamePinYin = appNamePinYin,
    lastReceiveTimeMs = lastReceiveTimeMs,
)

private fun ManagerApplicationDetailDto.toManagerApplication(): ManagerApplication = ManagerApplication(
    id = id,
    packageName = packageName,
    type = type,
    notificationOnRegister = notificationOnRegister,
    blocked = blocked,
    islandEnabled = islandEnabled,
    islandFocusNotification = islandFocusNotification,
    registeredType = registeredType,
    existServices = existServices,
    appName = appName,
    appNamePinYin = appNamePinYin,
    lastReceiveTimeMs = lastReceiveTimeMs,
)

private fun ManagerApplicationDiagnosticsDto.toManagerApplicationDiagnostics(): ManagerApplicationDiagnostics =
    ManagerApplicationDiagnostics(
        hasLocalRegistration = hasLocalRegistration,
        regSecCount = regSecCount,
        latestRegistrationEventResult = latestRegistrationEventResult,
        registeredType = registeredType,
        inferenceReason = inferenceReason,
    )

private inline fun <T, R> mapRemoteResult(
    result: ManagerRuntimeResult<T>,
    mapper: (T) -> R,
): ApplicationReadResult<R> = when (result) {
    is ManagerRuntimeResult.Success -> ApplicationReadResult.Available(mapper(result.value))
    is ManagerRuntimeResult.Unsupported -> ApplicationReadResult.Unavailable(ApplicationReadStatus.UNSUPPORTED)
    is ManagerRuntimeResult.Unavailable -> ApplicationReadResult.Unavailable(
        result.availability.toApplicationReadStatus(),
    )

    is ManagerRuntimeResult.Failed -> ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
}

private suspend inline fun <T> safelyLoadRemote(
    loader: suspend () -> ApplicationReadResult<T>,
): ApplicationReadResult<T> = try {
    loader()
} catch (error: CancellationException) {
    throw error
} catch (_: RuntimeException) {
    ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
}

private fun ManagerRuntimeAvailability.toApplicationReadStatus(): ApplicationReadStatus = when (this) {
    ManagerRuntimeAvailability.Disconnected -> ApplicationReadStatus.DISCONNECTED
    ManagerRuntimeAvailability.Binding -> ApplicationReadStatus.BINDING
    ManagerRuntimeAvailability.RuntimeMissing -> ApplicationReadStatus.RUNTIME_MISSING
    ManagerRuntimeAvailability.PermissionDenied -> ApplicationReadStatus.PERMISSION_DENIED
    ManagerRuntimeAvailability.TimedOut -> ApplicationReadStatus.TIMED_OUT
    is ManagerRuntimeAvailability.Incompatible -> ApplicationReadStatus.INCOMPATIBLE
    is ManagerRuntimeAvailability.TemporarilyDisconnected -> ApplicationReadStatus.TEMPORARILY_DISCONNECTED
    is ManagerRuntimeAvailability.Available,
    is ManagerRuntimeAvailability.Failed,
    -> ApplicationReadStatus.FAILED
}
