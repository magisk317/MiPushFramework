package io.github.magisk317.mipush.platform.support

internal object DeviceIdleWhitelistPolicy {
    fun areAllWhitelisted(
        packages: Collection<String>,
        isWhitelisted: (String) -> Boolean,
    ): Boolean {
        val targets = packages.filter(String::isNotBlank).distinct()
        return targets.isNotEmpty() && targets.all(isWhitelisted)
    }
}
