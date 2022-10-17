package com.xiaomi.xmsf.push.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

public interface INotificationManager {
    void createNotificationChannelGroup(@NonNull NotificationChannelGroup group);
    void deleteNotificationChannelGroup(@NonNull String groupId);
    NotificationChannel getNotificationChannel(@NonNull String channelId);
    void createNotificationChannel(@NonNull NotificationChannel channel);
    void deleteNotificationChannel(@NonNull String channelId);
    void notify(int id, @NonNull Notification notification);
    void cancel(int id);
    StatusBarNotification[] getActiveNotifications();
}

