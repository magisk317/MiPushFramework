package io.github.magisk317.mipush.feature.diagnostic

import androidx.annotation.StringRes
import io.github.magisk317.mipush.common.NotificationStyle
import io.github.magisk317.mipush.common.R

enum class MockNotificationKind(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val focusTemplateStyle: NotificationStyle? = null,
) {
    PLAIN(R.string.mock_kind_plain, R.string.mock_kind_plain_desc),
    BIG_TEXT(R.string.mock_kind_big_text, R.string.mock_kind_big_text_desc),
    BIG_PICTURE(R.string.mock_kind_big_picture, R.string.mock_kind_big_picture_desc),
    INBOX(R.string.mock_kind_inbox, R.string.mock_kind_inbox_desc),
    MESSAGING(R.string.mock_kind_messaging, R.string.mock_kind_messaging_desc),
    MEDIA(R.string.mock_kind_media, R.string.mock_kind_media_desc),
    PROGRESS(R.string.mock_kind_progress, R.string.mock_kind_progress_desc),
    HEADS_UP(R.string.mock_kind_heads_up, R.string.mock_kind_heads_up_desc),
    DYNAMIC_ISLAND(R.string.mock_kind_dynamic_island, R.string.mock_kind_dynamic_island_desc),
    FOCUS_NOTIFICATION(
        R.string.mock_kind_focus_notification,
        R.string.mock_kind_focus_notification_desc,
        NotificationStyle.GENERAL,
    ),
    FOCUS_MESSAGE(
        R.string.mock_kind_focus_message,
        R.string.mock_kind_focus_message_desc,
        NotificationStyle.MESSAGE,
    ),
    FOCUS_BANNER(
        R.string.mock_kind_focus_banner,
        R.string.mock_kind_focus_banner_desc,
        NotificationStyle.BANNER,
    ),
    FOCUS_ALERT(
        R.string.mock_kind_focus_alert,
        R.string.mock_kind_focus_alert_desc,
        NotificationStyle.ALERT,
    ),
    FOCUS_PROMO(
        R.string.mock_kind_focus_promo,
        R.string.mock_kind_focus_promo_desc,
        NotificationStyle.PROMO,
    ),
    FOCUS_MEDIA(
        R.string.mock_kind_focus_media,
        R.string.mock_kind_focus_media_desc,
        NotificationStyle.MEDIA,
    ),
    FOCUS_PROGRESS(
        R.string.mock_kind_focus_progress,
        R.string.mock_kind_focus_progress_desc,
        NotificationStyle.PROGRESS,
    ),
    VOIP_INCOMING(R.string.mock_kind_voip, R.string.mock_kind_voip_desc),
    LIVE_UPDATE_DELIVERY(R.string.mock_kind_live_update, R.string.mock_kind_live_update_desc);
}
