package io.github.magisk317.mipush.common.logging

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
        if (logDir == null || incomingBytes < 0L || incomingBytes > maxBytes || maxBytes <= 0L) {
            return false
        }
        val currentName = runtimeFileName(route, currentDay)
        val routeFiles = logDir.listFiles()
            .orEmpty()
            .filter { it.isFile && isRouteFile(it.name, route) }
            .sortedBy(File::getName)
        var totalBytes = routeFiles.sumOf(File::length)
        if (totalBytes + incomingBytes <= maxBytes) return true

        for (file in routeFiles) {
            if (file.name == currentName) continue
            val fileBytes = file.length()
            if (file.delete()) totalBytes -= fileBytes
            if (totalBytes + incomingBytes <= maxBytes) return true
        }
        return totalBytes + incomingBytes <= maxBytes
    }

    fun runtimeFileName(route: String, day: String): String {
        val routeName = sanitizeRoute(route)
        return if (routeName == DEFAULT_ROUTE) {
            "runtime.$day.jsonl"
        } else {
            "runtime.$routeName.$day.jsonl"
        }
    }

    private fun isRouteFile(name: String, route: String): Boolean {
        val routeName = sanitizeRoute(route)
        val prefix = if (routeName == DEFAULT_ROUTE) "runtime" else "runtime.$routeName"
        return Regex("^${Regex.escape(prefix)}\\.\\d{4}-\\d{2}-\\d{2}\\.jsonl$").matches(name)
    }

    private fun sanitizeRoute(route: String): String = route
        .ifBlank { DEFAULT_ROUTE }
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .take(MAX_ROUTE_LENGTH)

    private const val MAX_ROUTE_LENGTH = 64
    private const val DEFAULT_ROUTE = "app"
}
