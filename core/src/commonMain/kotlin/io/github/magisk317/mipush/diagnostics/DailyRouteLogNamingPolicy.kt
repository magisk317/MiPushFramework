package io.github.magisk317.mipush.diagnostics

/**
 * Platform-neutral naming rules for daily runtime JSONL files.
 *
 * File enumeration, sizing, and deletion remain in platform adapters; this policy only provides
 * stable route normalization and file-name classification.
 */
object DailyRouteLogNamingPolicy {
    fun runtimeFileName(route: String, day: String): String {
        val routeName = sanitizeRoute(route)
        return if (routeName == DEFAULT_ROUTE) {
            "runtime.$day.jsonl"
        } else {
            "runtime.$routeName.$day.jsonl"
        }
    }

    fun isRouteFile(name: String, route: String): Boolean {
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
