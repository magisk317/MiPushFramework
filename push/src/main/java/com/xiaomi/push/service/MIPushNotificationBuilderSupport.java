package com.xiaomi.push.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.notification.BannerBuilder;
import com.xiaomi.push.service.notification.BuilderCompat;
import com.xiaomi.push.service.notification.ColorfulBuilder;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.util.Map;

final class MIPushNotificationBuilderSupport {
    private static final String STYLE_TYPE = "notification_style_type";
    private static final String STYLE_BIG_PICTURE = "2";
    private static final String STYLE_BIG_TEXT = "1";
    private static final String STYLE_COLORFUL = "3";
    private static final String STYLE_BANNER = "4";
    private static final String STYLE_BIG_PICTURE_URI = "notification_bigPic_uri";
    private static final String BANNER_IMAGE_URI = "notification_banner_image_uri";
    private static final String COLORFUL_BUTTON_TEXT = "notification_colorful_button_text";
    private static final String COLORFUL_BUTTON_NOTIFY_EFFECT = "notification_colorful_button_notify_effect";
    private static final String COLORFUL_BUTTON_INTENT_URI = "notification_colorful_button_intent_uri";
    private static final String COLORFUL_BUTTON_INTENT_CLASS = "notification_colorful_button_intent_class";
    private static final String COLORFUL_BUTTON_WEB_URI = "notification_colorful_button_web_uri";
    private static final String COLORFUL_BUTTON_BG_COLOR = "notification_colorful_button_bg_color";
    private static final String COLORFUL_BG_COLOR = "notification_colorful_bg_color";
    private static final String COLORFUL_BG_IMAGE_URI = "notification_colorful_bg_image_uri";
    private static final String NOTIFICATION_SMALL_ICON_URI = "notification_small_icon_uri";
    private static final String NOTIFICATION_LARGE_ICON_URI = "notification_large_icon_uri";
    private static final String NOTIFICATION_CHANNEL_NAME = "channel_name";
    private static final String NOTIFICATION_CHANNEL_IMPORTANCE = "channel_importance";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "channel_description";
    private static final String NOTIFICATION_CHANNEL_ID = "channel_id";
    private static final String NOTIFICATION_PRIORITY = "notification_priority";
    private static final String NOTIFICATION_TIMEOUT = "timeout";

    private MIPushNotificationBuilderSupport() {
    }

    static BuilderCompat createBuilder(Context context, RemoteViews remoteViews, Map<String, String> map, String str, String str2, int i) {
        if (remoteViews != null) {
            return new BuilderCompat(context).setCustomContentView(remoteViews);
        }
        if (map == null || !map.containsKey(STYLE_TYPE)) {
            return new BuilderCompat(context);
        }
        return createBuilderWithStyle(context, map, str, str2, i);
    }

    static void applyBaseStyle(BuilderCompat builderCompat, Context context, XmPushActionContainer xmPushActionContainer, Map<String, String> map, String[] strArr, PendingIntent pendingIntent) {
        builderCompat.setContentTitle(strArr[0]);
        builderCompat.setContentText(strArr[1]);
        builderCompat.setWhen(System.currentTimeMillis());
        String str = map == null ? null : map.get(MIPushNotificationHelper.NOTIFICATION_SHOW_WHEN);
        if (TextUtils.isEmpty(str)) {
            if (Build.VERSION.SDK_INT >= 24) {
                builderCompat.setShowWhen(true);
            }
        } else {
            builderCompat.setShowWhen(Boolean.parseBoolean(str));
        }
        builderCompat.setContentIntent(pendingIntent);
        MIPushNotificationActionSupport.setNotificationStyleAction(builderCompat, context, xmPushActionContainer.getPackageName(), map);
    }

