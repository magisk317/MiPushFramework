package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Probes the manager -> runtime Binder link with a throwaway
 * [ManagerRuntimeClient] and maps the terminal availability to
 * [XmsfConnectivity].
 *
 * The probe owns its client for its lifetime: [ManagerRuntimeClient] starts a
 * reconnect loop the moment it is constructed, so reusing a probe client for
 * anything else would keep that loop alive after the probe is done.
 */
class XmsfConnectivityProbe(
    context: Context,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MS,
) {

    private val appContext = context.applicationContext ?: context

    suspend fun probe(): XmsfConnectivity {
        val client = ManagerRuntimeClient(appContext, scope)
        return try {
            client.connect()
            val availability = withTimeoutOrNull(timeoutMillis) {
                client.availability.first { it.isTerminal() }
            } ?: return XmsfConnectivity.TIMED_OUT
            availability.toConnectivity()
        } catch (_: SecurityException) {
            XmsfConnectivity.PERMISSION_DENIED
        } catch (_: IllegalStateException) {
            XmsfConnectivity.FAILED
        } catch (_: RuntimeException) {
            XmsfConnectivity.FAILED
        } finally {
            runCatching { client.close() }
        }
    }

    companion object {
        const val DEFAULT_PROBE_TIMEOUT_MS = 10_000L

        /** Binding/Disconnected states are in-flight, not outcomes. */
        fun ManagerRuntimeAvailability.isTerminal(): Boolean = when (this) {
            is ManagerRuntimeAvailability.Available,
            is ManagerRuntimeAvailability.RuntimeMissing,
            is ManagerRuntimeAvailability.PermissionDenied,
            is ManagerRuntimeAvailability.TimedOut,
            is ManagerRuntimeAvailability.Incompatible,
            is ManagerRuntimeAvailability.Failed,
            -> true
            is ManagerRuntimeAvailability.Disconnected,
            is ManagerRuntimeAvailability.Binding,
            is ManagerRuntimeAvailability.TemporarilyDisconnected,
            -> false
        }

        fun ManagerRuntimeAvailability.toConnectivity(): XmsfConnectivity = when (this) {
            is ManagerRuntimeAvailability.Available -> XmsfConnectivity.CONNECTED
            is ManagerRuntimeAvailability.RuntimeMissing -> XmsfConnectivity.RUNTIME_MISSING
            is ManagerRuntimeAvailability.PermissionDenied -> XmsfConnectivity.PERMISSION_DENIED
            is ManagerRuntimeAvailability.TimedOut -> XmsfConnectivity.TIMED_OUT
            is ManagerRuntimeAvailability.Incompatible -> XmsfConnectivity.INCOMPATIBLE
            is ManagerRuntimeAvailability.Failed -> XmsfConnectivity.FAILED
            is ManagerRuntimeAvailability.Disconnected,
            is ManagerRuntimeAvailability.Binding,
            is ManagerRuntimeAvailability.TemporarilyDisconnected,
            -> XmsfConnectivity.UNKNOWN
        }
    }
}
