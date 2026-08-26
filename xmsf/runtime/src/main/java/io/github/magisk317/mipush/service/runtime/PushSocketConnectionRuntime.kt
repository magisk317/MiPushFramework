package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.RC4Cryption
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import io.github.magisk317.mipush.runtime.core.PushSocketConnectionPlanFactory

object PushSocketConnectionRuntime {
    @JvmStatic
    fun resolveCandidateHosts(
        requestedHost: String,
        fallbackHosts: List<String>,
    ): PushSocketHostSelectionPlan =
        PushSocketConnectionPlanFactory.resolveCandidateHosts(requestedHost, fallbackHosts)

    @JvmStatic
    fun planFailureRetry(
        initialConnPoint: String?,
        currentConnPoint: String?,
    ): PushSocketFailurePlan =
        PushSocketConnectionPlanFactory.planFailureRetry(initialConnPoint, currentConnPoint)

    @JvmStatic
    fun deriveConnectionKey(
        challenge: String?,
        deviceUuid: String?,
    ): ByteArray? {
        if (challenge.isNullOrEmpty() || deviceUuid.isNullOrEmpty()) {
            return null
        }
        val challengeTail = challenge.substring(challenge.length / 2)
        val deviceUuidTail = deviceUuid.substring(deviceUuid.length / 2)
        return RC4Cryption.encrypt(
            challenge.toByteArray(),
            (challengeTail + deviceUuidTail).toByteArray(),
        )
    }

    @JvmStatic
    fun evaluateShortConnection(
        nowElapsedMs: Long,
        lastConnectedTime: Long,
        hasNetwork: Boolean,
        curShortConnCount: Int,
        shortConnectionThresholdMs: Long = 300_000L,
        maxShortConnCount: Int = 2,
    ): PushShortConnectionPlan = PushSocketConnectionPlanFactory.evaluateShortConnection(
        nowElapsedMs = nowElapsedMs,
        lastConnectedTime = lastConnectedTime,
        hasNetwork = hasNetwork,
        curShortConnCount = curShortConnCount,
        shortConnectionThresholdMs = shortConnectionThresholdMs,
        maxShortConnCount = maxShortConnCount,
    )
}
