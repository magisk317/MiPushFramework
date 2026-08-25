package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Platform-neutral composition policy for the persisted event-search snapshot.
 *
 * Android adapters resolve localized names and event text; this policy owns only stable ordering,
 * blank filtering, and de-duplication before the result is persisted in EVENT.search_text.
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
