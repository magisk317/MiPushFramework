package io.github.magisk317.mipush.diagnostics

data class DailyRouteLogQuotaInput(
    val currentDay: String,
    val incomingBytes: Long,
    val maxBytes: Long,
    val files: List<DailyRouteLogFile>,
)

data class DailyRouteLogFile(
    val name: String,
    val sizeBytes: Long,
)

data class DailyRouteLogQuotaDecision(
    val accepted: Boolean,
    val filesToDelete: List<String>,
)

/** Pure quota decision; filesystem deletion is performed by the calling platform adapter. */
object DailyRouteLogQuotaCore {
    fun decide(route: String, input: DailyRouteLogQuotaInput): DailyRouteLogQuotaDecision {
        if (input.incomingBytes < 0L || input.maxBytes <= 0L || input.incomingBytes > input.maxBytes) {
            return DailyRouteLogQuotaDecision(false, emptyList())
        }
        val currentName = DailyRouteLogNamingPolicy.runtimeFileName(route, input.currentDay)
        val routeFiles = input.files
            .filter { DailyRouteLogNamingPolicy.isRouteFile(it.name, route) }
            .sortedBy(DailyRouteLogFile::name)
        var totalBytes = routeFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        if (totalBytes + input.incomingBytes <= input.maxBytes) {
            return DailyRouteLogQuotaDecision(true, emptyList())
        }

        val filesToDelete = buildList {
            routeFiles.forEach { file ->
                if (file.name == currentName) return@forEach
                add(file.name)
                totalBytes -= file.sizeBytes.coerceAtLeast(0L)
                if (totalBytes + input.incomingBytes <= input.maxBytes) return@buildList
            }
        }
        return DailyRouteLogQuotaDecision(
            accepted = totalBytes + input.incomingBytes <= input.maxBytes,
            filesToDelete = filesToDelete,
        )
    }
}
