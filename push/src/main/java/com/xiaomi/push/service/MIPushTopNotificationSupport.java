package com.xiaomi.push.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class MIPushTopNotificationSupport {
    static final String EXTRA_FREQUENCY = "notification_top_frequency";
    static final String EXTRA_PERIOD = "notification_top_period";
    static final String EXTRA_REPEAT = "notification_top_repeat";
    static final String LOCAL_FLAG = "mipush_n_top_flag";
    static final String LOCAL_FREQUENCY = "mipush_n_top_fre";
    static final String LOCAL_PERIOD = "mipush_n_top_prd";
    private static final String EXTRA_MESSAGE_ID = "message_id";

    private MIPushTopNotificationSupport() {
    }

    static String generateTopNotificationUpdateJobId(int i, String str) {
        return ScheduledJobConstants.TOP_NOTIFICATION_UPDATE_JOB_ID + i + "_" + str;
    }

    static ScheduledJobManager.Job buildTopNotificationUpdateJob(final Context context, final String str, final int i, final String str2, final Notification notification) {
        return new ScheduledJobManager.Job() { // from class: com.xiaomi.push.service.MIPushTopNotificationSupport.1
            @Override
            public String getJobId() {
                return MIPushTopNotificationSupport.generateTopNotificationUpdateJobId(i, str2);
            }

            @Override
            public void run() {
                MIPushTopNotificationSupport.scheduleTopNotificationUpdate(context, str, i, str2, notification);
            }
        };
    }

    static int getNotificationPeriod(Map<String, String> map) {
        int i = 0;
        if (map != null) {
            String str = map.get(EXTRA_PERIOD);
            if (!TextUtils.isEmpty(str)) {
                try {
                    MyLog.v("prd of top notification is " + str);
                    i = Integer.parseInt(str);
                } catch (Exception e) {
                    MyLog.e("parsing top notification period error: " + e);
                }
            }
        }
        return Math.max(0, i);
    }

    static int getNotificationUpdateFrequency(Map<String, String> map) {
        int i = 0;
        if (map != null) {
            String str = map.get(EXTRA_FREQUENCY);
            if (!TextUtils.isEmpty(str)) {
                try {
                    MyLog.v("fre of top notification is " + str);
                    i = Integer.parseInt(str);
                } catch (Exception e) {
                    MyLog.e("parsing top notification frequency error: " + e);
                }
            }
        }
        return Math.max(0, i);
    }

    static boolean shouldRepeatSetTopAndToCommon(Map<String, String> map) {
        boolean z = true;
        if (map != null) {
            String str = map.get(EXTRA_REPEAT);
            if (!TextUtils.isEmpty(str)) {
                z = Boolean.parseBoolean(str);
                MyLog.v("top notification' repeat is " + z);
            }
        }
        return z;
    }

    static void onNotificationRemoved(Context context, StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null || Build.VERSION.SDK_INT < 23 || !NotificationUtils.isNotificationFromXmsf(context, statusBarNotification)) {
            return;
        }
        NotificationGroupHelper.getInstance().onNotificationRemoved(context, statusBarNotification);
        Notification notification = statusBarNotification.getNotification();
        if (notification != null) {
            String string = notification.extras.getString(EXTRA_MESSAGE_ID);
            int id = statusBarNotification.getId();
            if (!notification.extras.getBoolean(LOCAL_FLAG, false) || TextUtils.isEmpty(string)) {
                return;
            }
            ScheduledJobManager.getInstance(context).cancelJob(generateTopNotificationUpdateJobId(id, string));
        }
    }

    static void scheduleTopNotificationUpdate(Context context, String str, int i, String str2, Notification notification) {
        boolean z;
        if (context == null || TextUtils.isEmpty(str) || TextUtils.isEmpty(str2) || NotificationManagerHelper.from(context, str) == null || Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, str);
        if (notification == null) {
            List<StatusBarNotification> activeNotifications = notificationManagerHelperFrom.getActiveNotifications();
            if (activeNotifications == null) {
                return;
            }
            Iterator<StatusBarNotification> it = activeNotifications.iterator();
            while (true) {
                notification = null;
                z = false;
                if (!it.hasNext()) {
                    break;
                }
                StatusBarNotification next = it.next();
                notification = next.getNotification();
                String string = notification.extras.getString(EXTRA_MESSAGE_ID);
                if (i == next.getId() && str2.equals(string)) {
                    break;
                }
            }
        } else {
            z = true;
            if (!str2.equals(notification.extras.getString(EXTRA_MESSAGE_ID))) {
                return;
            }
        }
        if (notification == null) {
            return;
        }
        if (notification.getGroupAlertBehavior() != 1) {
            JavaCalls.setField(notification, "mGroupAlertBehavior", 1);
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        long j = notification.extras.getLong(MIPushNotificationHelper.NOTIFICATION_LOCAL_EXTRA_CREATE_TIME_LONG, 0L);
        int i2 = notification.extras.getInt(LOCAL_FREQUENCY, 0);
        int i3 = notification.extras.getInt(LOCAL_PERIOD, 0);
        if (i3 <= 0 || i3 < i2) {
            return;
        }
        long j2 = (i3 * 1000L) + j;
        if (j >= jCurrentTimeMillis || jCurrentTimeMillis >= j2) {
            i3 = 0;
        } else if (i2 > 0) {
            int iMin = (int) Math.min((j2 - jCurrentTimeMillis) / 1000, i2);
            i3 = iMin;
            if (iMin > 0 && !z) {
                notification.when = jCurrentTimeMillis;
                MyLog.w("update top notification: " + str2);
                notificationManagerHelperFrom.notify(i, notification);
            }
        }
        if (i3 > 0) {
            MyLog.w("schedule top notification next update delay: " + i3);
            ScheduledJobManager.getInstance(context).cancelJob(generateTopNotificationUpdateJobId(i, str2));
            ScheduledJobManager.getInstance(context).addOneShootJob(buildTopNotificationUpdateJob(context, str, i, str2, null), i3);
            return;
        }
        String mipushChannelId = notificationManagerHelperFrom.getMipushChannelId(NotificationManagerHelper.DEFAULT_ID);
        if (notificationManagerHelperFrom.getNotificationChannel(mipushChannelId) == null) {
            notificationManagerHelperFrom.createNotificationChannel(new NotificationChannel(mipushChannelId, AppInfoUtils.getAppLabel(context, str), 3));
        }
        Notification.Builder builderRecoverBuilder = Notification.Builder.recoverBuilder(context, notification);
        builderRecoverBuilder.setChannelId(mipushChannelId);
        JavaCalls.callMethod(builderRecoverBuilder, "setPriority", Integer.valueOf(0));
        MyLog.w("update top notification to common: " + str2);
        notificationManagerHelperFrom.notify(i, builderRecoverBuilder.build());
    }
}
