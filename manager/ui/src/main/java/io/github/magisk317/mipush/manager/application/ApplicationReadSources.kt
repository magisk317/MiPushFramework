package io.github.magisk317.mipush.manager.application

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerApplications
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
import io.github.magisk317.mipush.manager.client.RemoteCallBudget
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.remote.PageRemoteCallAdapter
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
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

    suspend fun load(request: ApplicationListRequest): ApplicationListSnapshot {
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
    private val userIdProvider: () -> Int = { Utils.requireValidUserId(Utils.myUserId()) },
    private val pageCallAdapter: PageRemoteCallAdapter? = null,
) {
    constructor(client: ManagerRuntimeClient, pageCallAdapter: PageRemoteCallAdapter? = null) : this(
        pageLoader = client::getApplicationPage,
        pageSizeProvider = {
            val negotiated = (client.availability.value as? ManagerRuntimeAvailability.Available)
                ?.handshake
                ?.maxPageSize
                ?: ManagerProtocol.DEFAULT_MAX_PAGE_SIZE
            minOf(negotiated, ManagerProtocol.DEFAULT_MAX_PAGE_SIZE)
        },
        pageCallAdapter = pageCallAdapter,
    )

    suspend fun load(
        request: ApplicationListRequest,
        budget: RemoteCallBudget = PageRemoteCallPolicy.visiblePage,
    ): ApplicationReadResult<ApplicationListSnapshot> = try {
        loadPages(request, budget)
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
    }

    private suspend fun loadPages(
        request: ApplicationListRequest,
        budget: RemoteCallBudget,
    ): ApplicationReadResult<ApplicationListSnapshot> {
        val pageSize = pageSizeProvider().coerceAtLeast(1)
        val userId = Utils.requireValidUserId(userIdProvider())
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
            val result = loadPage(
                ManagerApplicationQueryDto(
                    query = request.query,
                    filterMode = request.filterMode,
                    includeSystemApps = request.includeSystemApps,
                    pageSize = pageSize,
                    pageToken = token,
                    userId = userId,
                ),
                budget,
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
            if (page.userId != userId || page.items.any { it.userId != userId }) {
                return ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED)
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

    private suspend fun loadPage(
        query: ManagerApplicationQueryDto,
        budget: RemoteCallBudget,
    ): ManagerRuntimeResult<ManagerApplicationPageDto> {
        val adapter = pageCallAdapter ?: return pageLoader(query)
        return when (val scheduled = adapter.call(
            operation = "application_page",
            budget = budget,
        ) { pageLoader(query) }) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Success -> scheduled.value
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Unavailable ->
                ManagerRuntimeResult.Unavailable(scheduled.availability)
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Busy ->
                ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.TemporarilyDisconnected(
                    io.github.magisk317.mipush.manager.client.DisconnectReason.REMOTE_ERROR,
                ))
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Timeout ->
                ManagerRuntimeResult.Failed("runtime_request_timeout")
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Cancelled,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Stale,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.ValidationFailed,
            -> ManagerRuntimeResult.Failed("stale_or_cancelled")
        }
    }

    private companion object {
        const val MAX_PAGE_REQUESTS = 1_000
    }
}

class GatewayApplicationDetailSource(
    context: Context,
    private val applicationGateway: ManagerApplicationGateway,
) {
    private val appContext = context.applicationContext ?: context

    suspend fun load(packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? =
        applicationGateway.getApplication(appContext, packageName, ignoreNotRegistered)

    suspend fun loadDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics =
        applicationGateway.getDiagnostics(packageName, registeredType)
}

class RemoteApplicationDetailSource internal constructor(
    private val detailLoader: suspend (String, Boolean) -> ManagerRuntimeResult<ManagerApplicationDetailDto?>,
    private val diagnosticsLoader: suspend (String, Int) -> ManagerRuntimeResult<ManagerApplicationDiagnosticsDto>,
    private val userIdProvider: () -> Int = { Utils.requireValidUserId(Utils.myUserId()) },
) {
    constructor(client: ManagerRuntimeClient) : this(
        detailLoader = client::getApplicationDetail,
        diagnosticsLoader = client::getApplicationDiagnostics,
    )

    suspend fun load(
        packageName: String,
        ignoreNotRegistered: Boolean,
    ): ApplicationReadResult<ManagerApplication?> = safelyLoadRemote {
        val userId = Utils.requireValidUserId(userIdProvider())
        mapRemoteResult(
            result = detailLoader(packageName, ignoreNotRegistered),
            mapper = { detail ->
                detail?.takeIf { it.userId == userId }?.toManagerApplication()
                    ?: detail?.let { throw IllegalStateException("Application detail user mismatch") }
            },
        )
    }

    suspend fun loadDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ApplicationReadResult<ManagerApplicationDiagnostics> = safelyLoadRemote {
        val userId = Utils.requireValidUserId(userIdProvider())
        mapRemoteResult(
            result = diagnosticsLoader(packageName, registeredType),
            mapper = { diagnostics ->
                if (diagnostics.userId != userId) {
                    throw IllegalStateException("Application diagnostics user mismatch")
                }
                diagnostics.toManagerApplicationDiagnostics()
            },
        )
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
    userId = userId,
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
    userId = userId,
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
        userId = userId,
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
    is ManagerRuntimeAvailability.Available -> ApplicationReadStatus.UNSUPPORTED
    is ManagerRuntimeAvailability.Failed -> ApplicationReadStatus.FAILED
}
