package top.trumeet.common.utils;

import static top.trumeet.common.utils.NotificationUtils.getExtraField;

import java.util.HashMap;
import java.util.Map;

public class CustomConfiguration {
    private static String Config(String name) {
        return "__mi_push_" + name;
    }

    private static final String SUB_TEXT = Config("sub_text");
    private static final String ROUND_LARGE_ICON = Config("round_large_icon");
    private static final String USE_MESSAGING_STYLE = Config("use_messaging_style");
    private static final String CONVERSATION_TITLE = Config("conversation_title");
    private static final String CONVERSATION_ID = Config("conversation_id");
    private static final String CONVERSATION_ICON = Config("conversation_icon");
    private static final String CONVERSATION_IMPORTANT = Config("conversation_important");
    private static final String CONVERSATION_SENDER = Config("conversation_sender");
    private static final String CONVERSATION_SENDER_ID = Config("conversation_sender_id");
    private static final String CONVERSATION_SENDER_ICON = Config("conversation_sender_icon");
    private static final String CONVERSATION_MESSAGE = Config("conversation_message");

    private Map<String, String> mExtra = new HashMap<>();

    public CustomConfiguration(Map<String, String> extra) {
        if (extra != null) {
            mExtra = extra;
        }
    }

    public String subText(String defaultValue) {
        return get(SUB_TEXT, defaultValue);
    }

    public boolean roundLargeIcon(boolean defaultValue) {
        return get(ROUND_LARGE_ICON, defaultValue);
    }

    public boolean useMessagingStyle(boolean defaultValue) {
        return get(USE_MESSAGING_STYLE, defaultValue);
    }

    public String conversationTitle(String defaultValue) {
        return get(CONVERSATION_TITLE, defaultValue);
    }

    public String conversationId(String defaultValue) {
        return get(CONVERSATION_ID, defaultValue);
    }

    public String conversationIcon(String defaultValue) {
        return get(CONVERSATION_ICON, defaultValue);
    }

    public boolean conversationImportant(boolean defaultValue) {
        return get(CONVERSATION_IMPORTANT, defaultValue);
    }

    public String conversationSender(String defaultValue) {
        return get(CONVERSATION_SENDER, defaultValue);
    }

    public String conversationSenderId(String defaultValue) {
        return get(CONVERSATION_SENDER_ID, defaultValue);
    }

    public String conversationSenderIcon(String defaultValue) {
        return get(CONVERSATION_SENDER_ICON, defaultValue);
    }

    public String conversationMessage(String defaultValue) {
        return get(CONVERSATION_MESSAGE, defaultValue);
    }

    private boolean get(String key, boolean defaultValue) {
        if (getExtraField(mExtra, key, null) != null) {
            return true;
        }
        return defaultValue;
    }

    private String get(String key, String defaultValue) {
        return getExtraField(mExtra, key, defaultValue);
    }
}
