package com.xiaomi.push.service

import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PushGslbRequest(
    val requestUrl: String,
    val statsHostPort: String
)

data class PushBucketFetchPlan(
    val shouldRefresh: Boolean,
    val eventAction: String
)

data class PushBucketReconnectPlan(
    val shouldReconnect: Boolean,
    val eventAction: String,
    val connectionStateReason: String? = null
)

object PushHostRuntime {
    @JvmStatic
    fun buildGslbRequest(
        baseUrl: String,
        sdkVersion: Int,
        droidVersion: Int,
        model: String,
        incremental: String,
        miuiType: Int
    ): PushGslbRequest {
        val separator = when {
            baseUrl.contains("?").not() -> "?"
            baseUrl.endsWith("?") || baseUrl.endsWith("&") -> ""
            else -> "&"
        }
        val requestUrl = buildString {
            append(baseUrl)
            append(separator)
            append(query("sdkver", sdkVersion.toString()))
            append('&')
            append(query("osver", droidVersion.toString()))
            append('&')
            append(query("os", "$model:$incremental"))
            append('&')
            append(query("mi", miuiType.toString()))
        }
        val url = URL(requestUrl)
        val port = if (url.port == -1) 80 else url.port
        return PushGslbRequest(
            requestUrl = requestUrl,
            statsHostPort = "${url.host}:$port"
        )
    }

    private fun query(key: String, value: String): String {
        return key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    @JvmStatic
    fun decideBucketFetch(
        fetchBucketRequested: Boolean,
        lastFetchTimeMs: Long,
        nowMs: Long,
        minBucketFetchDurationMs: Long
    ): PushBucketFetchPlan {
        if (!fetchBucketRequested) {
            return PushBucketFetchPlan(
                shouldRefresh = false,
                eventAction = "gslb_fetch_not_requested"
            )
        }
        if (nowMs - lastFetchTimeMs <= minBucketFetchDurationMs) {
            return PushBucketFetchPlan(
                shouldRefresh = false,
                eventAction = "gslb_fetch_throttled"
            )
        }
        return PushBucketFetchPlan(
            shouldRefresh = true,
            eventAction = "gslb_fetch_refresh"
        )
    }

    @JvmStatic
    fun decideBucketReconnect(
        hasConnection: Boolean,
        currentHost: String?,
        candidateHosts: List<String>
    ): PushBucketReconnectPlan {
        if (!hasConnection) {
            return PushBucketReconnectPlan(
                shouldReconnect = false,
                eventAction = "gslb_refresh_no_connection"
            )
        }
        if (currentHost.isNullOrBlank()) {
            return PushBucketReconnectPlan(
                shouldReconnect = false,
                eventAction = "gslb_refresh_no_current_host"
            )
        }
        val effectiveHosts = candidateHosts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (effectiveHosts.isEmpty()) {
            return PushBucketReconnectPlan(
                shouldReconnect = false,
                eventAction = "gslb_refresh_no_hosts"
            )
        }
        if (effectiveHosts.any { it == currentHost }) {
            return PushBucketReconnectPlan(
                shouldReconnect = false,
                eventAction = "gslb_hosts_unchanged"
            )
        }
        return PushBucketReconnectPlan(
            shouldReconnect = true,
            eventAction = "gslb_hosts_changed",
            connectionStateReason = "bucket_changed"
        )
    }

    @JvmStatic
    fun decideBucketReconnect(
        currentHost: String?,
        candidateHosts: List<String>
    ): PushBucketReconnectPlan {
        return decideBucketReconnect(
            hasConnection = true,
            currentHost = currentHost,
            candidateHosts = candidateHosts
        )
    }
}
