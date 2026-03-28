package com.xiaomi.push.service.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.NotificationManagerHelper;
import com.xiaomi.push.service.NotificationUtils;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/notification/CustomNotificationBuilder.class */
public abstract class CustomNotificationBuilder extends BuilderCompat {
    private static final String CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN = "mipush.customCopyLayout";
    protected static final int INVALID_COLOR = 16777216;
    protected CharSequence mContent;
    protected Bitmap mIconBitmap;
    private boolean mIsSupportCustom;
    private int mNotifyId;
    protected Map<String, String> mPushExtra;
    private RemoteViews mRemoteViews;
    private String mTargetPackageName;
    protected CharSequence mTitle;
    private boolean mUseCopyLayout;

    public CustomNotificationBuilder(Context context, int i, String str) {
        super(context);
        this.mTargetPackageName = str;
        this.mNotifyId = i;
        createRemoteViews();
    }

    public CustomNotificationBuilder(Context context, String str) {
        super(context);
        this.mTargetPackageName = str;
        createRemoteViews();
    }

    private boolean builderSetTitle() {
        Map<String, String> map = this.mPushExtra;
        return map != null && Boolean.parseBoolean(map.get(MIPushNotificationHelper.NOTIFICATION_CUSTOM_BUILDER_SET_TITLE));
    }

    private void createRemoteViews() {
        int resourceIdentifier = getResourceIdentifier(getContext().getResources(), getLayoutName(), "layout", getContext().getPackageName());
        if (resourceIdentifier == 0) {
            MyLog.w("create RemoteViews failed, no such layout resource was found");
        } else {
            this.mRemoteViews = new RemoteViews(getContext().getPackageName(), resourceIdentifier);
            this.mIsSupportCustom = checkSupportCustom();
        }
    }

    private Bitmap getAppIconBitmap() {
        return MIPushNotificationHelper.drawableToBitmap(AppInfoUtils.getAppIconDrawable(getContext(), this.mTargetPackageName));
    }

    private String getLayoutName() {
        boolean zUseCopyLayout = useCopyLayout();
        this.mUseCopyLayout = zUseCopyLayout;
        return zUseCopyLayout ? getCopyLayout() : getOriginalLayout();
    }

    private boolean reapplyDisallowed() {
        return (TextUtils.isEmpty(getCopyLayout()) || TextUtils.isEmpty(this.mTargetPackageName)) ? false : true;
    }

    private void setContentTitleAndText() {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentTitle(this.mTitle);
            super.setContentText(this.mContent);
        }
    }

    private boolean shouldUseCopyLayout() {
        List<StatusBarNotification> activeNotifications;
        if (Build.VERSION.SDK_INT < 20 || (activeNotifications = NotificationManagerHelper.from(getContext(), this.mTargetPackageName).getActiveNotifications()) == null || activeNotifications.isEmpty()) {
            return false;
        }
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (statusBarNotification.getId() == this.mNotifyId) {
                if (statusBarNotification.getNotification() == null) {
                    return false;
                }
                return !statusBarNotification.getNotification().extras.getBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, true);
            }
        }
        return false;
    }

    private boolean useCopyLayout() {
        return reapplyDisallowed() && shouldUseCopyLayout();
    }

    @Override // android.app.Notification.Builder
    public CustomNotificationBuilder addAction(int i, CharSequence charSequence, PendingIntent pendingIntent) {
        return this;
    }

    @Override // android.app.Notification.Builder
    public CustomNotificationBuilder addAction(Notification.Action action) {
        return this;
    }

    @Override // com.xiaomi.push.service.notification.BuilderCompat
    protected void applyCustomizations() {
        super.applyCustomizations();
        Bundle bundle = new Bundle();
        if (reapplyDisallowed()) {
            bundle.putBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, this.mUseCopyLayout);
        } else {
            bundle.putBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, false);
        }
        bundle.putBoolean("miui.customHeight", false);
        addExtras(bundle);
        if (builderSetTitle() || !NotificationUtils.isUserAggregate(getContext().getContentResolver())) {
            setContentTitleAndText();
        }
    }

    protected Bitmap bitmapWithRound(Bitmap bitmap, float f) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRoundRect(new RectF(rect), f, f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return bitmapCreateBitmap;
    }

    protected final void buildStandardNotification() {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentTitle(this.mTitle);
            super.setContentText(this.mContent);
            Bitmap bitmap = this.mIconBitmap;
            if (bitmap != null) {
                super.setLargeIcon(bitmap);
            }
        }
    }

    protected abstract boolean checkSupportCustom();

    protected int dp2px(float f) {
        return (int) ((f * getContext().getResources().getDisplayMetrics().density) + 0.5f);
    }

    protected abstract String getCopyLayout();

    protected abstract String getOriginalLayout();

    protected final RemoteViews getRemoteViews() {
        return this.mRemoteViews;
    }

    protected final String getTargetPackageName() {
        return this.mTargetPackageName;
    }

    protected final boolean isDarkColor(int i) {
        return ((((double) Color.red(i)) * 0.299d) + (((double) Color.green(i)) * 0.587d)) + (((double) Color.blue(i)) * 0.114d) < 192.0d;
    }

    protected final boolean isSupportCustom() {
        return this.mIsSupportCustom;
    }

    @Override // android.app.Notification.Builder
    public CustomNotificationBuilder setContentText(CharSequence charSequence) {
        this.mContent = charSequence;
        return this;
    }

    @Override // android.app.Notification.Builder
    public CustomNotificationBuilder setContentTitle(CharSequence charSequence) {
        this.mTitle = charSequence;
        return this;
    }

    @Override // android.app.Notification.Builder
    public CustomNotificationBuilder setLargeIcon(Bitmap bitmap) {
        this.mIconBitmap = bitmap;
        return this;
    }

    public CustomNotificationBuilder setPushExtra(Map<String, String> map) {
        this.mPushExtra = map;
        return this;
    }

    protected void showDefaultAppIcon(int i) {
        Bitmap appIconBitmap = getAppIconBitmap();
        if (appIconBitmap != null) {
            getRemoteViews().setImageViewBitmap(i, appIconBitmap);
            return;
        }
        int appIconId = AppInfoUtils.getAppIconId(getContext(), this.mTargetPackageName);
        if (appIconId != 0) {
            getRemoteViews().setImageViewResource(i, appIconId);
        }
    }
}
