package io.github.magisk317.mipush.manager.runtime

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationStatsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.runtime.read.AndroidManagerApplicationReadSource
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadDiagnostics
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadPage
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadQuery
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadStats
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationRuntimeReader
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerEventSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelGroupSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelSummaryDto
import io.github.magisk317.mipush.manager.runtime.read.ManagerConfigurationCatalogRuntimeReader
import io.github.magisk317.mipush.manager.runtime.read.ManagerEventReadPage
import io.github.magisk317.mipush.manager.runtime.read.ManagerEventReadQuery
import io.github.magisk317.mipush.manager.runtime.read.ManagerEventReadSummary
import io.github.magisk317.mipush.manager.runtime.read.ManagerEventRuntimeReader
import io.github.magisk317.mipush.manager.runtime.read.ManagerLogExportRuntimeReader
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto
import io.github.magisk317.mipush.manager.api.ManagerMigrationSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.runtime.read.ManagerConfigurationUploadRuntimeWriter
import io.github.magisk317.mipush.manager.runtime.read.ManagerPreferenceRuntimeReader
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.runtime.write.ManagerWriteRuntimeExecutor
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.manager.runtime.read.ManagerNotificationChannelReadPage
import io.github.magisk317.mipush.manager.runtime.read.ManagerNotificationChannelReadQuery
import io.github.magisk317.mipush.manager.runtime.read.ManagerNotificationChannelRuntimeReader
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter

class ManagerRuntimeService : Service() {
    private val runtimeSettingsAdapter: RuntimeSettingsAdapter by lazy {
        AppDependencies.get(this)
    }
    private val applicationReader by lazy {
        ManagerApplicationRuntimeReader(
            source = AndroidManagerApplicationReadSource(this),
            maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
        )
    }
    private val eventReader by lazy { ManagerEventRuntimeReader(this) }
    private val notificationChannelReader by lazy { ManagerNotificationChannelRuntimeReader() }
    private val configurationCatalogReader by lazy { ManagerConfigurationCatalogRuntimeReader(this) }
    private val logExportReader by lazy { ManagerLogExportRuntimeReader(this) }
    private val preferenceReader by lazy { ManagerPreferenceRuntimeReader(this) }
    private val configurationUploadWriter by lazy { ManagerConfigurationUploadRuntimeWriter(this) }
    private val writeExecutor by lazy { ManagerWriteRuntimeExecutor(this) }

