package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerMigrationSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerRuntimeEnvironmentSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto

/** A narrow seam for operation façades; it owns no Binder or session lifecycle. */
internal interface ManagerRuntimeCapabilityCaller {
    suspend fun <T> callCapability(
        capability: String,
        requestValidator: (ManagerHandshake) -> String? = { null },
        callTimeoutMillis: Long? = USE_CLIENT_CALL_TIMEOUT_MILLIS,
        releaseSessionOnTimeout: Boolean = true,
        validator: (T, ManagerHandshake) -> String?,
        block: (IManagerRuntimeService) -> T,
    ): ManagerRuntimeResult<T>
}

/** Delegates operation-specific validation and Binder mapping to the injected client caller. */
internal class ManagerRuntimeOperationFacade(
    private val caller: ManagerRuntimeCapabilityCaller,
    private val eventPageCallTimeoutMillis: Long?,
) {
    suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto> {
        return caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            validator = { snapshot, _ -> ManagerProtocol.validateConnectionSnapshot(snapshot) },
        ) { it.connectionSnapshot }
    }

    suspend fun getRuntimeEnvironmentSnapshot(): ManagerRuntimeResult<ManagerRuntimeEnvironmentSnapshotDto> =
        caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_RUNTIME_ENVIRONMENT,
            validator = { snapshot, _ -> ManagerProtocol.validateRuntimeEnvironmentSnapshot(snapshot) },
        ) { it.getRuntimeEnvironmentSnapshot() }

    suspend fun getApplicationPage(
        query: ManagerApplicationQueryDto,
    ): ManagerRuntimeResult<ManagerApplicationPageDto> {
        return caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_APPLICATION_LIST,
            requestValidator = { handshake ->
                ManagerProtocol.validateApplicationQuery(query, handshake.maxPageSize)
            },
            validator = { page, handshake ->
                ManagerProtocol.validateApplicationPage(
                    page = page,
                    negotiatedMaxPageSize = handshake.maxPageSize,
                    negotiatedMaxPayloadBytes = handshake.maxPayloadBytes,
                )
            },
        ) { it.getApplicationPage(query) }
    }

    suspend fun getApplicationDetail(
        packageName: String,
        ignoreNotRegistered: Boolean = false,
    ): ManagerRuntimeResult<ManagerApplicationDetailDto?> = caller.callCapability(
        capability = ManagerProtocol.CAPABILITY_APPLICATION_DETAIL,
        requestValidator = { ManagerProtocol.validateApplicationPackageName(packageName) },
        validator = { detail, _ -> detail?.let(ManagerProtocol::validateApplicationDetail) },
    ) { it.getApplicationDetail(packageName, ignoreNotRegistered) }

    suspend fun getApplicationDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ManagerRuntimeResult<ManagerApplicationDiagnosticsDto> = caller.callCapability(
        capability = ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS,
        requestValidator = {
            ManagerProtocol.validateApplicationDiagnosticsRequest(packageName, registeredType)
        },
        validator = { diagnostics, _ -> ManagerProtocol.validateApplicationDiagnostics(diagnostics) },
    ) { it.getApplicationDiagnostics(packageName, registeredType) }

    suspend fun getEventPage(query: ManagerEventQueryDto): ManagerRuntimeResult<ManagerEventPageDto> =
        ManagerRuntimeClientPolicy.eventPageCallPolicy(eventPageCallTimeoutMillis).let { operationPolicy ->
            caller.callCapability(
                capability = ManagerProtocol.CAPABILITY_EVENT_LIST,
                callTimeoutMillis = operationPolicy.timeoutMillis,
                releaseSessionOnTimeout = operationPolicy.releaseSessionOnTimeout,
                requestValidator = { handshake ->
                    ManagerProtocol.validateEventQuery(query, handshake.maxPageSize)
                },
                validator = { page, handshake ->
                    ManagerProtocol.validateEventPage(
                        page = page,
                        negotiatedMaxPageSize = handshake.maxPageSize,
                        negotiatedMaxPayloadBytes = handshake.maxPayloadBytes,
                    )
                },
            ) { it.getEventPage(query) }
        }

    suspend fun getNotificationChannelPage(
        query: ManagerNotificationChannelQueryDto,
    ): ManagerRuntimeResult<ManagerNotificationChannelPageDto> = caller.callCapability(
        capability = ManagerProtocol.CAPABILITY_NOTIFICATION_CHANNELS,
        requestValidator = { handshake ->
            ManagerProtocol.validateNotificationChannelQuery(query, handshake.maxPageSize)
        },
        validator = { page, handshake ->
            ManagerProtocol.validateNotificationChannelPage(
                page = page,
                negotiatedMaxPageSize = handshake.maxPageSize,
                negotiatedMaxPayloadBytes = handshake.maxPayloadBytes,
            )
        },
    ) { it.getNotificationChannelPage(query) }

    suspend fun getConfigurationCatalog(): ManagerRuntimeResult<ManagerConfigurationCatalogDto> =
        caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_CONFIGURATION_CATALOG,
            validator = { catalog, _ -> ManagerProtocol.validateConfigurationCatalog(catalog) },
        ) { it.configurationCatalog }

    suspend fun exportRuntimeLogs(): ManagerRuntimeResult<ManagerLogExportResultDto> =
        ManagerRuntimeClientPolicy.logExportCallPolicy().let { operationPolicy ->
            caller.callCapability(
                capability = ManagerProtocol.CAPABILITY_LOG_EXPORT,
                callTimeoutMillis = operationPolicy.timeoutMillis,
                releaseSessionOnTimeout = operationPolicy.releaseSessionOnTimeout,
                validator = { result, _ -> ManagerProtocol.validateLogExportResult(result) },
            ) { it.exportRuntimeLogs() }
        }

    suspend fun getRuntimePreferences(): ManagerRuntimeResult<ManagerRuntimePreferencesDto> =
        caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES,
            validator = { snapshot, _ -> ManagerProtocol.validateRuntimePreferences(snapshot) },
        ) { it.runtimePreferences }

    suspend fun getManagerMigrationSnapshot(): ManagerRuntimeResult<ManagerMigrationSnapshotDto> =
        caller.callCapability(
            capability = ManagerProtocol.CAPABILITY_MANAGER_MIGRATION_SNAPSHOT,
            validator = { snapshot, _ -> ManagerProtocol.validateManagerMigrationSnapshot(snapshot) },
        ) { it.managerMigrationSnapshot }

    suspend fun uploadConfiguration(
        request: ManagerConfigurationUploadRequestDto,
    ): ManagerRuntimeResult<ManagerConfigurationUploadResultDto> = caller.callCapability(
        capability = ManagerProtocol.CAPABILITY_CONFIGURATION_UPLOAD,
        requestValidator = { ManagerProtocol.validateConfigurationUploadRequest(request) },
        validator = { result, _ -> ManagerProtocol.validateConfigurationUploadResult(result) },
    ) { it.uploadConfiguration(request) }

    suspend fun executeWrite(
        request: ManagerWriteRequestDto,
    ): ManagerRuntimeResult<ManagerWriteResultDto> = caller.callCapability(
        capability = ManagerProtocol.CAPABILITY_WRITE_COMMANDS,
        requestValidator = { ManagerProtocol.validateWriteRequest(request) },
        validator = { result, _ -> ManagerProtocol.validateWriteResult(result) },
    ) { it.executeWrite(request) }
}

internal const val USE_CLIENT_CALL_TIMEOUT_MILLIS = Long.MIN_VALUE
