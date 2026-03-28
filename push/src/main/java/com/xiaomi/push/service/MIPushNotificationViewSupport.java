package com.xiaomi.push.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.push.service.notification.BuilderCompat;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.util.Map;

final class MIPushNotificationViewSupport {
    private static final String NOTIFICATION_ICON = "mipush_notification";
    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";

    private MIPushNotificationViewSupport() {
    }

    static String[] determineTitleAndDespByDIP(Context context, PushMetaInfo pushMetaInfo) {
        String str;
        String title = pushMetaInfo.getTitle();
        String description = pushMetaInfo.getDescription();
        Map<String, String> extra = pushMetaInfo.getExtra();
        String str2 = title;
        String str3 = description;
        if (extra != null) {
            int iIntValue = Float.valueOf((context.getResources().getDisplayMetrics().widthPixels / context.getResources().getDisplayMetrics().density) + 0.5f).intValue();
            if (iIntValue <= 320) {
                String str4 = extra.get("title_short");
                if (!TextUtils.isEmpty(str4)) {
                    title = str4;
                }
                String str5 = extra.get("description_short");
                str2 = title;
                str = description;
                if (!TextUtils.isEmpty(str5)) {
                    str = str5;
                    str2 = title;
                }
            } else {
                str2 = title;
                str = description;
                if (iIntValue > 360) {
                    String str6 = extra.get("title_long");
                    if (!TextUtils.isEmpty(str6)) {
                        title = str6;
                    }
                    String str7 = extra.get("description_long");
                    str2 = title;
                    str3 = description;
                    if (!TextUtils.isEmpty(str7)) {
                        str3 = str7;
                        str2 = title;
                    }
                }
            }
            str3 = str;
        }
        return new String[]{str2, str3};
    }

    static RemoteViews getNotificationForCustomLayout(Context context, XmPushActionContainer xmPushActionContainer) {
        return MIPushNotificationCustomLayoutSupport.getNotificationForCustomLayout(context, xmPushActionContainer);
    }

    static MIPushNotificationHelper.GetNotificationResult getNotificationForLargeIcons(Context context, XmPushActionContainer xmPushActionContainer, RemoteViews remoteViews, PendingIntent pendingIntent, int i) {
        MIPushNotificationHelper.GetNotificationResult getNotificationResult = new MIPushNotificationHelper.GetNotificationResult();
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
        Map<String, String> extra = metaInfo.getExtra();
        String[] strArrDetermineTitleAndDespByDIP = determineTitleAndDespByDIP(context, metaInfo);
        BuilderCompat builderCompat = MIPushNotificationBuilderSupport.createBuilder(context, remoteViews, extra, strArrDetermineTitleAndDespByDIP[1], targetPackage, i);
        MIPushNotificationBuilderSupport.applyBaseStyle(builderCompat, context, xmPushActionContainer, extra, strArrDetermineTitleAndDespByDIP, pendingIntent);
        int iconId = getIconId(context, targetPackage, NOTIFICATION_ICON);
        int iconId2 = getIconId(context, targetPackage, NOTIFICATION_SMALL_ICON);
        MIPushNotificationBuilderSupport.applyIcons(context, builderCompat, extra, targetPackage, iconId, iconId2);
        int i2 = metaInfo.notifyType;
        if (MIPushNotificationHelper.hasLocalNotifyType(context, targetPackage)) {
            i2 = MIPushNotificationHelper.getLocalNotifyType(context, targetPackage);
        }
        MIPushNotificationBuilderSupport.applyMetadata(context, builderCompat, extra, i2, targetPackage);
        MIPushNotificationBuilderSupport.ensureChannel(context, builderCompat, targetPackage, extra);
        Notification notificationBuild = builderCompat.build();
        notificationBuild.flags |= 16;
        getNotificationResult.notification = notificationBuild;
        return getNotificationResult;
    }

    static int getIdForSmallIcon(Context context, String str) {
        int iconId = getIconId(context, str, NOTIFICATION_ICON);
        int iconId2 = getIconId(context, str, NOTIFICATION_SMALL_ICON);
        if (iconId > 0) {
            iconId2 = iconId;
        } else if (iconId2 <= 0) {
            iconId2 = context.getApplicationInfo().icon;
        }
        if (iconId2 == 0 && Build.VERSION.SDK_INT >= 9) {
            iconId2 = context.getApplicationInfo().logo;
        }
        return iconId2;
    }

    static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int intrinsicWidth = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 1;
        int intrinsicHeight = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 1;
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmapCreateBitmap;
    }

    static Bitmap getBitmapFromId(Context context, int i) {
        return drawableToBitmap(context.getResources().getDrawable(i));
    }

    private static int getIconId(Context context, String str, String str2) {
        if (str.equals(context.getPackageName())) {
            return context.getResources().getIdentifier(str2, "drawable", str);
        }
        return 0;
    }

}
