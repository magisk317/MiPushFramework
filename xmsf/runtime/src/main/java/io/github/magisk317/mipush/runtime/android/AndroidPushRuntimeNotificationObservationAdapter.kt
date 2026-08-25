package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushRuntimeNotificationObservationSink

/** Android runtime implementation of the product-owned notification observation contract. */
object AndroidPushRuntimeNotificationObservationAdapter : PushRuntimeNotificationObservationSink {
    override fun observeNotificationEvent(
        packageName: String?,
        event: String,
        source: String,
    ) {
        AndroidPushRuntime.observeNotificationEvent(packageName, event, source)
    }
}
