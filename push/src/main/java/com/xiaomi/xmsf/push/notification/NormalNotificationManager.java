package com.xiaomi.xmsf.push.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

class NormalNotificationManager implements INotificationManager {

    private final Context context;
    private final NotificationManagerCompat manager;

    public NormalNotificationManager(Context context) {
        this.context = context;
        manager = NotificationManagerCompat.from(context);
    }

    @Override
    public void createNotificationChannelGroup(@NonNull NotificationChannelGroup group) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannelGroup(group);
        }
    }

    @Override
    public void deleteNotificationChannelGroup(@NonNull String groupId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannelGroup(groupId);
        }
    }

    @Override
    public NotificationChannel getNotificationChannel(@NonNull String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return manager.getNotificationChannel(channelId);
        }
        return null;
    }

    @Override
    public void createNotificationChannel(@NonNull NotificationChannel channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void deleteNotificationChannel(@NonNull String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel(channelId);
        }
    }

    @Override
    public void notify(int id, @NonNull Notification notification) {
        manager.notify(id, notification);
    }

    @Override
    public void cancel(int id) {
        manager.cancel(id);
    }

    @Override
    public StatusBarNotification[] getActiveNotifications() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return manager.getActiveNotifications();
        } else {
            return new StatusBarNotification[0];
        }
    }
}
