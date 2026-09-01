package io.github.magisk317.mipush.manager.client

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

/** Per-bind callback and resource holder; state transitions remain owned by ManagerRuntimeClient. */
internal class BindSession(
    private val onConnected: (BindSession, IBinder) -> Unit,
    private val onTemporaryDisconnect: (BindSession, DisconnectReason) -> Unit,
) {
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
            onConnected(this@BindSession, service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            onTemporaryDisconnect(this@BindSession, DisconnectReason.SERVICE_DISCONNECTED)
        }

        override fun onBindingDied(name: ComponentName) {
            onTemporaryDisconnect(this@BindSession, DisconnectReason.BINDING_DIED)
        }

        override fun onNullBinding(name: ComponentName) {
            onTemporaryDisconnect(this@BindSession, DisconnectReason.NULL_BINDING)
        }
    }
}

internal data class SessionCleanup(
    val connection: ServiceConnection,
    val binder: IBinder?,
    val deathRecipient: IBinder.DeathRecipient?,
    val handshakeJob: Job?,
    val bindTimeoutJob: Job?,
    val remoteCalls: List<Deferred<*>>,
    val shouldUnbind: Boolean,
)

internal data class RemoteTarget(
    val session: BindSession,
    val service: IManagerRuntimeService,
    val handshake: ManagerHandshake,
)

internal sealed interface BindResult {
    data class Success(val accepted: Boolean) : BindResult
    data class Failure(val state: ManagerRuntimeAvailability) : BindResult
}

internal class RemoteCallTimeoutException(
    val permitsExhausted: Boolean,
) : RuntimeException()

internal class RemoteCallValue<T>(
    val value: T,
)
