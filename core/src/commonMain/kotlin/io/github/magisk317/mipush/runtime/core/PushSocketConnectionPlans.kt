package io.github.magisk317.mipush.runtime.core

data class PushSocketHostSelectionPlan(
    val candidateHosts: List<String>,
    val eventAction: String,
)

data class PushSocketFailurePlan(
    val shouldContinue: Boolean,
    val eventAction: String,
)

data class PushShortConnectionPlan(
    val nextShortConnCount: Int,
    val shouldSinkDown: Boolean,
    val eventAction: String,
)

/** Platform-neutral socket connection decisions. Encryption and transport stay in platform adapters. */
object PushSocketConnectionPlanFactory {
    fun resolveCandidateHosts(
        requestedHost: String,
        fallbackHosts: List<String>,
    ): PushSocketHostSelectionPlan {
        val effectiveHosts = fallbackHosts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (effectiveHosts.isNotEmpty()) {
            PushSocketHostSelectionPlan(
                candidateHosts = effectiveHosts,
                eventAction = "socket_connect_fallback_hosts",
            )
        } else {
            PushSocketHostSelectionPlan(
                candidateHosts = listOf(requestedHost),
                eventAction = "socket_connect_direct_host",
            )
        }
    }

    fun planFailureRetry(
        initialConnPoint: String?,
        currentConnPoint: String?,
    ): PushSocketFailurePlan {
        val networkChanged = initialConnPoint != currentConnPoint
        return PushSocketFailurePlan(
            shouldContinue = !networkChanged,
            eventAction = if (networkChanged) {
                "socket_connect_abort_network_changed"
            } else {
                "socket_connect_retry_next_host"
            },
        )
    }

    fun evaluateShortConnection(
        nowElapsedMs: Long,
        lastConnectedTime: Long,
        hasNetwork: Boolean,
        curShortConnCount: Int,
        shortConnectionThresholdMs: Long = 300_000L,
        maxShortConnCount: Int = 2,
    ): PushShortConnectionPlan {
        if (nowElapsedMs - lastConnectedTime >= shortConnectionThresholdMs) {
            return PushShortConnectionPlan(
                nextShortConnCount = 0,
                shouldSinkDown = false,
                eventAction = "socket_short_conn_window_reset",
            )
        }
        if (!hasNetwork) {
            return PushShortConnectionPlan(
                nextShortConnCount = curShortConnCount,
                shouldSinkDown = false,
                eventAction = "socket_short_conn_no_network",
            )
        }
        val nextCount = curShortConnCount + 1
        return if (nextCount >= maxShortConnCount) {
            PushShortConnectionPlan(
                nextShortConnCount = 0,
                shouldSinkDown = true,
                eventAction = "socket_sinkdown_host",
            )
        } else {
            PushShortConnectionPlan(
                nextShortConnCount = nextCount,
                shouldSinkDown = false,
                eventAction = "socket_short_conn_retry",
            )
        }
    }
}
