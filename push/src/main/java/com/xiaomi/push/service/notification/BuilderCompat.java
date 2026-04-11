package com.xiaomi.push.service.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/notification/BuilderCompat.class */
public class BuilderCompat {
    private final Notification.Builder builder;
    private final Context mContext;

    public BuilderCompat(Context context) {
        this.mContext = context;
        this.builder = createBuilder(context);
    }

    public BuilderCompat addExtras(Bundle bundle) {
        if (Build.VERSION.SDK_INT >= 20) {
            this.builder.addExtras(bundle);
        }
        return this;
    }

    public BuilderCompat addAction(int i, CharSequence charSequence, PendingIntent pendingIntent) {
        JavaCalls.callMethod(this.builder, "addAction", Integer.valueOf(i), charSequence, pendingIntent);
        return this;
    }

    public BuilderCompat addAction(Notification.Action action) {
        if (Build.VERSION.SDK_INT >= 20) {
            this.builder.addAction(action);
        }
        return this;
    }

    protected void applyCustomizations() {
    }

    public Notification build() {
        applyCustomizations();
        return this.builder.build();
    }

    protected Context getContext() {
        return this.mContext;
    }

    public Bundle getExtras() {
        return this.builder.getExtras();
    }

    public Notification.Builder getPlatformBuilder() {
        return this.builder;
    }

    public int getResourceIdentifier(Resources resources, String str, String str2, String str3) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        return resources.getIdentifier(str, str2, str3);
    }

    public BuilderCompat setCustomContentView(RemoteViews remoteViews) {
        if (Build.VERSION.SDK_INT >= 24) {
            this.builder.setCustomContentView(remoteViews);
        } else {
            JavaCalls.callMethod(this.builder, "setContent", remoteViews);
        }
        return this;
    }

    public BuilderCompat setContentIntent(PendingIntent pendingIntent) {
        this.builder.setContentIntent(pendingIntent);
        return this;
    }

    public BuilderCompat setContentText(CharSequence charSequence) {
        this.builder.setContentText(charSequence);
        return this;
    }

    public BuilderCompat setContentTitle(CharSequence charSequence) {
        this.builder.setContentTitle(charSequence);
        return this;
    }

    public BuilderCompat setAutoCancel(boolean z) {
        this.builder.setAutoCancel(z);
        return this;
    }

    public BuilderCompat setChannelId(String str) {
        if (Build.VERSION.SDK_INT >= 26) {
            this.builder.setChannelId(str);
        }
        return this;
    }

    public BuilderCompat setDefaults(int i) {
        JavaCalls.callMethod(this.builder, "setDefaults", Integer.valueOf(i));
        return this;
    }

    public BuilderCompat setGroup(String str) {
        this.builder.setGroup(str);
        return this;
    }

    public BuilderCompat setGroupAlertBehavior(int i) {
        if (Build.VERSION.SDK_INT >= 26) {
            this.builder.setGroupAlertBehavior(i);
        }
        return this;
    }

    public BuilderCompat setGroupSummary(boolean z) {
        this.builder.setGroupSummary(z);
        return this;
    }

    public BuilderCompat setLargeIcon(Bitmap bitmap) {
        this.builder.setLargeIcon(bitmap);
        return this;
    }

    public BuilderCompat setPriority(int i) {
        JavaCalls.callMethod(this.builder, "setPriority", Integer.valueOf(i));
        return this;
    }

    public BuilderCompat setShowWhen(boolean z) {
        this.builder.setShowWhen(z);
        return this;
    }

    public BuilderCompat setSmallIcon(int i) {
        this.builder.setSmallIcon(i);
        return this;
    }

    public BuilderCompat setSmallIcon(Icon icon) {
        if (Build.VERSION.SDK_INT >= 23) {
            this.builder.setSmallIcon(icon);
        }
        return this;
    }

    public BuilderCompat setSound(Uri uri) {
        JavaCalls.callMethod(this.builder, "setSound", uri);
        return this;
    }

    public BuilderCompat setStyle(Notification.Style style) {
        this.builder.setStyle(style);
        return this;
    }

    public BuilderCompat setTicker(CharSequence charSequence) {
        this.builder.setTicker(charSequence);
        return this;
    }

    public BuilderCompat setTimeoutAfter(long j) {
        if (Build.VERSION.SDK_INT >= 26) {
            this.builder.setTimeoutAfter(j);
        }
        return this;
    }

    public BuilderCompat setWhen(long j) {
        this.builder.setWhen(j);
        return this;
    }

    public Notification.BigPictureStyle createBigPictureStyle() {
        try {
            return Notification.BigPictureStyle.class.getConstructor(Notification.Builder.class).newInstance(this.builder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create BigPictureStyle", e);
        }
    }

    private static Notification.Builder createBuilder(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(context, "default");
        }
        try {
            return Notification.Builder.class.getDeclaredConstructor(Context.class).newInstance(context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create Notification.Builder", e);
        }
    }
}
