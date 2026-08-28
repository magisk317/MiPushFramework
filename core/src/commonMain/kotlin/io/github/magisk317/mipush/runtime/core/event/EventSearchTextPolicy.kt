package io.github.magisk317.mipush.runtime.core.event

/**
 * Platform-neutral composition for an event-search text snapshot.
 *
 * Android adapters resolve localized display fields; this policy preserves their stable order,
 * omits blank values, and removes duplicate text before persistence.
 */
object EventSearchTextPolicy {
    fun compose(
        packageName: String?,
        applicationName: String? = null,
        title: String? = null,
        summary: String? = null,
    ): String = buildList {
        add(packageName)
        add(applicationName)
        add(title)
        add(summary)
    }
        .filterNotNull()
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(separator = " ")
}
