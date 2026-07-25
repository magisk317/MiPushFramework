package io.github.magisk317.mipush.manager.client

import io.github.magisk317.xposed.logging.MagiskOtel
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto
import io.github.magisk317.mipush.manager.api.ManagerMigrationSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

class ManagerRuntimeClient(
    context: Context,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val callTimeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MS,
    private val reconnectDelayProvider: (Int) -> Long = ManagerRuntimeClientPolicy::reconnectDelayMillis,
) : Closeable {
    private val appContext = context.applicationContext ?: context
    private val clientJob = SupervisorJob(scope.coroutineContext[Job])
    private val clientScope = CoroutineScope(scope.coroutineContext + clientJob)
    private val remoteCallPermits = Semaphore(MAX_IN_FLIGHT_REMOTE_CALLS)
    private val lock = Any()
    private val _availability = MutableStateFlow<ManagerRuntimeAvailability>(
        ManagerRuntimeAvailability.Disconnected,
    )
    val availability: StateFlow<ManagerRuntimeAvailability> = _availability.asStateFlow()

    private var activeSession: BindSession? = null
    private var closed = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    init {
        clientScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                close()
            }
        }
    }

    private inner class BindSession {
        var bindReturned = false
        var bindAccepted = false
        var unbound = false
        var released = false
        var binder: IBinder? = null
        var service: IManagerRuntimeService? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var handshakeJob: Job? = null
        var bindTimeoutJob: Job? = null
        val remoteCalls = mutableSetOf<Deferred<*>>()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                handleConnected(this@BindSession, service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                handleTemporaryDisconnect(this@BindSession, DisconnectReason.SERVICE_DISCONNECTED)
            }

            override fun onBindingDied(name: ComponentName) {
                handleTemporaryDisconnect(this@BindSession, DisconnectReason.BINDING_DIED)
            }

            override fun onNullBinding(name: ComponentName) {
                handleTemporaryDisconnect(this@BindSession, DisconnectReason.NULL_BINDING)
            }
        }
    }

    private data class SessionCleanup(
        val connection: ServiceConnection,
        val binder: IBinder?,
        val deathRecipient: IBinder.DeathRecipient?,
        val handshakeJob: Job?,
        val bindTimeoutJob: Job?,
        val remoteCalls: List<Deferred<*>>,
        val shouldUnbind: Boolean,
    )

    private data class RemoteTarget(
        val session: BindSession,
        val service: IManagerRuntimeService,
        val handshake: ManagerHandshake,
    )

    private class RemoteCallTimeoutException(
        val permitsExhausted: Boolean,
    ) : RuntimeException()

    private class RemoteCallValue<T>(
        val value: T,
    )

    @Suppress("TooGenericExceptionCaught")
    fun connect() {
        val session = synchronized(lock) {
            if (closed || activeSession != null || _availability.value == ManagerRuntimeAvailability.Binding) {
                return
            }
            reconnectJob?.cancel()
            reconnectJob = null
            BindSession().also {
                activeSession = it
                Log.i(TAG, "availability ${_availability.value} -> Binding")
                _availability.value = ManagerRuntimeAvailability.Binding
            }
        }
        val intent = Intent(ManagerProtocol.SERVICE_ACTION).setComponent(
            ComponentName(ManagerProtocol.RUNTIME_PACKAGE, ManagerProtocol.RUNTIME_SERVICE_CLASS),
        )
        val bindResult = try {
            BindResult.Success(
                appContext.bindService(intent, session.connection, Context.BIND_AUTO_CREATE),
            )
        } catch (_: SecurityException) {
            BindResult.Failure(ManagerRuntimeAvailability.PermissionDenied)
        } catch (_: IllegalStateException) {
            BindResult.Failure(ManagerRuntimeAvailability.Failed("bind_illegal_state"))
        } catch (_: RuntimeException) {
            BindResult.Failure(ManagerRuntimeAvailability.Failed("bind_failed"))
        }

        when (bindResult) {
            is BindResult.Success -> {
                val state = if (bindResult.accepted) {
                    null
                } else if (isRuntimeInstalled()) {
                    ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BIND_REJECTED)
                } else {
                    ManagerRuntimeAvailability.RuntimeMissing
                }
                finishBind(session, bindResult.accepted, state)
            }

            is BindResult.Failure -> finishBind(session, false, bindResult.state)
        }
    }

    suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto> {
        return callCapability(
            capability = ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            validator = { snapshot, _ -> ManagerProtocol.validateConnectionSnapshot(snapshot) },
        ) { it.connectionSnapshot }
    }

    suspend fun getApplicationPage(
        query: ManagerApplicationQueryDto,
    ): ManagerRuntimeResult<ManagerApplicationPageDto> {
        return callCapability(
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
    ): ManagerRuntimeResult<ManagerApplicationDetailDto?> = callCapability(
        capability = ManagerProtocol.CAPABILITY_APPLICATION_DETAIL,
        requestValidator = { ManagerProtocol.validateApplicationPackageName(packageName) },
        validator = { detail, _ -> detail?.let(ManagerProtocol::validateApplicationDetail) },
    ) { it.getApplicationDetail(packageName, ignoreNotRegistered) }

    suspend fun getApplicationDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ManagerRuntimeResult<ManagerApplicationDiagnosticsDto> = callCapability(
        capability = ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS,
        requestValidator = {
            ManagerProtocol.validateApplicationDiagnosticsRequest(packageName, registeredType)
        },
        validator = { diagnostics, _ -> ManagerProtocol.validateApplicationDiagnostics(diagnostics) },
    ) { it.getApplicationDiagnostics(packageName, registeredType) }

    suspend fun getEventPage(
        query: ManagerEventQueryDto,
    ): ManagerRuntimeResult<ManagerEventPageDto> = callCapability(
        capability = ManagerProtocol.CAPABILITY_EVENT_LIST,
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

    suspend fun getNotificationChannelPage(
        query: ManagerNotificationChannelQueryDto,
    ): ManagerRuntimeResult<ManagerNotificationChannelPageDto> = callCapability(
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
        callCapability(
            capability = ManagerProtocol.CAPABILITY_CONFIGURATION_CATALOG,
            validator = { catalog, _ -> ManagerProtocol.validateConfigurationCatalog(catalog) },
        ) { it.configurationCatalog }

    suspend fun exportRuntimeLogs(): ManagerRuntimeResult<ManagerLogExportResultDto> = callCapability(
        capability = ManagerProtocol.CAPABILITY_LOG_EXPORT,
        // Log zip can collect multi-10MB logs + root lsposed/logcat; 8s default is far too short.
        callTimeoutMillis = LOG_EXPORT_CALL_TIMEOUT_MS,
        validator = { result, _ -> ManagerProtocol.validateLogExportResult(result) },
    ) { it.exportRuntimeLogs() }


    suspend fun getRuntimePreferences(): ManagerRuntimeResult<ManagerRuntimePreferencesDto> =
        callCapability(
            capability = ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES,
            validator = { snapshot, _ -> ManagerProtocol.validateRuntimePreferences(snapshot) },
        ) { it.runtimePreferences }

    suspend fun getManagerMigrationSnapshot(): ManagerRuntimeResult<ManagerMigrationSnapshotDto> =
        callCapability(
            capability = ManagerProtocol.CAPABILITY_MANAGER_MIGRATION_SNAPSHOT,
            validator = { snapshot, _ -> ManagerProtocol.validateManagerMigrationSnapshot(snapshot) },
        ) { it.managerMigrationSnapshot }

    suspend fun uploadConfiguration(
        request: ManagerConfigurationUploadRequestDto,
    ): ManagerRuntimeResult<ManagerConfigurationUploadResultDto> = callCapability(
        capability = ManagerProtocol.CAPABILITY_CONFIGURATION_UPLOAD,
        requestValidator = { ManagerProtocol.validateConfigurationUploadRequest(request) },
        validator = { result, _ -> ManagerProtocol.validateConfigurationUploadResult(result) },
    ) { it.uploadConfiguration(request) }

    suspend fun executeWrite(
        request: ManagerWriteRequestDto,
    ): ManagerRuntimeResult<ManagerWriteResultDto> = callCapability(
        capability = ManagerProtocol.CAPABILITY_WRITE_COMMANDS,
        requestValidator = { ManagerProtocol.validateWriteRequest(request) },
        validator = { result, _ -> ManagerProtocol.validateWriteResult(result) },
    ) { it.executeWrite(request) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> callCapability(
        capability: String,
        requestValidator: (ManagerHandshake) -> String? = { null },
        callTimeoutMillis: Long = this.callTimeoutMillis,
        validator: (T, ManagerHandshake) -> String?,
        block: (IManagerRuntimeService) -> T,
    ): ManagerRuntimeResult<T> {
        val target = currentRemoteTarget()
        if (target == null) {
            Log.w(TAG, "call without target capability=$capability availability=${availability.value}")
            emitClientCall(result = "skip", reason = "no_target", capability = capability)
            return ManagerRuntimeResult.Unavailable(availability.value)
        }
        if (capability !in target.handshake.supportedCapabilities) {
            emitClientCall(result = "skip", reason = "unsupported", capability = capability)
            return ManagerRuntimeResult.Unsupported(capability)
        }
        requestValidator(target.handshake)?.let {
            emitClientCall(result = "error", reason = "invalid_request", capability = capability, statusOk = false)
            return ManagerRuntimeResult.Failed(it)
        }

        return try {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            Log.i(TAG, "call start capability=$capability timeoutMs=$callTimeoutMillis")
            val value = callRemote(target.session, callTimeoutMillis) { block(target.service) }
            Log.i(
                TAG,
                "call ok capability=$capability tookMs=${android.os.SystemClock.elapsedRealtime() - startedAt}",
            )
            val validationReason = validator(value, target.handshake)
            if (validationReason != null) {
                discardOwnedWireResources(value)
                emitClientCall(result = "error", reason = "validation_failed", capability = capability, statusOk = false)
                ManagerRuntimeResult.Failed(validationReason)
            } else if (isCurrentTarget(target)) {
                emitClientCall(result = "ok", reason = "success", capability = capability)
                ManagerRuntimeResult.Success(value)
            } else {
                discardOwnedWireResources(value)
                emitClientCall(result = "skip", reason = "stale_target", capability = capability)
                ManagerRuntimeResult.Unavailable(availability.value)
            }
        } catch (_: SecurityException) {
            val current = releaseSession(target.session, ManagerRuntimeAvailability.PermissionDenied)
            if (current) {
                ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.PermissionDenied)
            } else {
                ManagerRuntimeResult.Unavailable(availability.value)
            }
        } catch (error: RemoteCallTimeoutException) {
            if (error.permitsExhausted) {
                // Busy is not a dead session: keep the binder and let callers retry.
                Log.w(TAG, "remote busy capability=$capability availability=${availability.value}")
                return@callCapability ManagerRuntimeResult.Unavailable(
                    ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR),
                )
            }
            Log.w(TAG, "remote timed out capability=$capability")
            val timeoutState = ManagerRuntimeAvailability.TimedOut
            val current = releaseSession(target.session, timeoutState)
            if (current) scheduleReconnect()
            ManagerRuntimeResult.Unavailable(if (current) timeoutState else availability.value)
        } catch (_: DeadObjectException) {
            Log.w(TAG, "remote dead object capability=$capability")
            val current = releaseSession(
                target.session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED),
            )
            if (current) scheduleReconnect()
            ManagerRuntimeResult.Unavailable(availability.value)
        } catch (_: RemoteException) {
            Log.w(TAG, "remote exception capability=$capability")
            val current = releaseSession(
                target.session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR),
            )
            if (current) scheduleReconnect()
            ManagerRuntimeResult.Unavailable(
                if (current) {
                    ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR)
                } else {
                    availability.value
                },
            )
        } catch (error: CancellationException) {
            if (!currentCoroutineContext().isActive) throw error
            ManagerRuntimeResult.Unavailable(availability.value)
        } catch (_: RuntimeException) {
            // A method-level malformed/unsupported response must not tear down unrelated features.
            ManagerRuntimeResult.Failed("runtime_operation_failed")
        }
    }

    private fun emitClientCall(
        result: String,
        reason: String,
        capability: String,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "push.manager",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "manager",
                "stage" to "client_call",
                "reason" to reason,
                "operation" to capability,
            ),
            statusOk = statusOk,
        )
    }


    private fun currentRemoteTarget(): RemoteTarget? = synchronized(lock) {
        val state = _availability.value
        val session = activeSession
        val handshake = (state as? ManagerRuntimeAvailability.Available)?.handshake
        val service = session?.service
        if (session == null || handshake == null || service == null) {
            null
        } else {
            RemoteTarget(session, service, handshake)
        }
    }

    private fun isCurrentTarget(target: RemoteTarget): Boolean = synchronized(lock) {
        isCurrentLocked(target.session) && target.session.service === target.service
    }

    override fun close() {
        val session = synchronized(lock) {
            if (closed) return
            closed = true
            reconnectJob?.cancel()
            reconnectJob = null
            _availability.value = ManagerRuntimeAvailability.Disconnected
            activeSession
        }
        session?.let { releaseSession(it) }
        clientJob.cancel()
    }

    private fun finishBind(
        session: BindSession,
        accepted: Boolean,
        failureState: ManagerRuntimeAvailability?,
    ) {
        var shouldUnbind = false
        var timeoutJob: Job? = null
        var shouldRelease = false
        synchronized(lock) {
            session.bindReturned = true
            session.bindAccepted = accepted
            if (session.released || closed || activeSession !== session) {
                shouldUnbind = markUnbindLocked(session, accepted)
            } else if (!accepted) {
                shouldRelease = true
            } else if (session.service == null) {
                timeoutJob = createBindTimeoutLocked(session)
            }
        }
        if (shouldUnbind) unbind(session)
        timeoutJob?.start()
        if (shouldRelease) {
            val current = releaseSession(session, failureState ?: ManagerRuntimeAvailability.Failed("bind_failed"))
            if (current && failureState is ManagerRuntimeAvailability.TemporarilyDisconnected) {
                scheduleReconnect()
            }
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_bind",
                    "reason" to (failureState?.javaClass?.simpleName ?: "bind_failed"),
                ),
                statusOk = false,
            )
        } else if (accepted) {
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_bind",
                    "reason" to "accepted",
                ),
                statusOk = true,
            )
        }
    }

    private fun handleConnected(session: BindSession, binder: IBinder) {
        val eligible = synchronized(lock) { isCurrentLocked(session) }
        if (!eligible) return
        val service = IManagerRuntimeService.Stub.asInterface(binder)
        if (service == null) {
            handleTemporaryDisconnect(session, DisconnectReason.NULL_BINDING)
            return
        }
        val recipient = IBinder.DeathRecipient {
            handleTemporaryDisconnect(session, DisconnectReason.BINDER_DIED)
        }
        try {
            binder.linkToDeath(recipient, 0)
        } catch (_: RemoteException) {
            handleTemporaryDisconnect(session, DisconnectReason.BINDER_DIED)
            return
        } catch (_: SecurityException) {
            handleTemporaryDisconnect(session, DisconnectReason.BINDER_DIED)
            return
        } catch (_: IllegalStateException) {
            handleTemporaryDisconnect(session, DisconnectReason.BINDER_DIED)
            return
        }

        var handshakeJob: Job? = null
        var oldTimeoutJob: Job? = null
        var stale = false
        var duplicate = false
        synchronized(lock) {
            if (!isCurrentLocked(session)) {
                stale = true
            } else if (session.service != null) {
                duplicate = true
            } else {
                oldTimeoutJob = session.bindTimeoutJob
                session.binder = binder
                session.service = service
                session.deathRecipient = recipient
                session.bindTimeoutJob = null
                handshakeJob = clientScope.launch(start = CoroutineStart.LAZY) {
                    performHandshake(session, service)
                }
                session.handshakeJob = handshakeJob
            }
        }
        if (stale) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
            return
        }
        if (duplicate) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
            handleTemporaryDisconnect(session, DisconnectReason.REMOTE_ERROR)
            return
        }
        oldTimeoutJob?.cancel()
        handshakeJob?.start()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun performHandshake(session: BindSession, service: IManagerRuntimeService) {
        try {
            val handshake = callRemote(session) {
                service.handshake(ManagerProtocol.MAJOR, ManagerProtocol.MINOR)
            }
            val nextAvailability = ManagerRuntimeClientPolicy.classifyHandshake(handshake)
            synchronized(lock) {
                if (!isCurrentLocked(session) || session.service !== service) return
                Log.i(TAG, "handshake result $nextAvailability")
                _availability.value = nextAvailability
                session.handshakeJob = null
                if (nextAvailability is ManagerRuntimeAvailability.Available) {
                    reconnectAttempt = 0
                }
            }
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to if (nextAvailability is ManagerRuntimeAvailability.Available) "ok" else "skip",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to nextAvailability.javaClass.simpleName,
                ),
                statusOk = true,
            )
        } catch (error: RemoteCallTimeoutException) {
            // Handshake never reached Available; release and reconnect for both busy and hard timeout.
            android.util.Log.w(
                "ManagerRuntime",
                if (error.permitsExhausted) "handshake busy" else "handshake timed out",
            )
            val current = releaseSession(session, ManagerRuntimeAvailability.TimedOut)
            if (current) scheduleReconnect()
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to if (error.permitsExhausted) "busy" else "timeout",
                ),
                statusOk = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            releaseSession(session, ManagerRuntimeAvailability.PermissionDenied)
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to "permission_denied",
                ),
                statusOk = false,
            )
        } catch (_: DeadObjectException) {
            val current = releaseSession(
                session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED),
            )
            if (current) scheduleReconnect()
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to "binder_died",
                ),
                statusOk = false,
            )
        } catch (_: RemoteException) {
            val current = releaseSession(
                session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR),
            )
            if (current) scheduleReconnect()
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to "remote_error",
                ),
                statusOk = false,
            )
        } catch (_: RuntimeException) {
            releaseSession(session, ManagerRuntimeAvailability.Failed("handshake_failed"))
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "manager",
                    "stage" to "client_handshake",
                    "reason" to "handshake_failed",
                ),
                statusOk = false,
            )
        }
    }

    private fun handleTemporaryDisconnect(session: BindSession, reason: DisconnectReason) {
        val current = releaseSession(
            session,
            ManagerRuntimeAvailability.TemporarilyDisconnected(reason),
        )
        if (current) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        lateinit var scheduledJob: Job
        synchronized(lock) {
            if (closed || activeSession != null || reconnectJob?.isActive == true) return
            val delayMillis = reconnectDelayProvider(reconnectAttempt)
            reconnectAttempt += 1
            scheduledJob = clientScope.launch(start = CoroutineStart.LAZY) {
                delay(delayMillis)
                synchronized(lock) {
                    if (reconnectJob === scheduledJob) reconnectJob = null
                }
                connect()
            }
            reconnectJob = scheduledJob
        }
        scheduledJob.start()
    }

    private fun createBindTimeoutLocked(session: BindSession): Job {
        lateinit var timeoutJob: Job
        timeoutJob = clientScope.launch(start = CoroutineStart.LAZY) {
            delay(callTimeoutMillis)
            val released = releaseSession(
                session = session,
                nextAvailability = ManagerRuntimeAvailability.TimedOut,
                expectedBindTimeoutJob = timeoutJob,
            )
            if (released) scheduleReconnect()
        }
        session.bindTimeoutJob = timeoutJob
        return timeoutJob
    }

    private fun releaseSession(
        session: BindSession,
        nextAvailability: ManagerRuntimeAvailability? = null,
        expectedBindTimeoutJob: Job? = null,
    ): Boolean {
        val cleanup: SessionCleanup
        val current: Boolean
        synchronized(lock) {
            if (session.released) return false
            if (
                expectedBindTimeoutJob != null &&
                (session.bindTimeoutJob !== expectedBindTimeoutJob || session.service != null)
            ) {
                return false
            }
            current = activeSession === session
            session.released = true
            if (current) {
                activeSession = null
                if (!closed && nextAvailability != null) {
                    Log.i(TAG, "availability ${_availability.value} -> $nextAvailability")
                    _availability.value = nextAvailability
                }
            }
            cleanup = SessionCleanup(
                connection = session.connection,
                binder = session.binder,
                deathRecipient = session.deathRecipient,
                handshakeJob = session.handshakeJob,
                bindTimeoutJob = session.bindTimeoutJob,
                remoteCalls = session.remoteCalls.toList(),
                shouldUnbind = session.bindReturned && session.bindAccepted && !session.unbound,
            )
            if (cleanup.shouldUnbind) session.unbound = true
            session.binder = null
            session.service = null
            session.deathRecipient = null
            session.handshakeJob = null
            session.bindTimeoutJob = null
            session.remoteCalls.clear()
        }
        cleanup.handshakeJob?.cancel()
        cleanup.bindTimeoutJob?.cancel()
        cleanup.remoteCalls.forEach { it.cancel() }
        if (cleanup.binder != null && cleanup.deathRecipient != null) {
            runCatching { cleanup.binder.unlinkToDeath(cleanup.deathRecipient, 0) }
        }
        if (cleanup.shouldUnbind) unbind(session)
        return current
    }

    private fun markUnbindLocked(session: BindSession, accepted: Boolean): Boolean {
        if (!accepted || !session.bindAccepted || session.unbound) return false
        session.unbound = true
        return true
    }

    private fun unbind(session: BindSession) {
        runCatching { appContext.unbindService(session.connection) }
    }

    private fun isCurrentLocked(session: BindSession): Boolean =
        !closed && !session.released && activeSession === session

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun <T> callRemote(
        session: BindSession,
        callTimeoutMillis: Long = this.callTimeoutMillis,
        block: () -> T,
    ): T {
        val acquired = if (remoteCallPermits.tryAcquire()) {
            true
        } else {
            Log.w(TAG, "waiting for remote permit timeoutMs=$callTimeoutMillis")
            withTimeoutOrNull(callTimeoutMillis) {
                remoteCallPermits.acquire()
                true
            } == true
        }
        if (!acquired) {
            throw RemoteCallTimeoutException(permitsExhausted = true)
        }
        val deferred: Deferred<T> = clientScope.async(ioDispatcher, start = CoroutineStart.LAZY) { block() }
        deferred.invokeOnCompletion { remoteCallPermits.release() }
        val registered = synchronized(lock) {
            if (isCurrentLocked(session)) {
                session.remoteCalls += deferred
                true
            } else {
                false
            }
        }
        if (!registered) {
            deferred.cancel()
            throw CancellationException("manager runtime session is no longer active")
        }
        if (!deferred.start()) {
            synchronized(lock) { session.remoteCalls -= deferred }
            throw CancellationException("manager runtime client is no longer active")
        }
        return try {
            val completed = withTimeoutOrNull(callTimeoutMillis) {
                RemoteCallValue(deferred.await())
            }
            if (completed == null) {
                // If the remote finished after the client timed out, drop any transferred FDs.
                deferred.invokeOnCompletion { error ->
                    if (error == null) {
                        discardOwnedWireResources(runCatching { deferred.getCompleted() }.getOrNull())
                    }
                }
                throw RemoteCallTimeoutException(permitsExhausted = false)
            }
            completed.value
        } finally {
            synchronized(lock) { session.remoteCalls -= deferred }
            deferred.cancel()
        }
    }

    @Suppress("DEPRECATION")
    private fun isRuntimeInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getApplicationInfo(
                ManagerProtocol.RUNTIME_PACKAGE,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            appContext.packageManager.getApplicationInfo(ManagerProtocol.RUNTIME_PACKAGE, 0)
        }
    }.isSuccess

    private sealed interface BindResult {
        data class Success(val accepted: Boolean) : BindResult
        data class Failure(val state: ManagerRuntimeAvailability) : BindResult
    }

    private fun discardOwnedWireResources(value: Any?) {
        when (value) {
            is ManagerLogExportResultDto ->
                runCatching { value.parcelFileDescriptor?.close() }
        }
    }

    private companion object {
        private const val TAG = "ManagerRuntime"
        // Application list paging + concurrent overview/event loads need headroom on mid-range devices.
        const val DEFAULT_CALL_TIMEOUT_MS = 8_000L
        // Observed live export ~137s with ~30MB runtime logs + root lsposed/logcat collection.
        const val LOG_EXPORT_CALL_TIMEOUT_MS = 180_000L
        const val MAX_IN_FLIGHT_REMOTE_CALLS = 6
    }
}
