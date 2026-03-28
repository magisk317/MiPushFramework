package com.xiaomi.push.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Pair;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushNotificationHelper.class */
public class MIPushNotificationHelper {
    private static final String ALLOW_DYNAMIC_ICON_ON_MIUI = "__adiom";
    public static final String ANDROID_RESOURCE = "android.resource://";
    private static final String DYNAMIC_ICON_URI = "__dynamic_icon_uri";
    public static final String EXTRA_PARAM_NOTIFY_FOREGROUND = "notify_foreground";
    public static final String EXTRA_PARAM_SHOW_AT_TAIL = "miui.showAtTail";
    public static final String FROM_NOTIFICATION = "mipush_notified";
    public static final int MAX_DOWNLOAD_ONLINE_PICTURE_WAIT = 180;
    public static final int MAX_NOTIFY_ID_CACHE_SIZE = 100;
    private static final String MESSAGE_TYPE_INDEX = "satuigmo";
    public static final String MIUI_PACKAGE_NAME = "miui_package_name";
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_LEFT = 1;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_MID = 2;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT = 3;
    private static final String NOTIFICATION_BANNER_IMAGE_URI = "notification_banner_image_uri";
    private static final String NOTIFICATION_BKG_COLOR = "background_color";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "channel_description";
    private static final String NOTIFICATION_CHANNEL_ID = "channel_id";
    private static final String NOTIFICATION_CHANNEL_IMPORTANCE = "channel_importance";
    private static final String NOTIFICATION_CHANNEL_NAME = "channel_name";
    private static final String NOTIFICATION_COLORFUL_BG_COLOR = "notification_colorful_bg_color";
    private static final String NOTIFICATION_COLORFUL_BG_IMAGE_URI = "notification_colorful_bg_image_uri";
    private static final String NOTIFICATION_COLORFUL_BUTTON_BG_COLOR = "notification_colorful_button_bg_color";
    private static final String NOTIFICATION_COLORFUL_BUTTON_INTENT_CLASS = "notification_colorful_button_intent_class";
    private static final String NOTIFICATION_COLORFUL_BUTTON_INTENT_URI = "notification_colorful_button_intent_uri";
    private static final String NOTIFICATION_COLORFUL_BUTTON_NOTIFY_EFFECT = "notification_colorful_button_notify_effect";
    private static final String NOTIFICATION_COLORFUL_BUTTON_TEXT = "notification_colorful_button_text";
    private static final String NOTIFICATION_COLORFUL_BUTTON_WEB_URI = "notification_colorful_button_web_uri";
    public static final String NOTIFICATION_CUSTOM_BUILDER_SET_TITLE = "custom_builder_set_title";
    private static final String NOTIFICATION_ENABLE_FLOAT = "enable_float";
    private static final String NOTIFICATION_ENABLE_KEYGUARD = "enable_keyguard";
    private static final String NOTIFICATION_EXTRA_MESSAGE_ID_STRING = "message_id";
    public static final String NOTIFICATION_EXTRA_SHOW_AT_TAIL = "miui.showAtTail";
    public static final String NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING = "target_package";
    public static final String NOTIFICATION_GROUP = "notification_group";
    private static final String NOTIFICATION_ICON = "mipush_notification";
    public static final String NOTIFICATION_IMAGE_TEXT_COLOR = "notification_image_text_color";
    public static final String NOTIFICATION_IS_SUMMARY = "notification_is_summary";
    private static final String NOTIFICATION_LARGE_ICON_URI = "notification_large_icon_uri";
    static final String NOTIFICATION_LOCAL_EXTRA_CREATE_TIME_LONG = "mipush_org_when";
    private static final String NOTIFICATION_MIUI_IS_GRAYSCALE_ICON = "miui.isGrayscaleIcon";
    private static final String NOTIFICATION_PRIORITY = "notification_priority";
    public static final String NOTIFICATION_SHOW_WHEN = "notification_show_when";
    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";
    private static final String NOTIFICATION_SMALL_ICON_COLOR = "notification_small_icon_color";
    private static final String NOTIFICATION_SMALL_ICON_URI = "notification_small_icon_uri";
    public static final String NOTIFICATION_SOUND_URI = "sound_uri";
    private static final String NOTIFICATION_STYLE_BANNER = "4";
    private static final String NOTIFICATION_STYLE_BIG_PICTURE = "2";
    private static final String NOTIFICATION_STYLE_BIG_PICTURE_URI = "notification_bigPic_uri";
    private static final String NOTIFICATION_STYLE_BIG_TEXT = "1";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri";
    private static final String NOTIFICATION_STYLE_COLORFUL = "3";
    private static final String NOTIFICATION_STYLE_TYPE = "notification_style_type";
    public static final String NOTIFICATION_TICKER = "ticker";
    private static final String NOTIFICATION_TIMEOUT = "timeout";
    public static final int NOTIFY_ALL = -1;
    public static final long NOTIFY_INTERVAL = 10000;
    public static final int NO_NOTIFY_ID = -2;
    private static final String PREF_KEY_NOTIFY_TYPE = "pref_notify_type";
    private static final String TOP_NOTIFICATION_LOCAL_EXTRA_FLAG_BOOLEAN = MIPushTopNotificationSupport.LOCAL_FLAG;
    private static final String TOP_NOTIFICATION_LOCAL_EXTRA_FREQUENCY_INT = MIPushTopNotificationSupport.LOCAL_FREQUENCY;
    private static final String TOP_NOTIFICATION_LOCAL_EXTRA_PERIOD_INT = MIPushTopNotificationSupport.LOCAL_PERIOD;
    public static long lastNotify = 0;
    private static final LinkedList<Pair<Integer, XmPushActionContainer>> notifyContainerCache = new LinkedList<>();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushNotificationHelper$GetNotificationResult.class */
    public static class GetNotificationResult {
        Notification notification;
        long trafficSize = 0;
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushNotificationHelper$NotifyPushMessageInfo.class */
    public static class NotifyPushMessageInfo {
        public String targetPkgName;
        public long traffic = 0;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        return MIPushNotificationViewSupport.drawableToBitmap(drawable);
    }

