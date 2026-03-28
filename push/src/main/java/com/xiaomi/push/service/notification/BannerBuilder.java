package com.xiaomi.push.service.notification;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.MIPushNotificationHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/notification/BannerBuilder.class */
public class BannerBuilder extends CustomNotificationBuilder {
    private static final int BANNER_IMAGE_MAX_HEIGHT = 1678;
    private static final int BANNER_IMAGE_MIN_HEIGHT = 184;
    private static final int BANNER_IMAGE_WIDTH = 984;
    private static final String BANNER_NOTIFICATION_BACKGROUND_NAME = "bg";
    private static final String BANNER_NOTIFICATION_ICON_NAME = "icon";
    private static final String BANNER_NOTIFICATION_LAYOUT_NAME = "notification_banner";
    private static final String BANNER_NOTIFICATION_TITLE_NAME = "title";
    private Bitmap mBannerBitmap;
    private int mImageTextColor;

    public BannerBuilder(Context context, String str) {
        super(context, str);
        this.mImageTextColor = 16777216;
    }

    @Override // com.xiaomi.push.service.notification.CustomNotificationBuilder, com.xiaomi.push.service.notification.BuilderCompat
    public void applyCustomizations() {
        if (!isSupportCustom() || this.mBannerBitmap == null) {
            buildStandardNotification();
            return;
        }
        super.applyCustomizations();
        Resources resources = getContext().getResources();
        String packageName = getContext().getPackageName();
        int resourceIdentifier = getResourceIdentifier(resources, BANNER_NOTIFICATION_BACKGROUND_NAME, "id", packageName);
        if (MIUIUtils.getMiuiVersionCode(getContext()) >= 10) {
            getRemoteViews().setImageViewBitmap(resourceIdentifier, bitmapWithRound(this.mBannerBitmap, 30.0f));
        } else {
            getRemoteViews().setImageViewBitmap(resourceIdentifier, this.mBannerBitmap);
        }
        showDefaultAppIcon(getResourceIdentifier(resources, BANNER_NOTIFICATION_ICON_NAME, "id", packageName));
        int resourceIdentifier2 = getResourceIdentifier(resources, "title", "id", packageName);
        getRemoteViews().setTextViewText(resourceIdentifier2, this.mTitle);
        if (this.mPushExtra != null && this.mImageTextColor == 16777216) {
            setImageTextColor(this.mPushExtra.get(MIPushNotificationHelper.NOTIFICATION_IMAGE_TEXT_COLOR));
        }
        RemoteViews remoteViews = getRemoteViews();
        int i = this.mImageTextColor;
        remoteViews.setTextColor(resourceIdentifier2, (i == 16777216 || !isDarkColor(i)) ? -1 : -16777216);
        setCustomContentView(getRemoteViews());
        Bundle bundle = new Bundle();
        bundle.putBoolean("miui.customHeight", true);
        addExtras(bundle);
    }

    @Override // com.xiaomi.push.service.notification.CustomNotificationBuilder
    protected boolean checkSupportCustom() {
        boolean z = false;
        if (!MIUIUtils.isXMSF(getContext())) {
            return false;
        }
        Resources resources = getContext().getResources();
        String packageName = getContext().getPackageName();
        int resourceIdentifier = getResourceIdentifier(getContext().getResources(), BANNER_NOTIFICATION_BACKGROUND_NAME, "id", getContext().getPackageName());
        int resourceIdentifier2 = getResourceIdentifier(resources, BANNER_NOTIFICATION_ICON_NAME, "id", packageName);
        int resourceIdentifier3 = getResourceIdentifier(resources, "title", "id", packageName);
        if (resourceIdentifier != 0 && resourceIdentifier2 != 0 && resourceIdentifier3 != 0 && MIUIUtils.getMiuiVersionCode(getContext()) >= 9) {
            z = true;
        }
        return z;
    }

    @Override // com.xiaomi.push.service.notification.CustomNotificationBuilder
    protected String getCopyLayout() {
        return null;
    }

    @Override // com.xiaomi.push.service.notification.CustomNotificationBuilder
    protected String getOriginalLayout() {
        return BANNER_NOTIFICATION_LAYOUT_NAME;
    }

    public BannerBuilder setBanner(Bitmap bitmap) {
        if (isSupportCustom() && bitmap != null) {
            if (bitmap.getWidth() != BANNER_IMAGE_WIDTH || BANNER_IMAGE_MIN_HEIGHT > bitmap.getHeight() || bitmap.getHeight() > BANNER_IMAGE_MAX_HEIGHT) {
                MyLog.w("colorful notification banner image resolution error, must belong to [984*184, 984*1678]");
            } else {
                this.mBannerBitmap = bitmap;
            }
        }
        return this;
    }

    public BannerBuilder setImageTextColor(String str) {
        if (isSupportCustom() && !TextUtils.isEmpty(str)) {
            try {
                this.mImageTextColor = Color.parseColor(str);
            } catch (Exception e) {
                MyLog.w("parse banner notification image text color error");
            }
        }
        return this;
    }

    @Override // com.xiaomi.push.service.notification.CustomNotificationBuilder, android.app.Notification.Builder
    public CustomNotificationBuilder setLargeIcon(Bitmap bitmap) {
        return this;
    }
}
