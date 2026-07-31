package io.github.magisk317.mipush.manager.preferences

import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.data.PreferenceOwner
import io.github.magisk317.mipush.data.PreferenceOwnership
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerPreferenceEntryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RuntimePreferenceWrite(
    val operation: String,
    val key: String,
    val type: String,
    val value: String,
    val intArgument: Int = 0,
    val booleanArgument: Boolean = false,
    val argument: String = key,
)

class RuntimePreferenceGateway internal constructor(
    private val runtimeAvailable: Flow<Boolean>,
    private val executeRemote: suspend (RuntimePreferenceWrite) -> ManagerWriteResultDto?,
    private val readRemote: suspend () -> ManagerRuntimeResult<ManagerRuntimePreferencesDto>,
    private val importLocal: suspend (List<OwnedPreferenceValue>) -> Unit,
) {
    constructor(
        client: ManagerRuntimeClient,
        preferenceRepository: PreferenceRepository,
    ) : this(
        runtimeAvailable = client.availability.map { it is ManagerRuntimeAvailability.Available },
        executeRemote = { write ->
            RemoteWriteSupport.execute(
                client = client,
                operation = write.operation,
                intArgument = write.intArgument,
                booleanArgument = write.booleanArgument,
                argument = write.argument,
            )
        },
        readRemote = client::getRuntimePreferences,
        importLocal = { entries ->
            preferenceRepository.importOwnedPreferences(
                entries = entries,
                owner = PreferenceOwner.RUNTIME,
                onlyMissing = false,
            )
        },
    )

    private val operationMutex = Mutex()

    suspend fun setBoolean(key: String, value: Boolean): Boolean = write(
        RuntimePreferenceWrite(
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_BOOLEAN,
            key = key,
            type = TYPE_BOOLEAN,
            value = value.toString(),
            booleanArgument = value,
        ),
    )

    suspend fun setInt(key: String, value: Int): Boolean = write(
        RuntimePreferenceWrite(
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_INT,
            key = key,
            type = TYPE_INT,
            value = value.toString(),
            intArgument = value,
        ),
    )

    suspend fun setXmppServer(host: String): Boolean {
        val normalizedHost = host.trim()
        return write(
            RuntimePreferenceWrite(
                operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
                key = XMPP_SERVER_KEY,
                type = TYPE_STRING,
                value = normalizedHost,
                argument = normalizedHost,
            ),
        )
    }

    suspend fun setRuntimeLogRetentionDays(days: Int): Boolean {
        val normalizedDays = days.coerceAtLeast(1)
        return write(
            RuntimePreferenceWrite(
                operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
                key = RUNTIME_LOG_RETENTION_DAYS_KEY,
                type = TYPE_INT,
                value = normalizedDays.toString(),
                intArgument = normalizedDays,
                argument = "",
            ),
        )
    }

    suspend fun setEventRetentionDays(days: Int): Boolean {
        val normalizedDays = days.coerceAtLeast(1)
        return write(
            RuntimePreferenceWrite(
                operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
                key = EVENT_RETENTION_DAYS_KEY,
                type = TYPE_INT,
                value = normalizedDays.toString(),
                intArgument = normalizedDays,
                argument = "",
            ),
        )
    }

    fun startReconnectSync(scope: CoroutineScope): Job = scope.launch {
        runtimeAvailable
            .distinctUntilChanged()
            .filter { it }
            .collect { syncFromRuntime() }
    }

    suspend fun syncFromRuntime(): Boolean = operationMutex.withLock {
        val snapshot = when (val result = readRemote()) {
            is ManagerRuntimeResult.Success -> result.value
            is ManagerRuntimeResult.Failed,
            is ManagerRuntimeResult.Unavailable,
            is ManagerRuntimeResult.Unsupported,
            -> return false
        }
        runCatching {
            importLocal(snapshot.entries.map { it.toOwnedRuntimePreference() })
        }.isSuccess
    }

    private suspend fun write(write: RuntimePreferenceWrite): Boolean = operationMutex.withLock {
        if (PreferenceOwnership.ownerOf(write.key) != PreferenceOwner.RUNTIME) return@withLock false
        val result = executeRemote(write)
        if (!RemoteWriteSupport.isSuccess(result)) return@withLock false
        runCatching {
            importLocal(
                listOf(
                    OwnedPreferenceValue(
                        key = write.key,
                        type = write.type,
                        value = write.value,
                        owner = PreferenceOwner.RUNTIME,
                    ),
                ),
            )
        }.isSuccess
    }

    private fun ManagerPreferenceEntryDto.toOwnedRuntimePreference(): OwnedPreferenceValue =
        OwnedPreferenceValue(
            key = key,
            type = type,
            value = value,
            owner = PreferenceOwner.RUNTIME,
        )

    private companion object {
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_INT = "int"
        const val TYPE_STRING = "string"
        const val XMPP_SERVER_KEY = "xmpp_server"
        const val RUNTIME_LOG_RETENTION_DAYS_KEY = "runtime_log_retention_days"
        const val EVENT_RETENTION_DAYS_KEY = "event_retention_days"
    }
}
