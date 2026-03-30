package com.xiaomi.push.service;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.notification.BuilderCompat;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

final class MIPushNotificationActionSupport {
    private static final String STYLE_TYPE = "notification_style_type";
    private static final String STYLE_COLORFUL = "3";
    private static final String STYLE_BANNER = "4";
    private static final String STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect";
    private static final String STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri";
    private static final String STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class";
    private static final String STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri";
    private static final String STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name";
    private static final String STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect";
    private static final String STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri";
    private static final String STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class";
    private static final String STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri";
    private static final String STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name";
    private static final String STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect";
    private static final String STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri";
    private static final String STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class";
    private static final String STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri";
    private static final String STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name";

    private MIPushNotificationActionSupport() {
    }

    static PendingIntent getClickedPendingIntent(Context context, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo, byte[] bArr, int i) {
        Intent intent;
        int i2 = -1;
        if (MIPushNotificationHelper.isNormalNotificationMessage(xmPushActionContainer)) {
            i2 = 1000;
        } else if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
            i2 = 3000;
        }
        String id = pushMetaInfo != null ? pushMetaInfo.getId() : "";
        if (pushMetaInfo != null && !TextUtils.isEmpty(pushMetaInfo.url)) {
            Intent intent2 = new Intent("android.intent.action.VIEW");
            intent2.setData(Uri.parse(pushMetaInfo.url));
            intent2.addFlags(268435456);
            intent2.putExtra("messageId", id);
            intent2.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, i2);
            return PendingIntent.getActivity(context, 0, intent2, 134217728);
        }
        if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
            intent = new Intent();
            intent.setComponent(new ComponentName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, "com.xiaomi.mipush.sdk.PushMessageHandler"));
            intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr);
            intent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
            intent.addCategory(String.valueOf(i));
            intent.addCategory(String.valueOf(id));
        } else {
            intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
            intent.setComponent(new ComponentName(xmPushActionContainer.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler"));
            intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr);
            intent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
            intent.addCategory(String.valueOf(i));
            intent.addCategory(String.valueOf(id));
        }
        intent.putExtra("messageId", id);
        intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, i2);
        ComponentName componentName = new ComponentName(xmPushActionContainer.packageName, "com.xiaomi.mipush.sdk.BridgeActivity");
        if (!ComponentHelper.checkActivity(context, componentName)) {
            return PendingIntent.getService(context, 0, intent, 134217728);
        }
        Intent intent3 = new Intent();
        intent3.setComponent(componentName);
        intent3.addFlags(276824064);
        intent3.putExtra(PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, intent);
        intent3.addCategory(String.valueOf(i));
        intent3.addCategory(String.valueOf(id));
        return PendingIntent.getActivity(context, 0, intent3, 134217728);
    }

    static BuilderCompat setNotificationStyleAction(BuilderCompat builderCompat, Context context, String str, Map<String, String> map) {
        if (map == null || TextUtils.equals(STYLE_COLORFUL, map.get(STYLE_TYPE)) || TextUtils.equals(STYLE_BANNER, map.get(STYLE_TYPE))) {
            return builderCompat;
        }
        PendingIntent stylePendingIntent = getStylePendingIntent(context, str, map, STYLE_BUTTON_LEFT_NOTIFY_EFFECT, STYLE_BUTTON_LEFT_INTENT_URI, STYLE_BUTTON_LEFT_INTENT_CLASS, STYLE_BUTTON_LEFT_WEB_URI);
        if (stylePendingIntent != null && !TextUtils.isEmpty(map.get(STYLE_BUTTON_LEFT_NAME))) {
            builderCompat.addAction(0, map.get(STYLE_BUTTON_LEFT_NAME), stylePendingIntent);
        }
        PendingIntent stylePendingIntent2 = getStylePendingIntent(context, str, map, STYLE_BUTTON_MID_NOTIFY_EFFECT, STYLE_BUTTON_MID_INTENT_URI, STYLE_BUTTON_MID_INTENT_CLASS, STYLE_BUTTON_MID_WEB_URI);
        if (stylePendingIntent2 != null && !TextUtils.isEmpty(map.get(STYLE_BUTTON_MID_NAME))) {
            builderCompat.addAction(0, map.get(STYLE_BUTTON_MID_NAME), stylePendingIntent2);
        }
        PendingIntent stylePendingIntent3 = getStylePendingIntent(context, str, map, STYLE_BUTTON_RIGHT_NOTIFY_EFFECT, STYLE_BUTTON_RIGHT_INTENT_URI, STYLE_BUTTON_RIGHT_INTENT_CLASS, STYLE_BUTTON_RIGHT_WEB_URI);
        if (stylePendingIntent3 != null && !TextUtils.isEmpty(map.get(STYLE_BUTTON_RIGHT_NAME))) {
            builderCompat.addAction(0, map.get(STYLE_BUTTON_RIGHT_NAME), stylePendingIntent3);
        }
        return builderCompat;
    }

    static PendingIntent getStylePendingIntent(Context context, String str, Map<String, String> map, String str2, String str3, String str4, String str5) {
        Intent pendingIntentFromExtra;
        if (map == null || (pendingIntentFromExtra = getPendingIntentFromExtra(context, str, map, str2, str3, str4, str5)) == null) {
            return null;
        }
        return PendingIntent.getActivity(context, 0, pendingIntentFromExtra, PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent getPendingIntentFromExtra(Context context, String str, Map<String, String> map, String str2, String str3, String str4, String str5) {
        String str6 = map.get(str2);
        if (TextUtils.isEmpty(str6)) {
            return null;
        }
        Intent intent = null;
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(str6)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(str);
            } catch (Exception e) {
                MyLog.e("Cause:" + e.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(str6)) {
            if (map.containsKey(str3)) {
                String str7 = map.get(str3);
                if (str7 != null) {
                    try {
                        intent = Intent.parseUri(str7, 1);
                        intent.setPackage(str);
                    } catch (URISyntaxException e2) {
                        MyLog.e("Cause:" + e2.getMessage());
                    }
                }
            } else if (map.containsKey(str4)) {
                Intent intent2 = new Intent();
                intent2.setComponent(new ComponentName(str, map.get(str4)));
                intent = intent2;
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(str6)) {
            String str8 = map.get(str5);
            if (!TextUtils.isEmpty(str8)) {
                String strTrim = str8.trim();
                if (!strTrim.startsWith("http://") && !strTrim.startsWith("https://")) {
                    strTrim = "http://" + strTrim;
                }
                try {
                    String protocol = new URL(strTrim).getProtocol();
                    if ("http".equals(protocol) || "https".equals(protocol)) {
                        Intent intent3 = new Intent("android.intent.action.VIEW");
                        intent3.setData(Uri.parse(strTrim));
                        NotificationUtils.setXiaomiBrowserAsDefault(context, intent3);
                        intent = intent3;
                    }
                } catch (MalformedURLException e3) {
                    MyLog.e("Cause:" + e3.getMessage());
                }
            }
        }
        if (intent == null) {
            return null;
        }
        intent.addFlags(268435456);
        try {
            if (context.getPackageManager().resolveActivity(intent, 65536) != null) {
                return intent;
            }
        } catch (Exception e4) {
            MyLog.e("Cause:" + e4.getMessage());
        }
        return null;
    }
}