    static void clearLocalNotifyType(Context context, String str) {
        context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).edit().remove(str).commit();
    }

    public static void clearNotification(Context context, String str) {
        MIPushNotificationCacheSupport.clearNotification(context, str, notifyContainerCache);
    }

    public static void clearNotification(Context context, String str, int i) {
        MIPushNotificationCacheSupport.clearNotification(context, str, i, notifyContainerCache);
    }

    public static void clearNotification(Context context, String str, String str2, String str3) {
        MIPushNotificationCacheSupport.clearNotification(context, str, str2, str3, notifyContainerCache);
    }

    public static String getInterfaceId(XmPushActionContainer xmPushActionContainer) {
        return isBusinessMessage(xmPushActionContainer) ? ReportConstants.AWAKE_EVENT_CHAIN_INTERFACE_ID : isNormalNotificationMessage(xmPushActionContainer) ? ReportConstants.NOTIFICATION_EVENT_CHAIN_INTERFACE_ID : isPassThoughMessage(xmPushActionContainer) ? ReportConstants.THROUGH_EVENT_CHAIN_INTERFACE_ID : isRegisterMessage(xmPushActionContainer) ? ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID : "";
    }

    static int getLocalNotifyType(Context context, String str) {
        return context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).getInt(str, Integer.MAX_VALUE);
    }

    static String getTargetPackage(XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo;
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(xmPushActionContainer.packageName) && (metaInfo = xmPushActionContainer.getMetaInfo()) != null && metaInfo.getExtra() != null) {
            String str = metaInfo.getExtra().get(MIUI_PACKAGE_NAME);
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return xmPushActionContainer.packageName;
    }

    static boolean hasLocalNotifyType(Context context, String str) {
        return context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).contains(str);
    }

    public static boolean isApplicationForeground(Context context, String str) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            if (runningAppProcessInfo.importance == 100 && Arrays.asList(runningAppProcessInfo.pkgList).contains(str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBusinessMessage(XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        return isIdVaild(metaInfo) && metaInfo.isIgnoreRegInfo();
    }

    private static boolean isIdVaild(PushMetaInfo pushMetaInfo) {
        boolean z = false;
        if (pushMetaInfo != null) {
            String id = pushMetaInfo.getId();
            z = false;
            if (!TextUtils.isEmpty(id)) {
                z = false;
                if (id.length() == 22) {
                    z = false;
                    if (MESSAGE_TYPE_INDEX.indexOf(id.charAt(0)) >= 0) {
                        z = true;
                    }
                }
            }
        }
        return z;
    }

    public static boolean isNPBMessage(XmPushActionContainer xmPushActionContainer) {
        return isBusinessMessage(xmPushActionContainer) || isNormalNotificationMessage(xmPushActionContainer) || isPassThoughMessage(xmPushActionContainer);
    }

    public static boolean isNormalNotificationMessage(XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        return isIdVaild(metaInfo) && metaInfo.passThrough == 0 && !isBusinessMessage(xmPushActionContainer);
    }

    public static boolean isNotifyForeground(Map<String, String> map) {
        if (map == null || !map.containsKey(EXTRA_PARAM_NOTIFY_FOREGROUND)) {
            return true;
        }
        return "1".equals(map.get(EXTRA_PARAM_NOTIFY_FOREGROUND));
    }

    public static boolean isPassThoughMessage(XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        boolean z = true;
        if (!isIdVaild(metaInfo) || metaInfo.passThrough != 1 || isBusinessMessage(xmPushActionContainer)) {
            z = false;
        }
        return z;
    }

    public static boolean isRegisterMessage(XmPushActionContainer xmPushActionContainer) {
        return xmPushActionContainer.getAction() == ActionType.Registration;
    }

    public static NotifyPushMessageInfo notifyPushMessage(Context context, XmPushActionContainer xmPushActionContainer, byte[] bArr) {
        return MIPushNotificationPublishSupport.notifyPushMessage(context, xmPushActionContainer, bArr, notifyContainerCache);
    }

    public static void onNotificationRemoved(Context context, StatusBarNotification statusBarNotification) {
        MIPushTopNotificationSupport.onNotificationRemoved(context, statusBarNotification);
    }

    static void setLocalNotifyType(Context context, String str, int i) {
        context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).edit().putInt(str, i).commit();
    }

    public static void uploadClearMessageData(Context context, LinkedList<? extends Object> linkedList) {
        MIPushNotificationCacheSupport.uploadClearMessageData(context, linkedList);
    }
}
