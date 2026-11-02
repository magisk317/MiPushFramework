package io.github.magisk317.mipush.core.zygisk

object ZygiskPackagePolicy {
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
