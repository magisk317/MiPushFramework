package io.github.magisk317.mipush.common

import io.github.magisk317.mipush.runtime.store.kmp.NotificationClassifier as KmpNotificationClassifier

/** Android-side adapter delegating to the platform-neutral classifier. */
object NotificationClassifier {
    fun classify(
        title: String,
        content: String,
        packageName: String? = null,
        channelId: String? = null,
        channelName: String? = null,
    ): NotificationStyle = KmpNotificationClassifier.classify(title, content, packageName, channelId, channelName)
}
