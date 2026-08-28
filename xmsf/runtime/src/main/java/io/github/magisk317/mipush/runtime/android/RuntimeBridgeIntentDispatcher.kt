package io.github.magisk317.mipush.runtime.android

import android.content.Intent
import io.github.magisk317.mipush.common.utils.logE

/** Bridge-host lifecycle and pending intent dispatch, synchronized only through [state]. */
internal class RuntimeBridgeIntentDispatcher(
    private val state: AndroidPushRuntimeState,
) {
    fun attach(host: PushRuntimeBridgeHost) {
        val pending = state.withLock {
            bridgeHost = host
            pendingBridgeIntents.drain()
        }
        runCatching { host.onRuntimeStarted() }
            .onFailure { logE("bridge host start failed", it) }
        pending.forEach { dispatchToHost(host, it) }
    }

    fun detach(host: PushRuntimeBridgeHost) {
        val shouldStop = state.withLock {
            if (bridgeHost !== host) return
            bridgeHost = null
            true
        }
        if (shouldStop) {
            runCatching { host.onRuntimeStopped() }
                .onFailure { logE("bridge host stop failed", it) }
        }
    }

    fun submit(intent: Intent) {
        val host = state.withLock {
            val activeHost = bridgeHost
            if (activeHost == null) pendingBridgeIntents.offer(Intent(intent))
            activeHost
        }
        if (host != null) dispatchToHost(host, Intent(intent))
    }

    private fun dispatchToHost(host: PushRuntimeBridgeHost, intent: Intent) {
        runCatching { host.processBridgeIntent(intent) }
            .onFailure { logE("bridge intent processing failed: action=${intent.action}", it) }
    }
}
