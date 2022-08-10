package com.xiaomi.xmsf.push.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.TextUtils;

import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.utils.ColorUtil;


import top.trumeet.common.cache.ApplicationNameCache;
import top.trumeet.common.cache.IconCache;
import top.trumeet.common.utils.NotificationUtils;

import static top.trumeet.common.utils.NotificationUtils.*;

import java.util.Map;

/**
 * @author Trumeet
 * @date 2018/1/25
 */

public class NotificationController {

    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";

    private static final String ID_GROUP_APPLICATIONS = "applications";

    public static final String CHANNEL_WARN = "warn";


    @TargetApi(26)
    public static void deleteOldNotificationChannelGroup(@NonNull Context context) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannelGroup(ID_GROUP_APPLICATIONS);
        } catch (Exception ignore) {

        }

    }

    @TargetApi(26)
    private static NotificationChannelGroup createGroupWithPackage(@NonNull String packageName,
                                                                   @NonNull CharSequence appName) {
        return new NotificationChannelGroup(getGroupIdByPkg(packageName), appName);
    }

    @TargetApi(26)
    private static NotificationChannel createChannelWithPackage(@NonNull PushMetaInfo metaInfo,
                                                                @NonNull String packageName) {
        final Map<String, String> extra = metaInfo.getExtra();
        String channelName = getExtraField(extra, EXTRA_CHANNEL_NAME, "未分类");
        String channelDescription = getExtraField(extra, EXTRA_CHANNEL_DESCRIPTION, null);
        String sound = getExtraField(extra, EXTRA_SOUND_URL, null);

        NotificationChannel channel = new NotificationChannel(getChannelId(metaInfo, packageName),
                channelName, NotificationManager.IMPORTANCE_DEFAULT);
        if (channelDescription != null) {
            channel.setDescription(channelDescription);
        }
        if (sound != null) {
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            channel.setSound(Uri.parse(sound), attr);
        }
        return channel;
    }

    private static String getChannelId(@NonNull PushMetaInfo metaInfo,
                                       @NonNull String packageName) {
        final Map<String, String> extra = metaInfo.getExtra();
        String channelId = getExtraField(extra, EXTRA_CHANNEL_ID, "");
        return getChannelIdByPkg(packageName) + "_" + channelId;
    }


    public static NotificationChannel registerChannelIfNeeded(Context context, PushMetaInfo metaInfo, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = getChannelId(metaInfo, packageName);
        NotificationChannel notificationChannel = manager.getNotificationChannel(channelId);

        if (notificationChannel != null) {
            final boolean isValidGroup =
                    !ID_GROUP_APPLICATIONS.equals(notificationChannel.getGroup()) &&
                            !TextUtils.isEmpty(notificationChannel.getGroup());
            if (isValidGroup) {
                return notificationChannel;
            }
            manager.deleteNotificationChannel(channelId);
        }

        CharSequence appName = ApplicationNameCache.getInstance().getAppName(context, packageName);
        if (appName == null) {
            return null;
        }

        return createNotificationChannel(metaInfo, packageName, manager, appName);

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel createNotificationChannel(PushMetaInfo metaInfo, String packageName, NotificationManager manager, CharSequence appName) {
        NotificationChannelGroup notificationChannelGroup = createGroupWithPackage(packageName, appName);
        manager.createNotificationChannelGroup(notificationChannelGroup);

        NotificationChannel notificationChannel = createChannelWithPackage(metaInfo, packageName);
        notificationChannel.setGroup(notificationChannelGroup.getId());

        manager.createNotificationChannel(notificationChannel);
        return notificationChannel;
    }


    @TargetApi(Build.VERSION_CODES.N)
    private static void updateSummaryNotification(Context context, PushMetaInfo metaInfo, String packageName, String groupId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationCntInGroup = getNotificationCountOfGroup(groupId, manager);


        if (notificationCntInGroup > 1) {

            CharSequence appName = ApplicationNameCache.getInstance().getAppName(context, packageName);

            Bundle extras = new Bundle();
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, getChannelId(metaInfo, packageName));
                builder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
            } else {
                builder = new Notification.Builder(context);
            }

            int color = getIconColor(context, packageName);
            if (color != Notification.COLOR_DEFAULT) {
                CharSequence subText = ColorUtil.createColorSubtext(appName, color);
                if (subText != null) {
                    extras.putCharSequence(NotificationCompat.EXTRA_SUB_TEXT, subText);
                }
                builder.setColor(color);
            } else {
                extras.putCharSequence(NotificationCompat.EXTRA_SUB_TEXT, appName);
            }

            builder.setExtras(extras);

            // Set small icon
            NotificationController.processSmallIcon(context, packageName, builder);

            builder.setCategory(Notification.CATEGORY_EVENT)
                    .setGroupSummary(true)
                    .setGroup(groupId);
            Notification notification = builder.build();
            manager.notify(groupId.hashCode(), notification);
        } else {
            manager.cancel(groupId.hashCode());
        }
    }

    private static int getNotificationCountOfGroup(String groupId, NotificationManager manager) {
        StatusBarNotification[] activeNotifications = manager.getActiveNotifications();


        int notificationCntInGroup = 0;
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (groupId.equals(statusBarNotification.getNotification().getGroup())) {
                notificationCntInGroup++;
            }
        }
        return notificationCntInGroup;
    }

    public static void publish(Context context, PushMetaInfo metaInfo, int notificationId, String packageName, Notification.Builder localBuilder) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Make the behavior consistent with official MIUI
        Bundle extras = new Bundle();
        extras.putString("target_package", packageName);
        localBuilder.addExtras(extras);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //Forward Compatibility
            registerChannelIfNeeded(context, metaInfo, packageName);

            localBuilder.setChannelId(getChannelId(metaInfo, packageName));
            localBuilder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
        } else {
            //for VERSION < Oero
            localBuilder.setDefaults(Notification.DEFAULT_ALL);
            localBuilder.setPriority(Notification.PRIORITY_HIGH);
        }

        Notification notification = localBuilder.build();
        manager.notify(notificationId, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateSummaryNotification(context, metaInfo, packageName, notification.getGroup());
        }
    }


    public static void cancel(Context context, XmPushActionContainer container, int id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String groupId = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            StatusBarNotification[] activeNotifications = manager.getActiveNotifications();
            for (StatusBarNotification activeNotification : activeNotifications) {
                if (activeNotification.getId() == id) {
                    groupId = activeNotification.getNotification().getGroup();
                    break;
                }

            }
        }

        manager.cancel(id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (groupId != null) {
                updateSummaryNotification(context, container.getMetaInfo(), container.getPackageName(), groupId);
            }
        }
    }


    /**
     * @param ctx context
     * @param pkg packageName
     * @return 0 if not processed
     */
    public static int getIconColor(final Context ctx, final String pkg) {
        return IconCache.getInstance().getAppColor(ctx, pkg, (ctx1, iconBitmap) -> {
            if (iconBitmap == null) {
                return Notification.COLOR_DEFAULT;
            }
            int color = ColorUtil.getIconColor(iconBitmap);
            if (color != Notification.COLOR_DEFAULT) {
                final float[] hsl = new float[3];
                ColorUtils.colorToHSL(color, hsl);
                hsl[1] = 0.94f;
                hsl[2] = Math.min(hsl[2] * 0.6f, 0.31f);
                return ColorUtils.HSLToColor(hsl);
            } else {
                return Notification.COLOR_DEFAULT;
            }
        });
    }


    public static void processSmallIcon(Context context, String packageName, Notification.Builder notificationBuilder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int iconSmallId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON);
            if (iconSmallId <= 0) {

                Icon iconCache = IconCache.getInstance().getIconCache(context, packageName, (ctx, b) -> Icon.createWithBitmap(b));
                if (iconCache != null) {
                    notificationBuilder.setSmallIcon(iconCache);
                } else {
                    notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
                }

            } else {
                notificationBuilder.setSmallIcon(Icon.createWithResource(packageName, iconSmallId));
            }
        } else {
            notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        }
    }


    public static void buildExtraSubText(Context var0, String packageName, Notification.Builder localBuilder, Bundle extras) {
        CharSequence appName = ApplicationNameCache.getInstance().getAppName(var0, packageName);
        int color = getIconColor(var0, packageName);
        if (color != Notification.COLOR_DEFAULT) {
            CharSequence subText = ColorUtil.createColorSubtext(appName, color);
            if (subText != null) {
                extras.putCharSequence(NotificationCompat.EXTRA_SUB_TEXT, subText);
            }
            localBuilder.setColor(color);
        } else {
            extras.putCharSequence(NotificationCompat.EXTRA_SUB_TEXT, appName);
        }
    }



    private static int getIconId(Context context, String str, String str2) {
        return context.getResources().getIdentifier(str2, "drawable", str);
    }



    public static void test(Context context, String packageName, String title, String description) {
        NotificationController.registerChannelIfNeeded(context, new PushMetaInfo(), packageName);

        int id = (int) (System.currentTimeMillis() / 1000L);

        Notification.Builder localBuilder = new Notification.Builder(context);

        Notification.BigTextStyle style = new Notification.BigTextStyle();
        style.bigText(description);
        style.setBigContentTitle(title);
        style.setSummaryText(description);
        localBuilder.setStyle(style);
        NotificationController.processSmallIcon(context, packageName, localBuilder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            localBuilder.setWhen(System.currentTimeMillis());
            localBuilder.setShowWhen(true);
        }

        NotificationController.publish(context, new PushMetaInfo(), id, packageName, localBuilder);
    }

}
