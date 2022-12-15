package top.trumeet.common.utils;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * @author Trumeet
 * @date 2018/1/30
 */

public class NotificationUtils {
    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_CHANNEL_DESCRIPTION = "channel_description";
    public static final String EXTRA_SOUND_URL = "sound_url";
    public static final String EXTRA_JOBKEY = "jobkey";
    public static final String EXTRA_SUB_TEXT = "__mi_push_sub_text";

    public static String getChannelIdByPkg(@NonNull String packageName) {
        // update version 2
        return "ch_" + packageName;
    }

    public static String getGroupIdByPkg(@NonNull String packageName) {
        return "gp_" + packageName;
    }

    public static String getPackageName(@NonNull String groupOrChannel) {
        return groupOrChannel.substring(3);
    }

    public static String getExtraField(Map<String, String> extra, String extraChannelName, String defaultValue) {
        return extra != null && extra.containsKey(extraChannelName) ?
                extra.get(extraChannelName) : defaultValue;
    }

}
