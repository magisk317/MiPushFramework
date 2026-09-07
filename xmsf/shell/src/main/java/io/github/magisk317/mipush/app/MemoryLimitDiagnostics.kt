package io.github.magisk317.mipush.app

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi

/** Policies used by startup diagnostics to distinguish memory pressure from unrelated exits. */
internal object MemoryLimitDiagnostics {
    private const val HIGH_USAGE_PERCENT = 80L

    @RequiresApi(Build.VERSION_CODES.R)
    fun isMemoryRelatedExitReason(reason: Int): Boolean =
        reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
            reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE

    @RequiresApi(Build.VERSION_CODES.R)
    fun isMemoryRelatedExit(reason: Int, description: String?): Boolean =
        isMemoryRelatedExitReason(reason) ||
            reason == ApplicationExitInfo.REASON_OTHER &&
            description?.contains(MEMORY_LIMITER_ANON_SWAP_MARKER) == true

    @RequiresApi(Build.VERSION_CODES.R)
    fun isMemoryRelatedExit(info: ApplicationExitInfo): Boolean =
        isMemoryRelatedExit(info.reason, info.description)

    fun isHighMemoryUsage(usedBytes: Long, maxBytes: Long): Boolean {
        if (usedBytes < 0L || maxBytes <= 0L) return false
        if (usedBytes >= maxBytes) return true
        val fifthsRemainder = if (maxBytes % 5L == 0L) 0L else 1L
        val strictThreshold = maxBytes - (maxBytes / 5L + fifthsRemainder) + 1L
        return usedBytes >= strictThreshold
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun describeExit(info: ApplicationExitInfo): String = buildString {
        append("reason=")
        append(info.reason)
        append(" status=")
        append(info.status)
        append(" timestamp=")
        append(info.timestamp)
        append(" pssKb=")
        append(info.pss)
        append(" rssKb=")
        append(info.rss)
        append(" description=")
        append(info.description.orEmpty())
    }

    private const val MEMORY_LIMITER_ANON_SWAP_MARKER = "MemoryLimiter:AnonSwap"
}
