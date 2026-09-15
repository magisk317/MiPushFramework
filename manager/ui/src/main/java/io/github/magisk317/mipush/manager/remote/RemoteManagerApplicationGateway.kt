package io.github.magisk317.mipush.manager.remote

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient

class RemoteManagerApplicationGateway(
    private val client: ManagerRuntimeClient,
) : ManagerApplicationGateway {
    private val listSource = RemoteApplicationListSource(client)
    private val detailSource = RemoteApplicationDetailSource(client)

    override suspend fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications {
        return when (
            val result = listSource.load(
                ApplicationListRequest(
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                ),
            )
        ) {
            is ApplicationReadResult.Available -> result.value.applications
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("loadApplications", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "loadApplications",
                )
            }
        }
    }

    override suspend fun getApplication(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean,
    ): ManagerApplication? {
        return when (val result = detailSource.load(packageName, ignoreNotRegistered)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getApplication", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getApplication",
                )
            }
        }
    }

    override suspend fun updateApplication(application: ManagerApplication) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
                packageName = application.packageName,
                argument = listOf(
                    application.type,
                    application.blocked,
                    application.islandEnabled,
                    application.islandFocusNotification,
                    application.notificationOnRegister,
                ).joinToString(","),
            ),
            operation = ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
        )
    }

    override suspend fun getDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ManagerApplicationDiagnostics {
        return when (val result = detailSource.loadDiagnostics(packageName, registeredType)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getDiagnostics", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getDiagnostics",
                )
            }
        }
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER,
            packageName = packageName,
            intArgument = registeredType,
        )
        if (result == null) {
            throw IllegalStateException("${ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER}:null_response")
        }
        return ManagerForceRegisterResult(
            succeeded = RemoteWriteSupport.isSuccess(result),
            message = result.details.ifBlank { "force_register_completed" },
        )
    }
}
