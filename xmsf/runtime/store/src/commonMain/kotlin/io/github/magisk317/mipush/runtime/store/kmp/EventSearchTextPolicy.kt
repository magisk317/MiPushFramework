package io.github.magisk317.mipush.runtime.store.kmp

import io.github.magisk317.mipush.runtime.core.event.EventSearchTextPolicy as CoreEventSearchTextPolicy

/**
 * Persistence compatibility facade for event-search text snapshots.
 *
 * This facade retains the store package used by Android adapters while the platform-neutral text
 * composition rule lives in [CoreEventSearchTextPolicy].
 */
object EventSearchTextPolicy {
    fun compose(
        packageName: String?,
        applicationName: String? = null,
        title: String? = null,
        summary: String? = null,
    ): String = CoreEventSearchTextPolicy.compose(
        packageName = packageName,
        applicationName = applicationName,
        title = title,
        summary = summary,
    )
}
