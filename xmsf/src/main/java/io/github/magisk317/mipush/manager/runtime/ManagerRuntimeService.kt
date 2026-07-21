package io.github.magisk317.mipush.manager.runtime

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import io.github.magisk317.mipush.app.di.AppDependencies
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

    private val binder = object : IManagerRuntimeService.Stub() {
        override fun handshake(clientMajor: Int, clientMinor: Int): ManagerHandshake {
            enforceTrustedCaller()
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
                )
            }
        }

        override fun getConnectionSnapshot(): ManagerConnectionSnapshotDto {
            enforceTrustedCaller()
            return withRuntimeIdentity {
                runtimeSettingsAdapter.getConnectionSnapshot().toWireDto()
            }
        }

        override fun getApplicationPage(query: ManagerApplicationQueryDto): ManagerApplicationPageDto {
            enforceTrustedCaller()
            ManagerProtocol.validateApplicationQuery(
                query = query,
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            )?.let(::invalidArgument)
            return withRuntimeIdentity {
                applicationReader.readPage(query.toReadQuery()).toWireDto().also { page ->
                    ManagerProtocol.validateApplicationPage(
                        page = page,
                        negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    )?.let(::invalidArgument)
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
    }

    override fun onBind(intent: Intent?): IBinder? {
        val expectedComponent = ComponentName(packageName, ManagerProtocol.RUNTIME_SERVICE_CLASS)
        return binder.takeIf {
            intent?.action == ManagerProtocol.SERVICE_ACTION && intent.component == expectedComponent
        }
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
