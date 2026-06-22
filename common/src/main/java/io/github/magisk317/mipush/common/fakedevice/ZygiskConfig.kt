package io.github.magisk317.mipush.common.fakedevice

data class ZygiskConfigEntry(
    val packageName: String,
    val processName: String? = null,
) {
    val isPackageWide: Boolean
        get() = processName.isNullOrBlank()

    fun matchesPackage(packageName: String): Boolean = this.packageName == packageName

    fun toLine(): String {
        return if (processName.isNullOrBlank()) {
            packageName
        } else {
            "$packageName|$processName"
        }
    }
}

data class ZygiskConfig(
    val entries: List<ZygiskConfigEntry> = emptyList(),
) {
    fun isEnabledForPackage(packageName: String): Boolean {
        return entries.any { it.matchesPackage(packageName) }
    }

    fun enabledPackages(): Set<String> {
        return entries.mapTo(linkedSetOf()) { it.packageName }
    }

    fun withPackageEnabled(packageName: String, enabled: Boolean): ZygiskConfig {
        val normalizedPackage = packageName.trim()
        val kept = entries.filterNot { it.matchesPackage(normalizedPackage) }.toMutableList()
        if (enabled && ZygiskPackagePolicy.isManagedPackage(normalizedPackage)) {
            kept += ZygiskConfigEntry(normalizedPackage)
        }
        return ZygiskConfig(kept).normalized()
    }

    fun normalized(): ZygiskConfig {
        val unique = LinkedHashMap<String, ZygiskConfigEntry>()
        entries.forEach { entry ->
            val packageName = entry.packageName.trim()
            val processName = entry.processName?.trim()?.takeIf { it.isNotEmpty() }
            if (!ZygiskPackagePolicy.isManagedPackage(packageName)) return@forEach
            if (processName != null && !ZygiskPackagePolicy.isValidProcessName(packageName, processName)) return@forEach
            val normalized = ZygiskConfigEntry(packageName, processName)
            unique[normalized.toLine()] = normalized
        }
        return ZygiskConfig(unique.values.sortedWith(compareBy<ZygiskConfigEntry> { it.packageName }.thenBy { it.processName ?: "" }))
    }

    fun toFileContent(): String {
        val normalizedEntries = normalized().entries
        if (normalizedEntries.isEmpty()) return ""
        return normalizedEntries.joinToString(separator = "\n", postfix = "\n") { it.toLine() }
    }

    companion object {
        fun parse(content: String): ZygiskConfig {
            val entries = content
                .lineSequence()
                .mapNotNull(::parseEntry)
                .toList()
            return ZygiskConfig(entries).normalized()
        }

        fun fromPackages(packages: Iterable<String>): ZygiskConfig {
            return ZygiskConfig(packages.map { ZygiskConfigEntry(it) }).normalized()
        }

        private fun parseEntry(rawLine: String): ZygiskConfigEntry? {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return null
            val parts = line.split('|', limit = 2)
            val packageName = parts[0].trim()
            val processName = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            if (!ZygiskPackagePolicy.isManagedPackage(packageName)) return null
            if (processName != null && !ZygiskPackagePolicy.isValidProcessName(packageName, processName)) return null
            return ZygiskConfigEntry(packageName, processName)
        }
    }
}

object ZygiskPackagePolicy {
    private val deniedPackagePrefixes = listOf(
        "android.",
        "com.android.",
        "com.google.android.",
        "com.mi.",
        "com.miui.",
        "com.milink.",
        "com.mipay.",
        "com.xiaomi.",
        "miui.",
    )

    private val packageNameRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val processNameRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+(\\:[A-Za-z0-9_.-]+)?")

    fun isManagedPackage(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (normalized == "android") return false
        if (!packageNameRegex.matches(normalized)) return false
        return deniedPackagePrefixes.none { prefix -> normalized.startsWith(prefix) }
    }

    fun isValidProcessName(packageName: String, processName: String): Boolean {
        val normalized = processName.trim()
        if (!processNameRegex.matches(normalized)) return false
        return normalized == packageName || normalized.startsWith("$packageName:")
    }
}
