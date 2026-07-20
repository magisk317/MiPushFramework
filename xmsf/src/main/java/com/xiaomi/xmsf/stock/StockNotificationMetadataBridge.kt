package com.xiaomi.xmsf.stock

import android.os.Bundle
import com.xiaomi.xmpush.thrift.PushMetaInfo

/** Copies stock XMSF notification-control and collection metadata into final Notification extras. */
internal object StockNotificationMetadataBridge {
    private const val SOURCE_SKIP_ASSISTANTS = "hyper_nms_skip_assistants"
    private const val TARGET_SKIP_ASSISTANTS = "skip_assistants"
    private const val SOURCE_SKIP_GROUP_OPT = "hyper_skip_group_opt"
    private const val TARGET_SKIP_GROUP_OPT = "miui_skip_group_opt"

    private val stringMappings = mapOf(
        "use_clicked_activity" to "xmsf.stat.useNCA",
        "high_priority_event" to "xmsf.stat.highPriorityEvent",
        "msg_busi_type" to "xmsf.stat.msgBusiType",
        "simplify_pull_type" to "xmsf.stat.sPullType",
    )

    fun apply(metaInfo: PushMetaInfo, target: Bundle) {
        val source = metaInfo.extra ?: return
        source[SOURCE_SKIP_ASSISTANTS]
            ?.takeIf(String::isNotEmpty)
            ?.let { target.putBoolean(TARGET_SKIP_ASSISTANTS, it.toBoolean()) }
        source[SOURCE_SKIP_GROUP_OPT]
            ?.takeIf(String::isNotEmpty)
            ?.let { target.putBoolean(TARGET_SKIP_GROUP_OPT, it.toBoolean()) }
        stringMappings.forEach { (sourceKey, targetKey) ->
            source[sourceKey]
                ?.takeIf(String::isNotEmpty)
                ?.let { target.putString(targetKey, it) }
        }
    }
}
