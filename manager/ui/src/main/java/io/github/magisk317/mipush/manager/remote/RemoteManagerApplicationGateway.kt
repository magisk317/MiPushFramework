package io.github.magisk317.mipush.manager.remote

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerRegistrationResult
import io.github.magisk317.mipush.manager.application.ManagerUnregisterResult
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
                    application.clickFallbackEnabled,
                    application.suppressWhenDisabled,
                    application.countLimitExempt,
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
            throw RuntimeWriteUnavailableException(status = "runtime_unavailable", operation = ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER)
        }
        return ManagerForceRegisterResult(
            succeeded = RemoteWriteSupport.isSuccess(result),
            message = result.details.ifBlank { "force_register_completed" },
        )
    }

    override suspend fun unregisterApplication(
        context: Context,
        packageName: String,
    ): ManagerUnregisterResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION,
            packageName = packageName,
        )
        if (result == null) {
            throw RuntimeWriteUnavailableException(status = "runtime_unavailable", operation = ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION)
        }
        return ManagerUnregisterResult(
            succeeded = RemoteWriteSupport.isSuccess(result),
            message = result.details.ifBlank { "unregister_completed" },
        )
    }
    override suspend fun setRegistrationPolicy(
        context: Context,
        packageName: String,
        type: Int,
    ): ManagerRegistrationResult = registrationWrite(
        ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, packageName, type,
    )

    override suspend fun approvePendingRegistration(
        context: Context,
        packageName: String,
    ): ManagerRegistrationResult = registrationWrite(ManagerProtocol.WRITE_OP_APPROVE_REGISTRATION, packageName)

    private suspend fun registrationWrite(
        operation: String,
        packageName: String,
        type: Int = 0,
    ): ManagerRegistrationResult {
        val result = RemoteWriteSupport.execute(
            client = client, operation = operation, packageName = packageName, intArgument = type,
        ) ?: throw RuntimeWriteUnavailableException(status = "runtime_unavailable", operation = operation)
        return ManagerRegistrationResult(
            succeeded = RemoteWriteSupport.isSuccess(result),
            message = result.details.ifBlank { operation },
        )
    }

}
