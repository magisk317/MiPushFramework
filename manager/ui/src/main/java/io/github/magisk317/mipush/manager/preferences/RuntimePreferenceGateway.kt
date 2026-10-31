package io.github.magisk317.mipush.manager.preferences

import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.data.PreferenceOwner
import io.github.magisk317.mipush.data.PreferenceOwnership
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.api.ManagerPreferenceEntryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.manager.client.ManagerRuntimeCallScheduler
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
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

/**
 * Why a runtime preference write did or did not take effect.
 *
 * Rejected and Unavailable must stay distinct: an older runtime package refuses an unknown key
 * while its binder is perfectly connected, and reporting that as a lost connection sends the user
 * off to restart a service that is already serving them.
 */
enum class RuntimePreferenceWriteOutcome {
    Applied,
    Rejected,
    Unavailable,
    ;

    val isApplied: Boolean get() = this == Applied
}

class RuntimePreferenceGateway internal constructor(
    private val runtimeAvailable: Flow<Boolean>,
    private val executeRemote: suspend (RuntimePreferenceWrite) -> ManagerWriteResultDto?,
    private val readRemote: suspend () -> ManagerRuntimeResult<ManagerRuntimePreferencesDto>,
    private val importLocal: suspend (List<OwnedPreferenceValue>) -> Unit,
    private val callScheduler: ManagerRuntimeCallScheduler? = null,
) {
    constructor(
        client: ManagerRuntimeClient,
        preferenceRepository: PreferenceRepository,
        callScheduler: ManagerRuntimeCallScheduler? = null,
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
        callScheduler = callScheduler,
    )

    private val operationMutex = Mutex()

    suspend fun setBoolean(key: String, value: Boolean): Boolean =
        setBooleanWithOutcome(key, value).isApplied

    /** Same path as [setBoolean] but keeps the reason so the UI can tell a rejection from a lost runtime. */
    suspend fun setBooleanWithOutcome(key: String, value: Boolean): RuntimePreferenceWriteOutcome = write(
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
        ).isApplied

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
        ).isApplied
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
        ).isApplied
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
        ).isApplied
    }

    suspend fun getXmppServer(): String? = operationMutex.withLock {
        val snapshot = when (val result = scheduledRead()) {
            is ManagerRuntimeResult.Success -> result.value
            is ManagerRuntimeResult.Unsupported -> throw RuntimeReadUnavailableException(
                status = "unsupported:${result.capability}", operation = "getXmppServer",
            )
            is ManagerRuntimeResult.Unavailable -> throw RuntimeReadUnavailableException(
                status = result.availability.toString(), operation = "getXmppServer",
            )
            is ManagerRuntimeResult.Failed -> throw RuntimeReadUnavailableException(
                status = result.reason, operation = "getXmppServer",
            )
        }
        snapshot.entries.firstOrNull { it.key == XMPP_SERVER_KEY }?.value?.takeIf(String::isNotBlank)
    }

    fun startReconnectSync(scope: CoroutineScope): Job = scope.launch {
        runtimeAvailable
            .distinctUntilChanged()
            .filter { it }
            .collect { syncFromRuntime() }
    }

    suspend fun syncFromRuntime(): Boolean = operationMutex.withLock {
        val snapshot = when (val result = scheduledRead()) {
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

    private suspend fun write(write: RuntimePreferenceWrite): RuntimePreferenceWriteOutcome =
        operationMutex.withLock {
            if (PreferenceOwnership.ownerOf(write.key) != PreferenceOwner.RUNTIME) {
                logW("preference write refused, key is not runtime-owned key=${write.key}")
                return@withLock RuntimePreferenceWriteOutcome.Rejected
            }
            val result = scheduledWrite(write)
            if (result == null) {
                logW("preference write has no runtime answer operation=${write.operation} key=${write.key}")
                return@withLock RuntimePreferenceWriteOutcome.Unavailable
            }
            if (!RemoteWriteSupport.isSuccess(result)) {
                logW(
                    "runtime rejected preference write key=${write.key} " +
                        "status=${result.status} details=${result.details}",
                )
                return@withLock RuntimePreferenceWriteOutcome.Rejected
            }
            val mirrored = runCatching {
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
            }
            if (mirrored.isFailure) {
                logW("preference local mirror failed key=${write.key}: ${mirrored.exceptionOrNull()?.message}")
                return@withLock RuntimePreferenceWriteOutcome.Rejected
            }
            RuntimePreferenceWriteOutcome.Applied
        }

    private suspend fun scheduledRead(): ManagerRuntimeResult<ManagerRuntimePreferencesDto> {
        val scheduler = callScheduler ?: return readRemote()
        return when (val result = scheduler.call(
            operation = "runtime_preferences",
            budget = PageRemoteCallPolicy.visiblePage,
        ) { readRemote() }) {
            is ManagerRuntimeCallResult.Success -> result.value
            is ManagerRuntimeCallResult.Unavailable -> ManagerRuntimeResult.Unavailable(result.availability)
            is ManagerRuntimeCallResult.Busy,
            is ManagerRuntimeCallResult.Timeout,
            is ManagerRuntimeCallResult.Cancelled,
            is ManagerRuntimeCallResult.Stale,
            is ManagerRuntimeCallResult.ValidationFailed,
            -> ManagerRuntimeResult.Failed("runtime_read_unavailable")
        }
    }

    private suspend fun scheduledWrite(write: RuntimePreferenceWrite): ManagerWriteResultDto? {
        val scheduler = callScheduler ?: return executeRemote(write)
        return when (val result = scheduler.call(
            operation = write.operation,
            budget = PageRemoteCallPolicy.userAction,
        ) { executeRemote(write) }) {
            is ManagerRuntimeCallResult.Success -> result.value
            else -> null
        }
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