    private val binder = object : IManagerRuntimeService.Stub() {
        override fun handshake(clientMajor: Int, clientMinor: Int): ManagerHandshake {
            enforceTrustedCaller()
            val started = android.os.SystemClock.elapsedRealtime()
            return withRuntimeIdentity {
                val compatibility = ManagerProtocol.evaluateCompatibility(
                    clientMajor = clientMajor,
                    clientMinor = clientMinor,
                )
                val runtimeVersion = runtimePackageVersion()
                ManagerHandshake(
                    protocolMajor = ManagerProtocol.MAJOR,
                    protocolMinor = ManagerProtocol.MINOR,
                    runtimeVersionName = runtimeVersion.name,
                    runtimeVersionCode = runtimeVersion.code,
                    supportedCapabilities = if (compatibility.isCompatible) {
                        ManagerProtocol.KNOWN_CAPABILITIES.sorted()
                    } else {
                        emptyList()
                    },
                    maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
                    compatibilityReason = compatibility.reason,
                ).also { handshake ->
                    val durationMs = android.os.SystemClock.elapsedRealtime() - started
                    logI(
                        "ManagerRuntime handshake client=$clientMajor.$clientMinor " +
                            "compat=${handshake.compatibilityReason} " +
                            "caps=${handshake.supportedCapabilities.size} " +
                            "tookMs=$durationMs",
                    )
                    MagiskOtel.event(
                        name = "push.manager",
                        attributes = mapOf(
                            "result" to if (compatibility.isCompatible) "ok" else "skip",
                            "duration_ms" to durationMs.toString(),
                            "process" to "xmsf",
                            "stage" to "handshake",
                            "reason" to (handshake.compatibilityReason?.ifBlank { "handshake" } ?: "handshake"),
                            "found_count" to handshake.supportedCapabilities.size.toString(),
                        ),
                        statusOk = true,
                    )
                }
            }
        }

        override fun getConnectionSnapshot(): ManagerConnectionSnapshotDto {
            enforceTrustedCaller()
            val started = android.os.SystemClock.elapsedRealtime()
            return withRuntimeIdentity {
                runtimeSettingsAdapter.getConnectionSnapshot().toWireDto().also { snapshot ->
                    logI(
                        "ManagerRuntime getConnectionSnapshot state=${snapshot.connectionState} " +
                            "host=${snapshot.serverHost.orEmpty()} " +
                            "tookMs=${android.os.SystemClock.elapsedRealtime() - started}",
                    )
                }
            }
        }

        override fun getApplicationPage(query: ManagerApplicationQueryDto): ManagerApplicationPageDto {
            enforceTrustedCaller()
            ManagerProtocol.validateApplicationQuery(
                query = query,
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            )?.let(::invalidArgument)
            val started = android.os.SystemClock.elapsedRealtime()
            return withRuntimeIdentity {
                applicationReader.readPage(query.toReadQuery()).toWireDto().also { page ->
                    ManagerProtocol.validateApplicationPage(
                        page = page,
                        negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    )?.let(::invalidArgument)
                    logI(
                        "ManagerRuntime getApplicationPage items=${page.items.size} " +
                            "using=${page.stats.usingMiPush} total=${page.stats.total} " +
                            "token=${!page.nextPageToken.isNullOrBlank()} " +
                            "tookMs=${android.os.SystemClock.elapsedRealtime() - started}",
                    )
                }
            }
        }

        override fun getApplicationDetail(
            packageName: String,
            ignoreNotRegistered: Boolean,
        ): ManagerApplicationDetailDto? {
            enforceTrustedCaller()
            requireValidPackageName(packageName)
            return withRuntimeIdentity {
                applicationReader.readDetail(packageName, ignoreNotRegistered)?.toDetailDto()?.also { detail ->
                    ManagerProtocol.validateApplicationDetail(detail)?.let(::invalidArgument)
                }
            }
        }

        override fun getApplicationDiagnostics(
            packageName: String,
            registeredType: Int,
        ): ManagerApplicationDiagnosticsDto {
            enforceTrustedCaller()
            requireValidPackageName(packageName)
            ManagerProtocol.validateApplicationDiagnosticsRequest(packageName, registeredType)
                ?.let(::invalidArgument)
            return withRuntimeIdentity {
                applicationReader.readDiagnostics(packageName, registeredType).toWireDto().also { diagnostics ->
                    ManagerProtocol.validateApplicationDiagnostics(diagnostics)?.let(::invalidArgument)
                }
            }
        }

        override fun getEventPage(query: ManagerEventQueryDto): ManagerEventPageDto {
            enforceTrustedCaller()
            ManagerProtocol.validateEventQuery(
                query = query,
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            )?.let(::invalidArgument)
            val started = android.os.SystemClock.elapsedRealtime()
            return withRuntimeIdentity {
                eventReader.readPage(query.toReadQuery()).toWireDto().also { page ->
                    ManagerProtocol.validateEventPage(
                        page = page,
                        negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    )?.let(::invalidArgument)
                    logI(
                        "ManagerRuntime getEventPage items=${page.items.size} " +
                            "lastId=${query.lastId} pkg=${query.packageName.orEmpty()} " +
                            "tookMs=${android.os.SystemClock.elapsedRealtime() - started}",
                    )
                }
            }
        }

        override fun getNotificationChannelPage(
            query: ManagerNotificationChannelQueryDto,
        ): ManagerNotificationChannelPageDto {
            enforceTrustedCaller()
            ManagerProtocol.validateNotificationChannelQuery(
                query = query,
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            )?.let(::invalidArgument)
            return withRuntimeIdentity {
                notificationChannelReader.readPage(query.toReadQuery()).toWireDto().also { page ->
                    ManagerProtocol.validateNotificationChannelPage(
                        page = page,
                        negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    )?.let(::invalidArgument)
                }
            }
        }

        override fun getConfigurationCatalog(): ManagerConfigurationCatalogDto {
            enforceTrustedCaller()
            return withRuntimeIdentity {
                configurationCatalogReader.readCatalog().also { catalog ->
                    ManagerProtocol.validateConfigurationCatalog(catalog)?.let(::invalidArgument)
                }
            }
        }

        override fun exportRuntimeLogs(): ManagerLogExportResultDto {
            enforceTrustedCaller()
            return withRuntimeIdentity {
                logExportReader.export().also { result ->
                    ManagerProtocol.validateLogExportResult(result)?.let(::invalidArgument)
                    MagiskOtel.event(
                        name = "app.monitor",
                        attributes = mapOf(
                            "result" to "ok",
                            "duration_ms" to "0",
                            "process" to "xmsf",
                            "stage" to "log_bundle_export",
                            "reason" to "manager_export",
                        ),
                        statusOk = true,
                    )
                }
            }
        }

        override fun getRuntimePreferences(): ManagerRuntimePreferencesDto {
            enforceTrustedCaller()
            return withRuntimeIdentity {
                preferenceReader.readRuntimePreferences().also {
                    ManagerProtocol.validateRuntimePreferences(it)?.let(::invalidArgument)
                }
            }
        }

        override fun getManagerMigrationSnapshot(): ManagerMigrationSnapshotDto {
            enforceTrustedCaller()
            return withRuntimeIdentity {
                preferenceReader.readManagerMigrationSnapshot().also {
                    ManagerProtocol.validateManagerMigrationSnapshot(it)?.let(::invalidArgument)
                }
            }
        }

        override fun uploadConfiguration(
            request: ManagerConfigurationUploadRequestDto,
        ): ManagerConfigurationUploadResultDto {
            enforceTrustedCaller()
            ManagerProtocol.validateConfigurationUploadRequest(request)?.let(::invalidArgument)
            return withRuntimeIdentity {
                configurationUploadWriter.upload(request).also {
                    ManagerProtocol.validateConfigurationUploadResult(it)?.let(::invalidArgument)
                }
            }
        }

        override fun executeWrite(request: ManagerWriteRequestDto): ManagerWriteResultDto {
            enforceTrustedCaller()
            ManagerProtocol.validateWriteRequest(request)?.let(::invalidArgument)
            return withRuntimeIdentity {
                writeExecutor.execute(request).also {
                    ManagerProtocol.validateWriteResult(it)?.let(::invalidArgument)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val expectedComponent = ComponentName(packageName, ManagerProtocol.RUNTIME_SERVICE_CLASS)
        val bound = binder.takeIf {
            intent?.action == ManagerProtocol.SERVICE_ACTION && intent.component == expectedComponent
        }
        MagiskOtel.event(
            name = "push.manager",
            attributes = mapOf(
                "result" to if (bound != null) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "service_bind",
                "reason" to if (bound != null) "bound" else "rejected",
                "action" to (intent?.action ?: "null"),
            ),
            statusOk = true,
        )
        return bound
    }

    @Suppress("DEPRECATION")
    private fun runtimePackageVersion(): RuntimePackageVersion = runCatching {
        packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
            RuntimePackageVersion(
                name = packageInfo.versionName.orEmpty(),
                code = packageInfo.longVersionCode,
            )
        }
    }.getOrElse {
        RuntimePackageVersion(name = "unknown", code = 0L)
    }

    private fun enforceTrustedCaller() {
        val callingUid = Binder.getCallingUid()
        val runtimeUid = Process.myUid()
        val callerPackages = packageManager.getPackagesForUid(callingUid)?.asList().orEmpty()
        val signaturesMatch = packageManager.checkSignatures(callingUid, runtimeUid) ==
            PackageManager.SIGNATURE_MATCH
        if (
            !ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = callingUid,
                runtimeUid = runtimeUid,
                callerPackages = callerPackages,
                signaturesMatch = signaturesMatch,
            )
        ) {
            throw SecurityException("Caller is not authorized to use the manager runtime service")
        }
    }

    private fun requireValidPackageName(packageName: String) {
        if (
            ManagerProtocol.validateApplicationPackageName(packageName) != null ||
            packageName.any { !it.isLetterOrDigit() && it != '.' && it != '_' }
        ) {
            invalidArgument("invalid_application_package_name")
        }
    }

    private fun invalidArgument(reason: String): Nothing = throw IllegalArgumentException(reason)

    private inline fun <T> withRuntimeIdentity(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private data class RuntimePackageVersion(
        val name: String,
        val code: Long,
    )
}

internal fun ManagerConnectionSnapshot.toWireDto(): ManagerConnectionSnapshotDto =
    ManagerConnectionSnapshotDto(
        connectionState = connectionState,
        connectedAtMs = connectedAtMs,
        lastDisconnectedAtMs = lastDisconnectedAtMs,
        connectionSessionCount = connectionSessionCount,
        serverHost = serverHost,
        serverIp = serverIp,
        keepAliveIntervalMs = keepAliveIntervalMs,
        pingIntervalMs = pingIntervalMs,
        downstreamMessageCount = downstreamMessageCount,
        deliveredToAppCount = deliveredToAppCount,
        duplicateMessageCount = duplicateMessageCount,
        ackMessageCount = ackMessageCount,
        registeredPackageCount = registeredPackageCount,
        trackedChannelCount = trackedChannelCount,
        boundChannelCount = boundChannelCount,
    )

private fun ManagerApplicationQueryDto.toReadQuery(): ManagerApplicationReadQuery =
    ManagerApplicationReadQuery(
        schemaVersion = schemaVersion,
        query = query,
        filterMode = filterMode,
        includeSystemApps = includeSystemApps,
        pageSize = pageSize,
        pageToken = pageToken,
    )

private fun ManagerApplicationReadPage.toWireDto(): ManagerApplicationPageDto =
    ManagerApplicationPageDto(
        items = items.map { it.toSummaryDto() },
        stats = stats.toWireDto(),
        nextPageToken = nextPageToken,
    )

private fun ManagerApplicationReadStats.toWireDto(): ManagerApplicationStatsDto =
    ManagerApplicationStatsDto(
        total = total,
        usingMiPush = usingMiPush,
        notUsingMiPush = notUsingMiPush,
        registered = registered,
        notRegistered = notRegistered,
    )

private fun io.github.magisk317.mipush.common.manager.ManagerApplication.toSummaryDto(): ManagerApplicationSummaryDto =
    ManagerApplicationSummaryDto(
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

private fun io.github.magisk317.mipush.common.manager.ManagerApplication.toDetailDto(): ManagerApplicationDetailDto =
    ManagerApplicationDetailDto(
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

private fun ManagerApplicationReadDiagnostics.toWireDto(): ManagerApplicationDiagnosticsDto =
    ManagerApplicationDiagnosticsDto(
        hasLocalRegistration = hasLocalRegistration,
        regSecCount = regSecCount,
        latestRegistrationEventResult = latestRegistrationEventResult,
        registeredType = registeredType,
        inferenceReason = inferenceReason,
    )


private fun ManagerEventQueryDto.toReadQuery(): ManagerEventReadQuery =
    ManagerEventReadQuery(
        schemaVersion = schemaVersion,
        lastId = lastId,
        pageSize = pageSize,
        packageName = packageName,
        query = query,
    )

private fun ManagerEventReadPage.toWireDto(): ManagerEventPageDto =
    ManagerEventPageDto(items = items.map { it.toWireDto() })

private fun ManagerEventReadSummary.toWireDto(): ManagerEventSummaryDto =
    ManagerEventSummaryDto(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = channel,
        receiveDateMs = receiveDateMs,
        title = title,
        content = content,
        appName = appName,
        type = type,
        result = result,
        info = info,
        payload = payload,
        regSec = regSec,
    )

private fun ManagerNotificationChannelQueryDto.toReadQuery(): ManagerNotificationChannelReadQuery =
    ManagerNotificationChannelReadQuery(
        packageName = packageName,
        pageSize = pageSize,
        pageToken = pageToken,
    )

private fun ManagerNotificationChannelReadPage.toWireDto(): ManagerNotificationChannelPageDto =
    ManagerNotificationChannelPageDto(
        packageName = packageName,
        isHooked = isHooked,
        items = items.map {
            ManagerNotificationChannelSummaryDto(
                id = it.id,
                name = it.name,
                importance = it.importance,
                groupId = it.groupId,
                description = it.description,
                enabled = it.enabled,
                managedByMiPush = it.managedByMiPush,
            )
        },
        groups = groups.map {
            ManagerNotificationChannelGroupSummaryDto(
                id = it.id,
                name = it.name,
                managedByMiPush = it.managedByMiPush,
            )
        },
        nextPageToken = nextPageToken,
    )
