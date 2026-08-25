package io.github.magisk317.mipush.bridge

import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.smack.Connection
import io.github.magisk317.mipush.service.runtime.XMPushServiceLifecycleRuntime

internal data class MiPushRuntimeServiceBinding(
    val service: XMPushServiceCore,
    val runtime: XMPushServiceLifecycleRuntime,
)

/**
 * Owns the bridge-wide service binding and active connection identity.
 *
 * Every binding and connection read/write is serialized on this instance so lifecycle replacement,
 * teardown, and stale connection callbacks observe one consistent state boundary.
 */
internal class MiPushRuntimeObserverState {
    private var serviceRuntimeBinding: MiPushRuntimeServiceBinding? = null
    private var activeConnection: Connection? = null

    fun replaceService(service: XMPushServiceCore) {
        synchronized(this) {
            serviceRuntimeBinding?.runtime?.close()
            activeConnection = null
            serviceRuntimeBinding = MiPushRuntimeServiceBinding(
                service = service,
                runtime = XMPushServiceLifecycleRuntime(),
            )
        }
    }

    fun clearService() {
        synchronized(this) {
            activeConnection = null
            serviceRuntimeBinding?.runtime?.close()
            serviceRuntimeBinding = null
        }
    }

    fun currentServiceLifecycleRuntime(service: XMPushServiceCore?): XMPushServiceLifecycleRuntime? = synchronized(this) {
        val binding = serviceRuntimeBinding ?: return@synchronized null
        binding.runtime.takeIf { service === binding.service }
    }

    fun service(): XMPushServiceCore? = synchronized(this) {
        serviceRuntimeBinding?.service
    }

    fun setActiveConnection(connection: Connection) {
        synchronized(this) {
            activeConnection = connection
        }
    }

    fun activeServiceFor(connection: Connection): XMPushServiceCore? = synchronized(this) {
        val service = serviceRuntimeBinding?.service ?: return@synchronized null
        val ownsConnection = activeConnection === connection || runCatching {
            service.currentConnection === connection || service.slimConnection === connection
        }.getOrDefault(false)
        service.takeIf { ownsConnection }
    }

    fun releaseConnection(connection: Connection) {
        synchronized(this) {
            if (activeConnection === connection) activeConnection = null
            // A stale callback may not have been initiated by service.disconnect(). Clear only the
            // matching object so a later connection is never affected by an old callback.
            serviceRuntimeBinding?.service?.let { service ->
                if (service.currentConnection === connection) {
                    service.clearCurrentConnection()
                }
            }
        }
    }
}
