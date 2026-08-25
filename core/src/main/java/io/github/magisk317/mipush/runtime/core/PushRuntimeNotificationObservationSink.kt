package io.github.magisk317.mipush.runtime.core

/** Product-owned sink for low-cardinality notification observations from the stock push observer. */
interface PushRuntimeNotificationObservationSink {
    fun observeNotificationEvent(
        packageName: String?,
        event: String,
        source: String,
    )
}
