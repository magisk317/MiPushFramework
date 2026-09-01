package io.github.magisk317.mipush.runtime.core

data class PushGslbQueryField(
    val name: String,
    val value: String,
)

data class PushGslbRequestPlan(
    val queryFields: List<PushGslbQueryField>,
    val defaultStatsPort: Int,
)

data class PushBucketFetchPlan(
    val shouldRefresh: Boolean,
    val eventAction: String,
)

data class PushBucketReconnectPlan(
    val shouldReconnect: Boolean,
    val eventAction: String,
    val connectionStateReason: String? = null,
)

data class HostRequestUrlsPlan(
    val urls: List<String>,
)

data class HostRefreshTargetsPlan(
    val targetHosts: List<String>,
)

data class HostRequestThrottlePlan(
    val shouldRequest: Boolean,
    val nextTimestampMs: Long,
    val eventAction: String,
)

/** Platform-neutral host selection, refresh and GSLB request decisions. */
object PushHostPlanFactory {
    private const val FAILURE_BACKOFF_WINDOW_MS = 60_000L
    private const val RESOLVER_HOST = "resolver.msg.xiaomi.net"

    fun planGslbRequest(
        sdkVersion: Int,
        droidVersion: Int,
        model: String,
        incremental: String,
        miuiType: Int,
    ): PushGslbRequestPlan = PushGslbRequestPlan(
        queryFields = listOf(
            PushGslbQueryField("sdkver", sdkVersion.toString()),
            PushGslbQueryField("osver", droidVersion.toString()),
            PushGslbQueryField("os", "$model:$incremental"),
            PushGslbQueryField("mi", miuiType.toString()),
        ),
        defaultStatsPort = 80,
    )

    fun decideBucketFetch(
        fetchBucketRequested: Boolean,
        lastFetchTimeMs: Long,
        nowMs: Long,
        minBucketFetchDurationMs: Long,
    ): PushBucketFetchPlan = when {
        !fetchBucketRequested -> PushBucketFetchPlan(false, "gslb_fetch_not_requested")
        nowMs - lastFetchTimeMs <= minBucketFetchDurationMs ->
            PushBucketFetchPlan(false, "gslb_fetch_throttled")
        else -> PushBucketFetchPlan(true, "gslb_fetch_refresh")
    }

    fun decideBucketReconnect(
        hasConnection: Boolean,
        currentHost: String?,
        candidateHosts: List<String>,
    ): PushBucketReconnectPlan {
        if (!hasConnection) {
            return PushBucketReconnectPlan(false, "gslb_refresh_no_connection")
        }
        if (currentHost.isNullOrBlank()) {
            return PushBucketReconnectPlan(false, "gslb_refresh_no_current_host")
        }
        val effectiveHosts = candidateHosts.map(String::trim).filter(String::isNotEmpty)
        if (effectiveHosts.isEmpty()) {
            return PushBucketReconnectPlan(false, "gslb_refresh_no_hosts")
        }
        if (currentHost in effectiveHosts) {
            return PushBucketReconnectPlan(false, "gslb_hosts_unchanged")
        }
        return PushBucketReconnectPlan(
            shouldReconnect = true,
            eventAction = "gslb_hosts_changed",
            connectionStateReason = "bucket_changed",
        )
    }

    fun planRefreshTargets(
        allHosts: List<String>,
        hostsWithFallback: Set<String>,
    ): HostRefreshTargetsPlan = HostRefreshTargetsPlan(
        targetHosts = allHosts.filterNot(hostsWithFallback::contains),
    )

    fun planRemoteFallbackRequest(
        nowMs: Long,
        lastRequestTimestampMs: Long,
        failureCount: Long,
    ): HostRequestThrottlePlan {
        val throttleWindowMs = failureCount * FAILURE_BACKOFF_WINDOW_MS
        return if (nowMs - lastRequestTimestampMs <= throttleWindowMs) {
            HostRequestThrottlePlan(
                shouldRequest = false,
                nextTimestampMs = lastRequestTimestampMs,
                eventAction = "gslb_request_throttled",
            )
        } else {
            HostRequestThrottlePlan(
                shouldRequest = true,
                nextTimestampMs = nowMs,
                eventAction = "gslb_request_allowed",
            )
        }
    }

    fun planRequestUrls(
        defaultUrl: String,
        localFallbackUrls: List<String>?,
        reservedHosts: List<String>,
    ): HostRequestUrlsPlan {
        val urls = localFallbackUrls?.takeIf(List<String>::isNotEmpty) ?: buildList {
            add(defaultUrl)
            reservedHosts.forEach { host -> add(defaultUrl.replaceFirst(RESOLVER_HOST, host)) }
        }
        return HostRequestUrlsPlan(urls.distinct())
    }
}
