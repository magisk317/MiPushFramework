package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.NotificationClassifier as CommonClassifier
import io.github.magisk317.mipush.common.NotificationStyle

/**
 * xmsf 模块的通知分类器 - 适配 PushMetaInfo，委托给 common 模块的统一分类器
 */
object NotificationClassifier {

    fun classify(
        metaInfo: PushMetaInfo?,
        packageName: String,
        channelId: String? = null,
        channelName: String? = null,
    ): NotificationStyle {
        return CommonClassifier.classify(
            title = metaInfo?.title ?: "",
            content = metaInfo?.description ?: "",
            packageName = packageName,
            channelId = channelId,
            channelName = channelName,
        )
    }
}
