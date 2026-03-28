package com.xiaomi.push.service.notification;

import android.app.Notification;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RemoteViews;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/notification/BuilderCompat.class */
public class BuilderCompat extends Notification.Builder {
    private Context mContext;

    public BuilderCompat(Context context) {
        super(context);
        this.mContext = context;
    }

    @Override // android.app.Notification.Builder
    public BuilderCompat addExtras(Bundle bundle) {
        if (Build.VERSION.SDK_INT >= 20) {
            super.addExtras(bundle);
        }
        return this;
    }

    protected void applyCustomizations() {
    }

    @Override // android.app.Notification.Builder
    public Notification build() {
        applyCustomizations();
        return super.build();
    }

    protected Context getContext() {
        return this.mContext;
    }

    public int getResourceIdentifier(Resources resources, String str, String str2, String str3) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        return resources.getIdentifier(str, str2, str3);
    }

    @Override // android.app.Notification.Builder
    public BuilderCompat setCustomContentView(RemoteViews remoteViews) {
        if (Build.VERSION.SDK_INT >= 24) {
            super.setCustomContentView(remoteViews);
        } else {
            super.setContent(remoteViews);
        }
        return this;
    }
}
