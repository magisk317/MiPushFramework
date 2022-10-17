package com.xiaomi.xmsf.push.notification;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;

public interface INotificationManager {
    void createNotificationChannelGroup(@NonNull NotificationChannelGroupCompat group);
    void deleteNotificationChannelGroup(@NonNull String groupId);
    NotificationChannelCompat getNotificationChannelCompat(@NonNull String channelId);
    void createNotificationChannel(@NonNull NotificationChannelCompat channel);
    void deleteNotificationChannel(@NonNull String channelId);
    void notify(int id, @NonNull Notification notification);
    void cancel(int id);
    StatusBarNotification[] getActiveNotifications();
}

