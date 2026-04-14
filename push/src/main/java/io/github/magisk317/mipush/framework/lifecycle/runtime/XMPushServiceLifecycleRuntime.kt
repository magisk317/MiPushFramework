package io.github.magisk317.mipush.framework.lifecycle.runtime

import com.xiaomi.push.service.XMPushService
import com.xiaomi.smack.Connection

class XMPushServiceLifecycleRuntime(
    service: XMPushService,
) {
    private val delegate = io.github.magisk317.mipush.service.runtime.XMPushServiceLifecycleRuntime(service)

    fun configureClientChangeListener() = delegate.configureClientChangeListener()

    fun networkChanged() = delegate.networkChanged()

    fun postOnCreate() = delegate.postOnCreate()

    fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        delegate.connectionClosed(connection, reason, error)
    }

    fun connectionStarted(connection: Connection) = delegate.connectionStarted(connection)

    fun reconnectionFailed(connection: Connection, error: Exception) {
        delegate.reconnectionFailed(connection, error)
    }

    fun reconnectionSuccessful(connection: Connection) {
        delegate.reconnectionSuccessful(connection)
    }
}
