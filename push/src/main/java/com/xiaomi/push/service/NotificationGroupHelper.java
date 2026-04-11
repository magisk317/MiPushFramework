package com.xiaomi.push.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.notification.BuilderCompat;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationGroupHelper.class */
class NotificationGroupHelper {
    private static final String EXTRA_SRC_GROUP_NAME = "push_src_group_name";
    private static final String EXTRA_SRC_GROUP_TIME = "push_src_group_time";
    private static final String GROUP_MASK_FORMAT = "pushmask_%s_%s";
    private static final int GROUP_MIN_SIZE = 2;
    private static final String GROUP_SUMMARY_CHANNEL_ID = "groupSummary";
    private static final String GROUP_SUMMARY_TITLE = "GroupSummary";
    private static NotificationGroupHelper sInstance = new NotificationGroupHelper();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationGroupHelper$AutoGroupItem.class */
    private class AutoGroupItem {
        List<NotificationInfo> childList;
        List<NotificationInfo> summaryList;

        private AutoGroupItem() {
            this.childList = new ArrayList<>();
            this.summaryList = new ArrayList<>();
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationGroupHelper$NotificationInfo.class */
    private class NotificationInfo {
        Notification notification;
        int notifyId;

        public NotificationInfo(int i, Notification notification) {
            this.notifyId = i;
            this.notification = notification;
        }

        public String toString() {
            return "id:" + this.notifyId;
        }
    }

    private NotificationGroupHelper() {
    }

    private void cancelGroupSummary(Context context, String str, String str2) {
        MyLog.i("group cancel summary:" + str2);
        NotificationManagerHelper.from(context, str).cancel(getGroupSummaryNotifyId(str, str2));
    }

    private List<StatusBarNotification> getActiveNotifications(NotificationManagerHelper notificationManagerHelper) {
        List<StatusBarNotification> activeNotifications = null;
        if (notificationManagerHelper != null) {
            activeNotifications = notificationManagerHelper.getActiveNotifications();
        }
        if (activeNotifications == null || activeNotifications.size() == 0) {
            return null;
        }
        return activeNotifications;
    }

    private int getGroupSummaryNotifyId(String str, String str2) {
        return (GROUP_SUMMARY_TITLE + str + str2).hashCode();
    }

    public static NotificationGroupHelper getInstance() {
        return sInstance;
    }

    private String getSrcGroup(Notification notification) {
        if (notification == null || notification.extras == null) {
            return null;
        }
        return notification.extras.getString(EXTRA_SRC_GROUP_NAME);
    }

    private String getTrueGroup(Notification notification) {
        if (notification == null) {
            return null;
        }
        String group = notification.getGroup();
        if (isMaskGroup(notification)) {
            group = getSrcGroup(notification);
        }
        return group;
    }

    private void handleAutoGroup(Context context, int i, Notification notification, boolean z) {
        String targetPackage = NotificationUtils.getTargetPackage(notification);
        if (TextUtils.isEmpty(targetPackage)) {
            MyLog.w("group auto not extract pkg from notification:" + i);
            return;
        }
        List<StatusBarNotification> activeNotifications = getActiveNotifications(NotificationManagerHelper.from(context, targetPackage));
        if (activeNotifications == null) {
            MyLog.w("group auto not get notifications");
            return;
        }
        String trueGroup = getTrueGroup(notification);
        Map<String, AutoGroupItem> map = new HashMap<>();
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (statusBarNotification.getNotification() != null && statusBarNotification.getId() != i) {
                putAutoGroupItem(map, statusBarNotification);
            }
        }
        for (Map.Entry<String, AutoGroupItem> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!TextUtils.isEmpty(key)) {
                AutoGroupItem value = entry.getValue();
                if (z && key.equals(trueGroup) && !isMaskGroup(notification)) {
                    NotificationInfo notificationInfo = new NotificationInfo(i, notification);
                    if (isGroupSummary(notification)) {
                        value.summaryList.add(notificationInfo);
                    } else {
                        value.childList.add(notificationInfo);
                    }
                }
                int size = value.childList.size();
                if (value.summaryList.size() <= 0) {
                    if (z && size >= 2) {
                        showGroupSummary(context, targetPackage, key, value.childList.get(0).notification);
                    }
                } else if (size <= 0) {
                    cancelGroupSummary(context, targetPackage, key);
                }
            }
        }
    }

