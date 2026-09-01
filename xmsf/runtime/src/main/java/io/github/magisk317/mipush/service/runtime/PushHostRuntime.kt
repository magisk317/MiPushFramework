package io.github.magisk317.mipush.service.runtime

import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.xiaomi.push.service.*

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
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.planGslbRequest(
            sdkVersion = sdkVersion,
            droidVersion = droidVersion,
            model = model,
            incremental = incremental,
            miuiType = miuiType,
        )
        val separator = when {
            baseUrl.contains("?").not() -> "?"
            baseUrl.endsWith("?") || baseUrl.endsWith("&") -> ""
            else -> "&"
        }
        val requestUrl = baseUrl + separator + plan.queryFields.joinToString("&") { field ->
            query(field.name, field.value)
        }
        val url = URL(requestUrl)
        val port = if (url.port == -1) plan.defaultStatsPort else url.port
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
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.decideBucketFetch(
            fetchBucketRequested = fetchBucketRequested,
            lastFetchTimeMs = lastFetchTimeMs,
            nowMs = nowMs,
            minBucketFetchDurationMs = minBucketFetchDurationMs,
        )
        return PushBucketFetchPlan(
            shouldRefresh = plan.shouldRefresh,
            eventAction = plan.eventAction,
        )
    }

    @JvmStatic
    fun decideBucketReconnect(
        hasConnection: Boolean,
        currentHost: String?,
        candidateHosts: List<String>
    ): PushBucketReconnectPlan {
        val plan = io.github.magisk317.mipush.runtime.core.PushHostPlanFactory.decideBucketReconnect(
            hasConnection = hasConnection,
            currentHost = currentHost,
            candidateHosts = candidateHosts,
        )
        return PushBucketReconnectPlan(
            shouldReconnect = plan.shouldReconnect,
            eventAction = plan.eventAction,
            connectionStateReason = plan.connectionStateReason,
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
