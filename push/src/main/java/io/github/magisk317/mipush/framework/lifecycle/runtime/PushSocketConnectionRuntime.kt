package io.github.magisk317.mipush.framework.lifecycle.runtime

import com.xiaomi.push.service.RC4Cryption

data class PushSocketHostSelectionPlan(
    val candidateHosts: List<String>,
    val eventAction: String
)

data class PushSocketFailurePlan(
    val shouldContinue: Boolean,
    val eventAction: String
)

data class PushShortConnectionPlan(
    val nextShortConnCount: Int,
    val shouldSinkDown: Boolean,
    val eventAction: String
)

object PushSocketConnectionRuntime {
    @JvmStatic
    fun resolveCandidateHosts(
        requestedHost: String,
        fallbackHosts: List<String>
    ): PushSocketHostSelectionPlan {
        val effectiveHosts = fallbackHosts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (effectiveHosts.isNotEmpty()) {
            return PushSocketHostSelectionPlan(
                candidateHosts = effectiveHosts,
                eventAction = "socket_connect_fallback_hosts"
            )
        }
        return PushSocketHostSelectionPlan(
            candidateHosts = listOf(requestedHost),
            eventAction = "socket_connect_direct_host"
        )
    }

    @JvmStatic
    fun planFailureRetry(
        initialConnPoint: String?,
        currentConnPoint: String?
    ): PushSocketFailurePlan {
        val networkChanged = initialConnPoint != currentConnPoint
        return PushSocketFailurePlan(
            shouldContinue = !networkChanged,
            eventAction = if (networkChanged) {
                "socket_connect_abort_network_changed"
            } else {
                "socket_connect_retry_next_host"
            }
        )
    }

    @JvmStatic
    fun deriveConnectionKey(
        challenge: String?,
        deviceUuid: String?
    ): ByteArray? {
        if (challenge.isNullOrEmpty() || deviceUuid.isNullOrEmpty()) {
            return null
        }
        val challengeTail = challenge.substring(challenge.length / 2)
        val deviceUuidTail = deviceUuid.substring(deviceUuid.length / 2)
        return RC4Cryption.encrypt(
            challenge.toByteArray(),
            (challengeTail + deviceUuidTail).toByteArray()
        )
    }

    @JvmStatic
    fun evaluateShortConnection(
        nowElapsedMs: Long,
        lastConnectedTime: Long,
        hasNetwork: Boolean,
        curShortConnCount: Int,
        shortConnectionThresholdMs: Long = 300_000L,
        maxShortConnCount: Int = 2
    ): PushShortConnectionPlan {
        if (nowElapsedMs - lastConnectedTime >= shortConnectionThresholdMs) {
            return PushShortConnectionPlan(
                nextShortConnCount = 0,
                shouldSinkDown = false,
                eventAction = "socket_short_conn_window_reset"
            )
        }
        if (!hasNetwork) {
            return PushShortConnectionPlan(
                nextShortConnCount = curShortConnCount,
                shouldSinkDown = false,
                eventAction = "socket_short_conn_no_network"
            )
        }
        val nextCount = curShortConnCount + 1
        if (nextCount >= maxShortConnCount) {
            return PushShortConnectionPlan(
                nextShortConnCount = 0,
                shouldSinkDown = true,
                eventAction = "socket_sinkdown_host"
            )
        }
        return PushShortConnectionPlan(
            nextShortConnCount = nextCount,
            shouldSinkDown = false,
            eventAction = "socket_short_conn_retry"
        )
    }
}