    private void handleRestoreGroup(Context context, int i, Notification notification) {
        String targetPackage = NotificationUtils.getTargetPackage(notification);
        if (TextUtils.isEmpty(targetPackage)) {
            MyLog.w("group restore not extract pkg from notification:" + i);
            return;
        }
        NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, targetPackage);
        List<StatusBarNotification> activeNotifications = getActiveNotifications(notificationManagerHelperFrom);
        if (activeNotifications == null) {
            MyLog.w("group restore not get notifications");
            return;
        }
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            Notification notification2 = statusBarNotification.getNotification();
            if (notification2 != null && isMaskGroup(notification2) && statusBarNotification.getId() != i) {
                Notification.Builder builderRecoverBuilder = Notification.Builder.recoverBuilder(context, statusBarNotification.getNotification());
                builderRecoverBuilder.setGroup(getSrcGroup(notification2));
                suppressNotificationEffects(builderRecoverBuilder, isGroupSummary(notification2));
                notificationManagerHelperFrom.notify(statusBarNotification.getId(), builderRecoverBuilder.build());
                MyLog.i("group restore notification:" + statusBarNotification.getId());
            }
        }
    }

    private boolean isEnableLatestNotificationNotIntoGroup(Context context) {
        if (isEnableNotificationAutoGroup(context) && NotificationManagerHelper.isRomSupportNotificationBelongToApp(context)) {
            return OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.LatestNotificationNotIntoGroupSwitch.getValue(), false);
        }
        return false;
    }

    private boolean isEnableNotificationAutoGroup(Context context) {
        return OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.NotificationAutoGroupSwitch.getValue(), true);
    }

    private boolean isGroupSummary(Notification notification) {
        if (notification == null) {
            return false;
        }
        Object objCallMethod = JavaCalls.callMethod(notification, "isGroupSummary", (Object[]) null);
        if (objCallMethod instanceof Boolean) {
            return ((Boolean) objCallMethod).booleanValue();
        }
        return false;
    }

    private boolean isMaskGroup(Notification notification) {
        if (notification == null || notification.getGroup() == null || notification.extras == null) {
            return false;
        }
        long j = notification.extras.getLong(EXTRA_SRC_GROUP_TIME);
        return notification.getGroup().equals(String.format(GROUP_MASK_FORMAT, Long.valueOf(j), getSrcGroup(notification)));
    }

    private boolean isSupportPlatform() {
        return Build.VERSION.SDK_INT >= 24;
    }

    private void putAutoGroupItem(Map<String, AutoGroupItem> map, StatusBarNotification statusBarNotification) {
        String trueGroup = getTrueGroup(statusBarNotification.getNotification());
        AutoGroupItem autoGroupItem = map.get(trueGroup);
        AutoGroupItem autoGroupItem2 = autoGroupItem;
        if (autoGroupItem == null) {
            autoGroupItem2 = new AutoGroupItem();
            map.put(trueGroup, autoGroupItem2);
        }
        NotificationInfo notificationInfo = new NotificationInfo(statusBarNotification.getId(), statusBarNotification.getNotification());
        if (isGroupSummary(statusBarNotification.getNotification())) {
            autoGroupItem2.summaryList.add(notificationInfo);
        } else {
            autoGroupItem2.childList.add(notificationInfo);
        }
    }

    private void showGroupSummary(Context context, String str, String str2, Notification notification) {
        BuilderCompat defaults = new BuilderCompat(context);
        try {
            if (TextUtils.isEmpty(str2)) {
                MyLog.w("group show summary group is null");
                return;
            }
            int idForSmallIconFromTargetPkg = NotificationUtils.getIdForSmallIconFromTargetPkg(context, str);
            if (idForSmallIconFromTargetPkg == 0) {
                MyLog.w("group show summary not get icon from " + str);
                return;
            }
            NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, str);
            if (Build.VERSION.SDK_INT >= 26) {
                String groupSummaryChannelId = notificationManagerHelperFrom.getGroupSummaryChannelId(notification.getChannelId(), GROUP_SUMMARY_CHANNEL_ID);
                NotificationChannel notificationChannel = notificationManagerHelperFrom.getNotificationChannel(groupSummaryChannelId);
                if (GROUP_SUMMARY_CHANNEL_ID.equals(groupSummaryChannelId) && notificationChannel == null) {
                    notificationManagerHelperFrom.createNotificationChannel(new NotificationChannel(groupSummaryChannelId, "group_summary", 3));
                }
                defaults.setChannelId(groupSummaryChannelId);
            } else {
                defaults.setPriority(0).setDefaults(-1);
            }
            suppressNotificationEffects(defaults, true);
            Notification notificationBuild = defaults.setContentTitle(GROUP_SUMMARY_TITLE).setContentText(GROUP_SUMMARY_TITLE).setSmallIcon(Icon.createWithResource(str, idForSmallIconFromTargetPkg)).setAutoCancel(true).setGroup(str2).setGroupSummary(true).build();
            if (!MIUIUtils.isXMS() && PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.getPackageName())) {
                NotificationUtils.setTargetPackage(notificationBuild, str);
            }
            if (!MIUIUtils.isGlobalRegion()) {
                notificationBuild.extras.putBoolean("miui.showAtTail", true);
            }
            int groupSummaryNotifyId = getGroupSummaryNotifyId(str, str2);
            notificationManagerHelperFrom.notify(groupSummaryNotifyId, notificationBuild);
            MyLog.i("group show summary notify:" + groupSummaryNotifyId);
        } catch (Exception e) {
            MyLog.w("group show summary error " + e);
        }
    }

    private boolean suppressNotificationEffects(BuilderCompat builder, boolean z) {
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setGroupAlertBehavior(z ? 2 : 1);
            return true;
        }
        MyLog.i("not support setGroupAlertBehavior");
        return false;
    }

    private boolean suppressNotificationEffects(Notification.Builder builder, boolean z) {
        if (Build.VERSION.SDK_INT >= 26) {
            JavaCalls.callMethod(builder, "setGroupAlertBehavior", Integer.valueOf(z ? 2 : 1));
            return true;
        }
        MyLog.i("not support setGroupAlertBehavior");
        return false;
    }

    public String maskGroup(Context context, BuilderCompat builder, String str) {
        if (!isSupportPlatform() || !isEnableLatestNotificationNotIntoGroup(context)) {
            return str;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        Bundle extras = builder.getExtras();
        extras.putString(EXTRA_SRC_GROUP_NAME, str);
        extras.putLong(EXTRA_SRC_GROUP_TIME, jCurrentTimeMillis);
        return String.format(GROUP_MASK_FORMAT, Long.valueOf(jCurrentTimeMillis), str);
    }

    public void onNotificationNotify(Context context, int i, Notification notification) {
        if (isSupportPlatform()) {
            if (isEnableLatestNotificationNotIntoGroup(context)) {
                try {
                    handleRestoreGroup(context, i, notification);
                } catch (Exception e) {
                    MyLog.w("group notify handle restore error " + e);
                }
            }
            if (isEnableNotificationAutoGroup(context)) {
                try {
                    handleAutoGroup(context, i, notification, true);
                } catch (Exception e2) {
                    MyLog.w("group notify handle auto error " + e2);
                }
            }
        }
    }

    public void onNotificationRemoved(Context context, StatusBarNotification statusBarNotification) {
        if (isSupportPlatform() && isEnableNotificationAutoGroup(context) && statusBarNotification.getNotification() != null) {
            try {
                handleAutoGroup(context, statusBarNotification.getId(), statusBarNotification.getNotification(), false);
            } catch (Exception e) {
                MyLog.w("group remove handle auto error " + e);
            }
        }
    }
}
