package com.xiaomi.xmsf.push.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationManagerCompat;

class NormalNotificationManager implements INotificationManager {

    private final Context context;
    private final NotificationManagerCompat manager;

    public NormalNotificationManager(Context context) {
        this.context = context;
        manager = NotificationManagerCompat.from(context);
    }

    @Override
    public void createNotificationChannelGroup(@NonNull NotificationChannelGroupCompat group) {
        manager.createNotificationChannelGroup(group);
    }

    @Override
    public void deleteNotificationChannelGroup(@NonNull String groupId) {
        manager.deleteNotificationChannelGroup(groupId);
    }

    @Override
    public NotificationChannelCompat getNotificationChannelCompat(@NonNull String channelId) {
        return manager.getNotificationChannelCompat(channelId);
    }

    @Override
    public void createNotificationChannel(@NonNull NotificationChannelCompat channel) {
        manager.createNotificationChannel(channel);
    }

    @Override
    public void deleteNotificationChannel(@NonNull String channelId) {
        manager.deleteNotificationChannel(channelId);
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
