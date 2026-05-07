package com.xiaomi.network

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
    private const val FAILURE_BACKOFF_WINDOW_MS = 60_000L

    @JvmStatic
    fun planRefreshTargets(
        allHosts: List<String>,
        hostsWithFallback: Set<String>
    ): HostRefreshTargetsPlan {
        return HostRefreshTargetsPlan(
            targetHosts = allHosts.filterNot { hostsWithFallback.contains(it) }
        )
    }

    @JvmStatic
    fun planRemoteFallbackRequest(
        nowMs: Long,
        lastRequestTimestampMs: Long,
        failureCount: Long
    ): HostRequestThrottlePlan {
        val throttleWindowMs = failureCount * FAILURE_BACKOFF_WINDOW_MS
        if (nowMs - lastRequestTimestampMs <= throttleWindowMs) {
            return HostRequestThrottlePlan(
                shouldRequest = false,
                nextTimestampMs = lastRequestTimestampMs,
                eventAction = "gslb_request_throttled"
            )
        }
        return HostRequestThrottlePlan(
            shouldRequest = true,
            nextTimestampMs = nowMs,
            eventAction = "gslb_request_allowed"
        )
    }

    @JvmStatic
    fun planRequestUrls(
        defaultUrl: String,
        localFallbackUrls: List<String>?,
        reservedHosts: List<String>
    ): HostRequestUrlsPlan {
        val urls = localFallbackUrls?.takeIf { it.isNotEmpty() } ?: buildList {
            add(defaultUrl)
            reservedHosts.forEach { add(defaultUrl.replaceFirst("resolver.msg.xiaomi.net", it)) }
        }
        return HostRequestUrlsPlan(urls = urls.distinct())
    }
}
