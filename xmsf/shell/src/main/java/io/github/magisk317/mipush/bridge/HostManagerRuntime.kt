package io.github.magisk317.mipush.bridge

data class HostRefreshTargetsPlan(
    val targetHosts: List<String>
)

data class HostRequestThrottlePlan(
    val shouldRequest: Boolean,
    val nextTimestampMs: Long,
    val eventAction: String
)

data class HostRequestUrlsPlan(
    val urls: List<String>
)

object HostManagerRuntime {
    @JvmStatic
    fun planRefreshTargets(
        allHosts: List<String>,
        hostsWithFallback: Set<String>
    ): HostRefreshTargetsPlan {
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.planRefreshTargets(
            allHosts = allHosts,
            hostsWithFallback = hostsWithFallback,
        )
        return HostRefreshTargetsPlan(plan.targetHosts)
    }

    @JvmStatic
    fun planRemoteFallbackRequest(
        nowMs: Long,
        lastRequestTimestampMs: Long,
        failureCount: Long
    ): HostRequestThrottlePlan {
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.planRemoteFallbackRequest(
            nowMs = nowMs,
            lastRequestTimestampMs = lastRequestTimestampMs,
            failureCount = failureCount,
        )
        return HostRequestThrottlePlan(
            shouldRequest = plan.shouldRequest,
            nextTimestampMs = plan.nextTimestampMs,
            eventAction = plan.eventAction,
        )
    }

    @JvmStatic
    fun planRequestUrls(
        defaultUrl: String,
        localFallbackUrls: List<String>?,
        reservedHosts: List<String>
    ): HostRequestUrlsPlan {
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.planRequestUrls(
            defaultUrl = defaultUrl,
            localFallbackUrls = localFallbackUrls,
            reservedHosts = reservedHosts,
        )
        return HostRequestUrlsPlan(plan.urls)
    }
}
