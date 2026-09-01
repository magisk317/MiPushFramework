package io.github.magisk317.mipush.common.logging

import io.github.magisk317.mipush.diagnostics.DailyRouteLogFile
import io.github.magisk317.mipush.diagnostics.DailyRouteLogQuotaCore
import io.github.magisk317.mipush.diagnostics.DailyRouteLogNamingPolicy
import io.github.magisk317.mipush.diagnostics.DailyRouteLogQuotaInput
import java.io.File

/**
 * Capacity policy for daily runtime JSONL files.
 *
 * Each route owns an independent quota. Old daily files from the same route are removed first;
 * the current day's file is never deleted and no other route can be affected. If today's file
 * alone reaches the quota, new events are dropped until the next daily rotation.
 */
object DailyRouteLogQuota {
    const val DEFAULT_MAX_BYTES: Long = 32L * 1024L * 1024L

    fun ensureCapacity(
        logDir: File?,
        route: String,
        currentDay: String,
        incomingBytes: Long,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): Boolean {
        if (logDir == null) {
            return false
        }
        val files = logDir.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .map { file -> DailyRouteLogFile(file.name, file.length()) }
        val decision = DailyRouteLogQuotaCore.decide(
            route = route,
            input = DailyRouteLogQuotaInput(
                currentDay = currentDay,
                incomingBytes = incomingBytes,
                maxBytes = maxBytes,
                files = files,
            ),
        )
        if (!decision.accepted) return false
        decision.filesToDelete.forEach { name ->
            files.firstOrNull { it.name == name }?.let { file ->
                File(logDir, file.name).delete()
            }
        }
        val remainingBytes = logDir.listFiles()
            .orEmpty()
            .filter { it.isFile && DailyRouteLogNamingPolicy.isRouteFile(it.name, route) }
            .sumOf(File::length)
        return remainingBytes + incomingBytes <= maxBytes
    }

    fun runtimeFileName(route: String, day: String): String =
        DailyRouteLogNamingPolicy.runtimeFileName(route, day)

}
