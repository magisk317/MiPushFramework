package io.github.magisk317.mipush.common.utils

class CustomConfiguration(extra: Map<String, String>?) {
    companion object {
        private fun config(name: String): String {
            return "__mi_push_$name"
        }

        private val SUB_TEXT = config("sub_text")
        private val ROUND_LARGE_ICON = config("round_large_icon")
        private val USE_MESSAGING_STYLE = config("use_messaging_style")
        private val CONVERSATION_TITLE = config("conversation_title")
        private val CONVERSATION_ID = config("conversation_id")
        private val CONVERSATION_ICON = config("conversation_icon")
        private val CONVERSATION_IMPORTANT = config("conversation_important")
        private val CONVERSATION_SENDER = config("conversation_sender")
        private val CONVERSATION_SENDER_ID = config("conversation_sender_id")
        private val CONVERSATION_SENDER_ICON = config("conversation_sender_icon")
        private val CONVERSATION_MESSAGE = config("conversation_message")
        private val CLEAR_GROUP = config("clear_group")
        private val BORROW_CHANNEL_ID = config("borrow_channel_id")
        private val TEXT_ICON = config("text_icon")

        private const val NOTIFICATION_LARGE_ICON_URI = "notification_large_icon_uri"
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_NAME = "channel_name"
        private const val CHANNEL_DESCRIPTION = "channel_description"
        private const val SOUND_URL = "sound_url"
        private const val JOBKEY = "jobkey"
        private const val USE_CLICKED_ACTIVITY = "use_clicked_activity"
        private const val NOTIFICATION_GROUP = "notification_group"
        private const val NOTIFICATION_BIGPIC_URI = "notification_bigPic_uri"
        private const val FOCUS_PARAM = "miui.focus.param"

        private fun getExtraField(
            extra: Map<String, String>?,
            extraChannelName: String,
            defaultValue: String?
        ): String? {
            return if (extra != null && extra.containsKey(extraChannelName)) {
                extra[extraChannelName]
            } else defaultValue
        }
    }

    private var mExtra: Map<String, String> = extra ?: HashMap()

    fun subText(defaultValue: String?): String? = get(SUB_TEXT, defaultValue)

    fun roundLargeIcon(defaultValue: Boolean): Boolean = get(ROUND_LARGE_ICON, defaultValue)

    fun useMessagingStyle(defaultValue: Boolean): Boolean = get(USE_MESSAGING_STYLE, defaultValue)

    fun conversationTitle(defaultValue: String?): String? = get(CONVERSATION_TITLE, defaultValue)

    fun conversationId(defaultValue: String?): String? = get(CONVERSATION_ID, defaultValue)

    fun conversationIcon(defaultValue: String?): String? = get(CONVERSATION_ICON, defaultValue)

    fun conversationImportant(defaultValue: Boolean): Boolean = get(CONVERSATION_IMPORTANT, defaultValue)

    fun conversationSender(defaultValue: String?): String? = get(CONVERSATION_SENDER, defaultValue)

    fun conversationSenderId(defaultValue: String?): String? = get(CONVERSATION_SENDER_ID, defaultValue)

    fun conversationSenderIcon(defaultValue: String?): String? = get(CONVERSATION_SENDER_ICON, defaultValue)

    fun conversationMessage(defaultValue: String?): String? = get(CONVERSATION_MESSAGE, defaultValue)

    fun notificationLargeIconUri(defaultValue: String?): String? =
        get(NOTIFICATION_LARGE_ICON_URI, defaultValue)

    fun channelId(defaultValue: String?): String? = get(CHANNEL_ID, defaultValue)

    fun channelName(defaultValue: String?): String? = get(CHANNEL_NAME, defaultValue)

    fun channelDescription(defaultValue: String?): String? = get(CHANNEL_DESCRIPTION, defaultValue)

    fun soundUrl(defaultValue: String?): String? = get(SOUND_URL, defaultValue)

    fun jobkey(defaultValue: String?): String? = get(JOBKEY, defaultValue)

    fun useClickedActivity(defaultValue: Boolean): Boolean = get(USE_CLICKED_ACTIVITY, defaultValue)

    fun notificationGroup(defaultValue: String?): String? = get(NOTIFICATION_GROUP, defaultValue)

    fun notificationBigPicUri(defaultValue: String?): String? = get(NOTIFICATION_BIGPIC_URI, defaultValue)

    fun clearGroup(defaultValue: Boolean): Boolean = get(CLEAR_GROUP, defaultValue)

    fun borrowChannelId(defaultValue: String?): String? = get(BORROW_CHANNEL_ID, defaultValue)

    fun focusParam(defaultValue: String?): String? = get(FOCUS_PARAM, defaultValue)

    fun textIcon(defaultValue: String?): String? = get(TEXT_ICON, defaultValue)

    operator fun get(key: String, defaultValue: Boolean): Boolean {
        return if (getExtraField(mExtra, key, null) != null) {
            true
        } else defaultValue
    }

    operator fun get(key: String, defaultValue: String?): String? {
        return getExtraField(mExtra, key, defaultValue)
    }

    fun keys(): Set<String> {
        return mExtra.keys
    }
}
