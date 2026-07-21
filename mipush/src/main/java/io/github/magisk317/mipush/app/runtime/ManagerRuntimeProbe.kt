package io.github.magisk317.mipush.app.runtime

import android.content.Context
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ManagerRuntimeProbe : Closeable {
    val availability: StateFlow<ManagerRuntimeAvailability>
    val connectionSnapshot: StateFlow<ManagerRuntimeResult<ManagerConnectionSnapshotDto>>

    fun refreshConnectionSnapshot()
}

object ManagerRuntimeProbeFactory {
    fun start(context: Context): ManagerRuntimeProbe = DefaultManagerRuntimeProbe(
        dispatcher = Dispatchers.IO,
        transportFactory = { scope -> BinderManagerRuntimeTransport(context, scope) },
    )
}

internal interface ManagerRuntimeTransport : Closeable {
    val availability: StateFlow<ManagerRuntimeAvailability>

    fun connect()

    suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto>
}

internal class DefaultManagerRuntimeProbe(
    dispatcher: CoroutineDispatcher,
    transportFactory: (CoroutineScope) -> ManagerRuntimeTransport,
) : ManagerRuntimeProbe {
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + lifecycleJob)
    private val transport = transportFactory(scope)
    private val closed = AtomicBoolean(false)
    private val snapshotMutex = Mutex()
    private val _availability = MutableStateFlow<ManagerRuntimeAvailability>(
        ManagerRuntimeAvailability.Disconnected,
    )
    private val _connectionSnapshot = MutableStateFlow<ManagerRuntimeResult<ManagerConnectionSnapshotDto>>(
        ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.Disconnected),
    )

    override val availability: StateFlow<ManagerRuntimeAvailability> = _availability.asStateFlow()
    override val connectionSnapshot: StateFlow<ManagerRuntimeResult<ManagerConnectionSnapshotDto>> =
        _connectionSnapshot.asStateFlow()

    init {
        scope.launch {
            transport.availability.collectLatest(::handleAvailability)
        }
        scope.launch {
            connectSafely()
        }
    }

    override fun refreshConnectionSnapshot() {
        if (closed.get()) return
        scope.launch {
            refreshSnapshotSafely()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        transport.close()
        lifecycleJob.cancel()
        _availability.value = ManagerRuntimeAvailability.Disconnected
        _connectionSnapshot.value = ManagerRuntimeResult.Unavailable(
            ManagerRuntimeAvailability.Disconnected,
        )
    }

    private suspend fun handleAvailability(next: ManagerRuntimeAvailability) {
        if (closed.get()) return
        _availability.value = next
        if (next is ManagerRuntimeAvailability.Available) {
            refreshSnapshotSafely()
        } else {
            _connectionSnapshot.value = ManagerRuntimeResult.Unavailable(next)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun connectSafely() {
        try {
            transport.connect()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = ManagerRuntimeAvailability.Failed(error.reason())
            _availability.value = failure
            _connectionSnapshot.value = ManagerRuntimeResult.Unavailable(failure)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun refreshSnapshotSafely() {
        snapshotMutex.withLock {
            if (closed.get()) return
            _connectionSnapshot.value = try {
                transport.getConnectionSnapshot()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ManagerRuntimeResult.Failed(error.reason())
            }
        }
    }

    private fun Throwable.reason(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
}

private class BinderManagerRuntimeTransport(
    context: Context,
    scope: CoroutineScope,
) : ManagerRuntimeTransport {
    private val client = ManagerRuntimeClient(context, scope)

    override val availability: StateFlow<ManagerRuntimeAvailability> = client.availability

    override fun connect() {
        client.connect()
    }

    override suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto> =
        client.getConnectionSnapshot()

    override fun close() {
        client.close()
    }
}
