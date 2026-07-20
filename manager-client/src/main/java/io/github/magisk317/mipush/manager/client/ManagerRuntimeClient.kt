package io.github.magisk317.mipush.manager.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private data class SnapshotTarget(
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
        } catch (error: IllegalStateException) {
            BindResult.Failure(ManagerRuntimeAvailability.Failed(error.reason()))
        } catch (error: RuntimeException) {
            BindResult.Failure(ManagerRuntimeAvailability.Failed(error.reason()))
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

    @Suppress("TooGenericExceptionCaught")
    suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto> {
        val target = synchronized(lock) {
            val state = _availability.value
            val session = activeSession
            val handshake = (state as? ManagerRuntimeAvailability.Available)?.handshake
            if (session == null || handshake == null || session.service == null) {
                null
            } else {
                SnapshotTarget(session, session.service!!, handshake)
            }
        }
        if (target == null) {
            return ManagerRuntimeResult.Unavailable(availability.value)
        }
        if (ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT !in target.handshake.supportedCapabilities) {
            return ManagerRuntimeResult.Unsupported(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT)
        }

        return try {
            val snapshot = callRemote(target.session) { target.service.connectionSnapshot }
            val validationReason = ManagerProtocol.validateConnectionSnapshot(snapshot)
            if (validationReason != null) {
                val current = releaseSession(
                    target.session,
                    ManagerRuntimeAvailability.Failed(validationReason),
                )
                return if (current) {
                    ManagerRuntimeResult.Failed(validationReason)
                } else {
                    ManagerRuntimeResult.Unavailable(availability.value)
                }
            }
            val stillCurrent = synchronized(lock) {
                isCurrentLocked(target.session) && target.session.service === target.service
            }
            if (stillCurrent) {
                ManagerRuntimeResult.Success(snapshot)
            } else {
                ManagerRuntimeResult.Unavailable(availability.value)
            }
        } catch (_: SecurityException) {
            val current = releaseSession(target.session, ManagerRuntimeAvailability.PermissionDenied)
            if (!current) {
                ManagerRuntimeResult.Unavailable(availability.value)
            } else {
                ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.PermissionDenied)
            }
        } catch (error: RemoteCallTimeoutException) {
            val timeoutState = ManagerRuntimeAvailability.TimedOut
            val current = releaseSession(target.session, timeoutState)
            if (current && !error.permitsExhausted) scheduleReconnect()
            ManagerRuntimeResult.Unavailable(if (current) timeoutState else availability.value)
        } catch (_: DeadObjectException) {
            val current = releaseSession(
                target.session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED),
            )
            if (current) scheduleReconnect()
            ManagerRuntimeResult.Unavailable(availability.value)
        } catch (error: RemoteException) {
            val current = releaseSession(
                target.session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR),
            )
            if (current) scheduleReconnect()
            if (current) ManagerRuntimeResult.Failed(error.reason()) else ManagerRuntimeResult.Unavailable(availability.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            val current = releaseSession(
                target.session,
                ManagerRuntimeAvailability.Failed(error.reason()),
            )
            if (current) ManagerRuntimeResult.Failed(error.reason()) else ManagerRuntimeResult.Unavailable(availability.value)
        }
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
                _availability.value = nextAvailability
                session.handshakeJob = null
                if (nextAvailability is ManagerRuntimeAvailability.Available) {
                    reconnectAttempt = 0
                }
            }
        } catch (error: RemoteCallTimeoutException) {
            val current = releaseSession(session, ManagerRuntimeAvailability.TimedOut)
            if (current && !error.permitsExhausted) scheduleReconnect()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            releaseSession(session, ManagerRuntimeAvailability.PermissionDenied)
        } catch (_: DeadObjectException) {
            val current = releaseSession(
                session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED),
            )
            if (current) scheduleReconnect()
        } catch (_: RemoteException) {
            val current = releaseSession(
                session,
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR),
            )
            if (current) scheduleReconnect()
        } catch (error: RuntimeException) {
            releaseSession(session, ManagerRuntimeAvailability.Failed(error.reason()))
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

    private suspend fun <T> callRemote(session: BindSession, block: () -> T): T {
        if (!remoteCallPermits.tryAcquire()) {
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
            withTimeoutOrNull(callTimeoutMillis) {
                RemoteCallValue(deferred.await())
            }?.value ?: throw RemoteCallTimeoutException(permitsExhausted = false)
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

    private fun Throwable.reason(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private companion object {
        const val DEFAULT_CALL_TIMEOUT_MS = 3_000L
        const val MAX_IN_FLIGHT_REMOTE_CALLS = 2
    }
}
