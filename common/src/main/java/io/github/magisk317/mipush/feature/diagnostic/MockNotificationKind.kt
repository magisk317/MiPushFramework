package io.github.magisk317.mipush.feature.diagnostic

import android.content.Context
import io.github.magisk317.mipush.common.NotificationStyle

enum class MockNotificationKind(
    val labelKey: String,
    val descKey: String,
    val focusTemplateStyle: NotificationStyle? = null,
) {
    PLAIN("mock_kind_plain", "mock_kind_plain_desc"),
    BIG_TEXT("mock_kind_big_text", "mock_kind_big_text_desc"),
    BIG_PICTURE("mock_kind_big_picture", "mock_kind_big_picture_desc"),
    INBOX("mock_kind_inbox", "mock_kind_inbox_desc"),
    MESSAGING("mock_kind_messaging", "mock_kind_messaging_desc"),
    MEDIA("mock_kind_media", "mock_kind_media_desc"),
    PROGRESS("mock_kind_progress", "mock_kind_progress_desc"),
    HEADS_UP("mock_kind_heads_up", "mock_kind_heads_up_desc"),
    DYNAMIC_ISLAND("mock_kind_dynamic_island", "mock_kind_dynamic_island_desc"),
    FOCUS_NOTIFICATION(
        "mock_kind_focus_notification",
        "mock_kind_focus_notification_desc",
        NotificationStyle.GENERAL,
    ),
    FOCUS_MESSAGE(
        "mock_kind_focus_message",
        "mock_kind_focus_message_desc",
        NotificationStyle.MESSAGE,
    ),
    FOCUS_BANNER(
        "mock_kind_focus_banner",
        "mock_kind_focus_banner_desc",
        NotificationStyle.BANNER,
    ),
    FOCUS_ALERT(
        "mock_kind_focus_alert",
        "mock_kind_focus_alert_desc",
        NotificationStyle.ALERT,
    ),
    FOCUS_PROMO(
        "mock_kind_focus_promo",
        "mock_kind_focus_promo_desc",
        NotificationStyle.PROMO,
    ),
    FOCUS_MEDIA(
        "mock_kind_focus_media",
        "mock_kind_focus_media_desc",
        NotificationStyle.MEDIA,
    ),
    FOCUS_PROGRESS(
        "mock_kind_focus_progress",
        "mock_kind_focus_progress_desc",
        NotificationStyle.PROGRESS,
    ),
    VOIP_INCOMING("mock_kind_voip", "mock_kind_voip_desc"),
    LIVE_UPDATE_DELIVERY("mock_kind_live_update", "mock_kind_live_update_desc");

    fun getLabelRes(context: Context): Int {
        return context.resources.getIdentifier(labelKey, "string", context.packageName)
    }

    fun getDescRes(context: Context): Int {
        return context.resources.getIdentifier(descKey, "string", context.packageName)
    }
}
