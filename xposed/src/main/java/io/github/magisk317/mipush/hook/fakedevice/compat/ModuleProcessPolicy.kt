package io.github.magisk317.mipush.hook.fakedevice.compat

object ModuleProcessPolicy {
    private val defaultAllowedProcessSuffixes = setOf(
        ":push",
        ":pushservice",
        ":mipush",
        ":mipushservice",
        ":wschannel",
        ":channel",
        ":xmpush",
    )

    private val defaultDeniedProcessPrefixes = listOf(
        ":sandboxed_process",
        ":widgetProcess",
        ":webview",
        ":renderer",
        ":gpu",
        ":isolated",
    )

    fun shouldHandleProcess(packageName: String, processName: String): Boolean {
        return shouldHandleProcess(ModuleCompatRegistry.getProfile(packageName), packageName, processName)
    }

    internal fun shouldHandleProcess(
        profile: ModuleCompatProfile?,
        packageName: String,
        processName: String,
    ): Boolean {
        if (profile == null) {
            return false
        }
        if (profile.isAutoDetected) {
            return processName.isNotBlank()
        }
        if (processName == packageName) {
            return true
        }
        if (!processName.startsWith("$packageName:")) {
            return false
        }
        val suffix = processName.removePrefix(packageName)
        val deniedPrefixes = profile.deniedProcessPrefixes ?: defaultDeniedProcessPrefixes
        if (deniedPrefixes.any { suffix.startsWith(it) }) {
            return false
        }
        val allowedSuffixes = profile.allowedProcessSuffixes ?: defaultAllowedProcessSuffixes
        return suffix in allowedSuffixes
    }
}