    static void applyIcons(Context context, BuilderCompat builderCompat, Map<String, String> map, String str, int i, int i2) {
        if (i > 0 && i2 > 0) {
            builderCompat.setLargeIcon(MIPushNotificationViewSupport.getBitmapFromId(context, i));
            builderCompat.setSmallIcon(i2);
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                Bitmap onlinePictureResource = map == null ? null : MIPushOnlineResourceSupport.getOnlinePictureResource(context, map.get(NOTIFICATION_SMALL_ICON_URI), true);
                if (onlinePictureResource != null) {
                    builderCompat.setSmallIcon(Icon.createWithBitmap(onlinePictureResource));
                } else {
                    builderCompat.setSmallIcon(Icon.createWithResource(str, NotificationUtils.getIdForSmallIconFromTargetPkg(context, str)));
                }
            } catch (Throwable unused) {
                builderCompat.setSmallIcon(MIPushNotificationViewSupport.getIdForSmallIcon(context, str));
            }
        } else {
            builderCompat.setSmallIcon(MIPushNotificationViewSupport.getIdForSmallIcon(context, str));
        }
        if (map != null) {
            Bitmap onlinePictureResource2 = MIPushOnlineResourceSupport.getOnlinePictureResource(context, map.get(NOTIFICATION_LARGE_ICON_URI), false);
            if (onlinePictureResource2 != null) {
                builderCompat.setLargeIcon(onlinePictureResource2);
            }
        }
    }

    static void applyMetadata(Context context, BuilderCompat builderCompat, Map<String, String> map, int i, String str) {
        if (map != null) {
            if (!TextUtils.isEmpty(map.get(MIPushNotificationHelper.NOTIFICATION_TICKER))) {
                builderCompat.setTicker(map.get(MIPushNotificationHelper.NOTIFICATION_TICKER));
            }
            if (Build.VERSION.SDK_INT >= 16) {
                builderCompat.setPriority(getPriority(map));
            }
        }
        builderCompat.setDefaults(i);
        if (map != null && (i & 1) != 0) {
            String str2 = map.get(MIPushNotificationHelper.NOTIFICATION_SOUND_URI);
            if (!TextUtils.isEmpty(str2) && str2.startsWith(MIPushNotificationHelper.ANDROID_RESOURCE + str)) {
                builderCompat.setDefaults(i ^ 1);
                builderCompat.setSound(Uri.parse(str2));
            }
        }
        builderCompat.setAutoCancel(true);
    }

    static void ensureChannel(Context context, BuilderCompat builderCompat, String str, Map<String, String> map) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, str);
        String str2 = map == null ? null : map.get(NOTIFICATION_CHANNEL_ID);
        if (TextUtils.isEmpty(str2)) {
            str2 = NotificationManagerHelper.DEFAULT_ID;
        }
        String mipushChannelId = notificationManagerHelperFrom.getMipushChannelId(str2);
        if (notificationManagerHelperFrom.getNotificationChannel(mipushChannelId) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(mipushChannelId, getChannelName(context, str, map), getChannelImportance(map));
            setChannelDescription(notificationChannel, map);
            notificationManagerHelperFrom.createNotificationChannel(notificationChannel);
        }
        builderCompat.setChannelId(mipushChannelId);
        int timeout = getTimeout(map);
        if (timeout > 0) {
            builderCompat.setTimeoutAfter(timeout * 1000L);
        }
    }

    private static BuilderCompat createBuilderWithStyle(Context context, Map<String, String> map, String str, String str2, int i) {
        PendingIntent stylePendingIntent;
        String str3 = map.get(STYLE_TYPE);
        if (Build.VERSION.SDK_INT >= 16 && STYLE_BIG_PICTURE.equals(str3)) {
            BuilderCompat builderCompat = new BuilderCompat(context);
            Bitmap onlinePictureResource = TextUtils.isEmpty(map.get(STYLE_BIG_PICTURE_URI)) ? null : MIPushOnlineResourceSupport.getOnlinePictureResource(context, map.get(STYLE_BIG_PICTURE_URI), false);
            if (onlinePictureResource == null) {
                MyLog.w("can not get big picture.");
                return builderCompat;
            }
            Notification.BigPictureStyle bigPictureStyle = new Notification.BigPictureStyle(builderCompat);
            bigPictureStyle.bigPicture(onlinePictureResource);
            bigPictureStyle.setSummaryText(str);
            bigPictureStyle.bigLargeIcon((Bitmap) null);
            builderCompat.setStyle(bigPictureStyle);
            return builderCompat;
        }
        if (Build.VERSION.SDK_INT >= 16 && STYLE_BIG_TEXT.equals(str3)) {
            BuilderCompat builderCompat2 = new BuilderCompat(context);
            builderCompat2.setStyle(new Notification.BigTextStyle().bigText(str));
            return builderCompat2;
        }
        if (STYLE_BANNER.equals(str3) && MIUIUtils.isXMSF(context)) {
            BannerBuilder bannerBuilder = new BannerBuilder(context, str2);
            if (!TextUtils.isEmpty(map.get(BANNER_IMAGE_URI))) {
                bannerBuilder.setBanner(MIPushOnlineResourceSupport.getOnlinePictureResource(context, map.get(BANNER_IMAGE_URI), false));
            }
            bannerBuilder.setPushExtra(map);
            return bannerBuilder;
        }
        if (STYLE_COLORFUL.equals(str3) && MIUIUtils.isXMSF(context)) {
            ColorfulBuilder colorfulBuilder = new ColorfulBuilder(context, i, str2);
            if (!TextUtils.isEmpty(map.get(COLORFUL_BUTTON_TEXT)) && (stylePendingIntent = MIPushNotificationActionSupport.getStylePendingIntent(context, str2, map, COLORFUL_BUTTON_NOTIFY_EFFECT, COLORFUL_BUTTON_INTENT_URI, COLORFUL_BUTTON_INTENT_CLASS, COLORFUL_BUTTON_WEB_URI)) != null) {
                colorfulBuilder.addAction(map.get(COLORFUL_BUTTON_TEXT), stylePendingIntent).setActionBackground(map.get(COLORFUL_BUTTON_BG_COLOR));
            }
            if (!TextUtils.isEmpty(map.get(COLORFUL_BG_COLOR))) {
                colorfulBuilder.setBackground(map.get(COLORFUL_BG_COLOR));
            } else if (!TextUtils.isEmpty(map.get(COLORFUL_BG_IMAGE_URI))) {
                colorfulBuilder.setBackground(MIPushOnlineResourceSupport.getOnlinePictureResource(context, map.get(COLORFUL_BG_IMAGE_URI), false));
            }
            colorfulBuilder.setPushExtra(map);
            return colorfulBuilder;
        }
        return new BuilderCompat(context);
    }

    private static int getChannelImportance(Map<String, String> map) {
        int i = 3;
        if (map != null) {
            String str = map.get(NOTIFICATION_CHANNEL_IMPORTANCE);
            if (!TextUtils.isEmpty(str)) {
                try {
                    MyLog.v("importance=" + str);
                    i = Integer.parseInt(str);
                } catch (Exception e) {
                    MyLog.e("parsing channel importance error: " + e);
                }
            }
        }
        return i;
    }

    private static String getChannelName(Context context, String str, Map<String, String> map) {
        return (map == null || TextUtils.isEmpty(map.get(NOTIFICATION_CHANNEL_NAME))) ? AppInfoUtils.getAppLabel(context, str) : map.get(NOTIFICATION_CHANNEL_NAME);
    }

    private static int getPriority(Map<String, String> map) {
        int i = 0;
        if (map != null) {
            String str = map.get(NOTIFICATION_PRIORITY);
            if (!TextUtils.isEmpty(str)) {
                try {
                    MyLog.v("priority=" + str);
                    i = Integer.parseInt(str);
                } catch (Exception e) {
                    MyLog.e("parsing notification priority error: " + e);
                }
            }
        }
        return i;
    }

    private static int getTimeout(Map<String, String> map) {
        String str = map == null ? null : map.get(NOTIFICATION_TIMEOUT);
        int i = 0;
        if (!TextUtils.isEmpty(str)) {
            try {
                i = Integer.parseInt(str);
            } catch (Exception unused) {
                i = 0;
            }
        }
        return i;
    }

    private static void setChannelDescription(Object obj, Map<String, String> map) {
        if (map == null || TextUtils.isEmpty(map.get(NOTIFICATION_CHANNEL_DESCRIPTION))) {
            return;
        }
        JavaCalls.callMethod(obj, "setDescription", map.get(NOTIFICATION_CHANNEL_DESCRIPTION));
    }
}
