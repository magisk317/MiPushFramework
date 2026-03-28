package com.xiaomi.push.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

public class NotificationManagerHelper {
    public static final String DEFAULT_ID = "default";
    private static final String NEW_FORMAT_PREFIX = "mipush|%s|%s";
    private static final String OLD_FORMAT_PREFIX = "mipush_%s_%s";
    private static final String TAG = "NMHelper";
    private static final WeakHashMap<Integer, NotificationManagerHelper> CACHE_INSTANCE = new WeakHashMap<>();
    private final String targetPkg;

    private NotificationManagerHelper(String str) {
        this.targetPkg = str;
    }

    public static NotificationManagerHelper from(Context context, String str) {
        NotificationManagerPlatformSupport.init(context);
        int hashCode = str.hashCode();
        NotificationManagerHelper notificationManagerHelper = CACHE_INSTANCE.get(Integer.valueOf(hashCode));
        if (notificationManagerHelper == null) {
            notificationManagerHelper = new NotificationManagerHelper(str);
            CACHE_INSTANCE.put(Integer.valueOf(hashCode), notificationManagerHelper);
        }
        return notificationManagerHelper;
    }

    static void outLog(String str) {
        MyLog.w("NMHelper:" + str);
    }

    public static boolean isRomSupportNotificationBelongToApp(Context context) {
        return NotificationManagerPlatformSupport.isRomSupportNotificationBelongToApp(context);
    }

    private static boolean isSupportFwk() {
        return NotificationManagerPlatformSupport.isSupportFwk();
    }

    private static NotificationManager getNm() {
        return NotificationManagerPlatformSupport.getNm();
    }

    void cancel(int i) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.cancel(this.targetPkg, i);
                outLog("cancel succ:" + i);
            } else {
                getNm().cancel(i);
            }
        } catch (Exception e) {
            outLog("cancel error" + e);
        }
    }

    void createNotificationChannel(NotificationChannel notificationChannel) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.createNotificationChannel(this.targetPkg, notificationChannel);
            } else {
                getNm().createNotificationChannel(notificationChannel);
            }
        } catch (Exception e) {
            outLog("createNotificationChannel error" + e);
        }
    }

    void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.createNotificationChannelGroup(this.targetPkg, notificationChannelGroup);
            } else {
                getNm().createNotificationChannelGroup(notificationChannelGroup);
            }
        } catch (Exception e) {
            outLog("createNotificationChannelGroup error" + e);
        }
    }

    void deleteNotificationChannel(String str) {
        getNm().deleteNotificationChannel(str);
    }

    void deleteNotificationChannelGroup(String str) {
        getNm().deleteNotificationChannelGroup(str);
    }

    public List<StatusBarNotification> getActiveNotifications() {
        try {
            if (isSupportFwk()) {
                return NotificationManagerPlatformSupport.getActiveNotifications(this.targetPkg);
            }
            return NotificationManagerPlatformSupport.filterLocalActiveNotifications(this.targetPkg, getNm().getActiveNotifications());
        } catch (Exception e) {
            outLog("getActiveNotifications error " + e);
            return null;
        }
    }

    String getGroupSummaryChannelId(String str, String str2) {
        return isSupportFwk() ? str : str2;
    }

    String getMipushChannelId(String str) {
        return String.format(isSupportFwk() ? NEW_FORMAT_PREFIX : OLD_FORMAT_PREFIX, this.targetPkg, str);
    }

    public NotificationChannel getNotificationChannel(String str) {
        try {
            if (isSupportFwk()) {
                List<NotificationChannel> notificationChannels = getNotificationChannels();
                if (notificationChannels != null) {
                    Iterator<NotificationChannel> it = notificationChannels.iterator();
                    while (it.hasNext()) {
                        NotificationChannel next = it.next();
                        if (str.equals(next.getId())) {
                            return next;
                        }
                    }
                }
                return null;
            }
            return getNm().getNotificationChannel(str);
        } catch (Exception e) {
            outLog("getNotificationChannel error" + e);
            return null;
        }
    }

    NotificationChannelGroup getNotificationChannelGroup(String str) {
        try {
            if (!isSupportFwk()) {
                if (Build.VERSION.SDK_INT < 28) {
                    Iterator<NotificationChannelGroup> it = getNm().getNotificationChannelGroups().iterator();
                    while (it.hasNext()) {
                        NotificationChannelGroup next = it.next();
                        if (str.equals(next.getId())) {
                            return next;
                        }
                    }
                    return null;
                }
                return getNm().getNotificationChannelGroup(str);
            }
            return NotificationManagerPlatformSupport.getNotificationChannelGroup(str, this.targetPkg);
        } catch (Exception e) {
            outLog("getNotificationChannel error" + e);
            return null;
        }
    }

    List<NotificationChannelGroup> getNotificationChannelGroups() {
        try {
            return isSupportFwk() ? null : getNm().getNotificationChannelGroups();
        } catch (Exception e) {
            outLog("getNotificationChannelGroups error " + e);
            return null;
        }
    }

    List<NotificationChannel> getNotificationChannels() {
        try {
            List<NotificationChannel> list;
            String str;
            if (isSupportFwk()) {
                list = NotificationManagerPlatformSupport.getNotificationChannels(this.targetPkg);
                str = NEW_FORMAT_PREFIX;
            } else {
                list = getNm().getNotificationChannels();
                str = OLD_FORMAT_PREFIX;
            }
            return NotificationManagerPlatformSupport.filterMipushChannels(this.targetPkg, str, list);
        } catch (Exception e) {
            outLog("getNotificationChannels error" + e);
            return null;
        }
    }

    public void notify(int i, Notification notification) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.notify(this.targetPkg, i, notification);
            } else {
                getNm().notify(i, notification);
            }
        } catch (Exception e) {
        }
    }

    public String toString() {
        return "NotificationManagerHelper{" + this.targetPkg + "}";
    }
}
