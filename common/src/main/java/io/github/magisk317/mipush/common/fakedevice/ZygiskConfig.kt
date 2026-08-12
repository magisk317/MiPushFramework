package io.github.magisk317.mipush.common.fakedevice

data class ZygiskConfigEntry(
    val packageName: String,
    val processName: String? = null,
    val enabled: Boolean = true,
) {
    val isPackageWide: Boolean
        get() = processName.isNullOrBlank()

    fun matchesPackage(packageName: String): Boolean = this.packageName == packageName

    fun toLine(): String {
        val value = if (processName.isNullOrBlank()) {
            packageName
        } else {
            "$packageName|$processName"
        }
        return if (enabled) value else "-$value"
    }
}

data class ZygiskConfig(
    val entries: List<ZygiskConfigEntry> = emptyList(),
    val profile: String = DEFAULT_PROFILE,
    val observe: Boolean = false,
    val autoScan: Boolean = false,
) {
    fun isEnabledForPackage(packageName: String): Boolean {
        return entries.any { it.enabled && it.matchesPackage(packageName) } &&
            entries.none { !it.enabled && it.processName.isNullOrBlank() && it.matchesPackage(packageName) }
    }

    fun enabledPackages(): Set<String> {
        return entries.filter { it.enabled }.mapTo(linkedSetOf()) { it.packageName }
    }

    fun withPackageEnabled(packageName: String, enabled: Boolean): ZygiskConfig {
        val normalizedPackage = packageName.trim()
        val kept = entries.filterNot { it.matchesPackage(normalizedPackage) }.toMutableList()
        if (enabled && ZygiskPackagePolicy.isManagedPackage(normalizedPackage)) {
            kept += ZygiskConfigEntry(normalizedPackage)
        }
        return copy(entries = kept).normalized()
    }

    fun normalized(): ZygiskConfig {
        val unique = LinkedHashMap<String, ZygiskConfigEntry>()
        entries.forEach { entry ->
            val packageName = entry.packageName.trim()
            val processName = entry.processName?.trim()?.takeIf { it.isNotEmpty() }
            if (!ZygiskPackagePolicy.isManagedPackage(packageName)) return@forEach
            if (processName != null && !ZygiskPackagePolicy.isValidProcessName(packageName, processName)) return@forEach
            val normalized = ZygiskConfigEntry(packageName, processName, entry.enabled)
            unique[normalized.toLine()] = normalized
        }
        return copy(entries = unique.values.sortedWith(compareBy<ZygiskConfigEntry> { it.packageName }.thenBy { it.processName ?: "" }.thenBy { it.enabled }))
    }

    fun toFileContent(): String {
        val normalizedEntries = normalized().entries
        val metadata = listOf(
            "profile=${profile.takeIf { it in SUPPORTED_PROFILES } ?: DEFAULT_PROFILE}",
            "observe=$observe",
            "auto_scan=$autoScan",
        )
        return (metadata + normalizedEntries.map { it.toLine() }).joinToString(separator = "\n", postfix = "\n")
    }

    companion object {
        fun parse(content: String): ZygiskConfig {
            val entries = content
                .lineSequence()
                .mapNotNull(::parseEntry)
                .toList()
            val metadata = content.lineSequence().map { it.trim() }
            val profile = metadata.firstOrNull { it.startsWith("profile=") }?.substringAfter('=') ?: DEFAULT_PROFILE
            val observe = metadata.firstOrNull { it.startsWith("observe=") }?.substringAfter('=')?.toBooleanStrictOrNull() ?: false
            val autoScan = metadata.firstOrNull { it.startsWith("auto_scan=") }?.substringAfter('=')?.toBooleanStrictOrNull() ?: false
            return ZygiskConfig(entries, profile, observe, autoScan).normalized()
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
            val enabled = !packageName.startsWith("-")
            val normalizedPackage = packageName.removePrefix("-")
            if (!ZygiskPackagePolicy.isManagedPackage(normalizedPackage)) return null
            if (processName != null && !ZygiskPackagePolicy.isValidProcessName(normalizedPackage, processName)) return null
            return ZygiskConfigEntry(normalizedPackage, processName, enabled)
        }

        const val DEFAULT_PROFILE = "miui14"
        val SUPPORTED_PROFILES = setOf("miui14", "hyperos1", "legacy-v11")
    }
}

object ZygiskPackagePolicy {
    // No vendor/system package denylist: any well-formed package may be configured.
    // Unwanted apps are handled by per-app blocked flag, not static prefixes.

    private val packageNameRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val processNameRegex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+(\\:[A-Za-z0-9_.-]+)?")

    fun isManagedPackage(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (normalized.isEmpty() || normalized == "android") return false
        return packageNameRegex.matches(normalized)
    }

    fun isValidProcessName(packageName: String, processName: String): Boolean {
        val normalized = processName.trim()
        if (!processNameRegex.matches(normalized)) return false
        return normalized == packageName || normalized.startsWith("$packageName:")
    }
}
