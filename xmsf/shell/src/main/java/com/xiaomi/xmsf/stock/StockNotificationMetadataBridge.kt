package com.xiaomi.xmsf.stock

import android.os.Bundle
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo

/**
 * Copies stock XMSF notification identity, control, and collection metadata into final extras.
 *
 * Stock 7.4.67-C t0 writes `message_id` before posting and g1.u later requires it when selecting
 * managed active notifications. SDK 3.7.9 and 7.4.67-C also preserve typed policy fields consumed
 * by MIUI SystemUI. The old custom path skipped them, so restore the fields here without restoring
 * stock error reporting or uploads for malformed values.
 */
internal object StockNotificationMetadataBridge {
    private const val TARGET_MESSAGE_ID = "message_id"
    private const val SOURCE_SKIP_ASSISTANTS = "hyper_nms_skip_assistants"
    private const val TARGET_SKIP_ASSISTANTS = "skip_assistants"
    private const val SOURCE_SKIP_GROUP_OPT = "hyper_skip_group_opt"
    private const val TARGET_SKIP_GROUP_OPT = "miui_skip_group_opt"
    private const val MESSAGE_COUNT = "message_count"
    private const val TARGET_MESSAGE_COUNT = "miui.messageCount"
    private const val SHOW_AT_TAIL = "miui.showAtTail"
    private const val FOLD_TIMEOUT = "miui.fold.timeout"
    private const val ENABLE_KEYGUARD = "enable_keyguard"
    private const val TARGET_ENABLE_KEYGUARD = "miui.enableKeyguard"
    private const val ENABLE_FLOAT = "enable_float"
    private const val TARGET_ENABLE_FLOAT = "miui.enableFloat"
    private const val SECTION_IS_PRIORITY = "section_is_prr"
    private const val SECTION_PRIORITY_CLASS = "section_prr_cl"
    private const val TARGET_IS_PRIORITY = "is_priority"
    private const val TARGET_PRIORITY_CLASS = "mipush_class"
    private const val DISABLE_NOTIFICATION_FLAGS = "disable_notification_flags"
    private const val MILLIS_PER_SECOND = 1_000L

    /**
     * Payload metadata keys copied verbatim under the stock SystemUI names.
     *
     * `use_clicked_activity` is the only carrier of the "click opens an activity rather than a
     * service" decision. Stock reads it through `CustomConfiguration.useClickedActivity`; this
     * product owns the click route itself (the pending intent shape follows the payload, and the
     * fallback follows the per-app `click_fallback_enabled` switch), so the field is forwarded to
     * SystemUI here and deliberately has no `CustomConfiguration` accessor.
     */
    private val stringMappings = mapOf(
        "use_clicked_activity" to "xmsf.stat.useNCA",
        "high_priority_event" to "xmsf.stat.highPriorityEvent",
        "msg_busi_type" to "xmsf.stat.msgBusiType",
        "simplify_pull_type" to "xmsf.stat.sPullType",
    )

    fun apply(
        metaInfo: PushMetaInfo,
        target: Bundle,
        isMiui: Boolean = MIUIUtils.isMIUI(),
    ) {
        metaInfo.id
            ?.takeIf(String::isNotEmpty)
            ?.let { target.putString(TARGET_MESSAGE_ID, it) }
        val source = metaInfo.extra.orEmpty()
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
        // SDK 3.7.9 gates message count/show-at-tail to MIUI; 7.4.67-C does the same for
        // message count and disable flags. The old product bridge had no equivalent fields.
        if (isMiui) {
            source[MESSAGE_COUNT]
                ?.toIntOrNull()
                ?.let { target.putInt(TARGET_MESSAGE_COUNT, it) }
            target.putBoolean(SHOW_AT_TAIL, source[SHOW_AT_TAIL] == "true")
            source[DISABLE_NOTIFICATION_FLAGS]
                ?.takeIf(String::isNotEmpty)
                ?.let { target.putString(DISABLE_NOTIFICATION_FLAGS, it) }
        }
        source[FOLD_TIMEOUT]
            ?.toLongOrNull()
            ?.takeIf { it > 0L && it <= Long.MAX_VALUE / MILLIS_PER_SECOND }
            ?.let { target.putLong(FOLD_TIMEOUT, it * MILLIS_PER_SECOND) }
        source[ENABLE_KEYGUARD]
            ?.takeIf(String::isNotEmpty)
            ?.let { target.putBoolean(TARGET_ENABLE_KEYGUARD, it.toBoolean()) }
        source[ENABLE_FLOAT]
            ?.takeIf(String::isNotEmpty)
            ?.let { target.putBoolean(TARGET_ENABLE_FLOAT, it.toBoolean()) }
        val isPriority = source[SECTION_IS_PRIORITY]?.toIntOrNull() ?: -1
        val priorityClass = source[SECTION_PRIORITY_CLASS]?.toIntOrNull() ?: -1
        if (isPriority >= 0 && priorityClass >= 0) {
            target.putInt(TARGET_IS_PRIORITY, isPriority)
            target.putInt(TARGET_PRIORITY_CLASS, priorityClass)
        }
    }
}
