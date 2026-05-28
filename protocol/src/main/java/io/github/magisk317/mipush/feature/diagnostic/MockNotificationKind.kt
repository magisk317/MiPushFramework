package io.github.magisk317.mipush.feature.diagnostic

import android.content.Context

enum class MockNotificationKind(val labelKey: String, val descKey: String) {
    PLAIN("mock_kind_plain", "mock_kind_plain_desc"),
    BIG_TEXT("mock_kind_big_text", "mock_kind_big_text_desc"),
    BIG_PICTURE("mock_kind_big_picture", "mock_kind_big_picture_desc"),
    INBOX("mock_kind_inbox", "mock_kind_inbox_desc"),
    MESSAGING("mock_kind_messaging", "mock_kind_messaging_desc"),
    MEDIA("mock_kind_media", "mock_kind_media_desc"),
    PROGRESS("mock_kind_progress", "mock_kind_progress_desc"),
    HEADS_UP("mock_kind_heads_up", "mock_kind_heads_up_desc"),
    DYNAMIC_ISLAND("mock_kind_dynamic_island", "mock_kind_dynamic_island_desc"),
    FOCUS_NOTIFICATION("mock_kind_focus_notification", "mock_kind_focus_notification_desc"),
    VOIP_INCOMING("mock_kind_voip", "mock_kind_voip_desc"),
    LIVE_UPDATE_DELIVERY("mock_kind_live_update", "mock_kind_live_update_desc");

    fun getLabelRes(context: Context): Int {
        return context.resources.getIdentifier(labelKey, "string", context.packageName)
    }

    fun getDescRes(context: Context): Int {
        return context.resources.getIdentifier(descKey, "string", context.packageName)
    }
}
