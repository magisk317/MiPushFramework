package com.xiaomi.push.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.android.Region;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ThreadUtils;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.xmpush.thrift.ConfigKey;

final class XMPushServiceEnvironment {
    private static final String EXTREME_POWER_MODE = "EXTREME_POWER_MODE_ENABLE";
    private static final String SUPER_POWER_MODE = "power_supersave_mode_open";

    private XMPushServiceEnvironment() {
    }

    static boolean canOpenForegroundService(XMPushService service) {
        if (TextUtils.equals(service.getPackageName(), PushConstants.PUSH_SERVICE_PACKAGE_NAME)) {
            return false;
        }
        return OnlineConfig.getInstance(service).getBooleanValue(ConfigKey.ForegroundServiceSwitch.getValue(), false);
    }

    static String ensureRegionAvailable(XMPushService service) {
        ThreadUtils.checkNotUIThread();
        String countryCode = null;
        long elapsedRealtime = SystemClock.elapsedRealtime();
        Object obj = new Object();
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(service.getPackageName())) {
            PushProvision pushProvision = PushProvision.getInstance(service);
            while (true) {
                if (!TextUtils.isEmpty(countryCode) && pushProvision.getProvisioned() != 0) {
                    break;
                }
                String str = countryCode;
                if (TextUtils.isEmpty(countryCode)) {
                    String property = MIUIUtils.getProperty("ro.miui.region");
                    str = property;
                    if (TextUtils.isEmpty(property)) {
                        str = MIUIUtils.getProperty("ro.product.locale.region");
                    }
                }
                try {
                    synchronized (obj) {
                        obj.wait(100L);
                    }
                } catch (InterruptedException unused) {
                }
                countryCode = str;
            }
        } else {
            countryCode = MIUIUtils.getCountryCode();
        }
        String name = null;
        if (!TextUtils.isEmpty(countryCode)) {
            AppRegionStorage.getInstance(service.getApplicationContext()).setCountryCode(countryCode);
            name = MIUIUtils.getRegion(countryCode).name();
        }
        MyLog.w("wait region :" + name + " cost = " + (SystemClock.elapsedRealtime() - elapsedRealtime));
        return name;
    }

    static int[] getFalldownTimeRange(XMPushService service) {
        String[] split;
        String stringValue = OnlineConfig.getInstance(service.getApplicationContext()).getStringValue(ConfigKey.FallDownTimeRange.getValue(), "");
        if (TextUtils.isEmpty(stringValue) || (split = stringValue.split(",")) == null || split.length < 2) {
            return null;
        }
        int[] iArr = new int[2];
        try {
            iArr[0] = Integer.valueOf(split[0]).intValue();
            iArr[1] = Integer.valueOf(split[1]).intValue();
            if (iArr[0] < 0 || iArr[0] > 23 || iArr[1] < 0 || iArr[1] > 23 || iArr[0] == iArr[1]) {
                return null;
            }
            return iArr;
        } catch (NumberFormatException e) {
            MyLog.e("parse falldown time range failure: " + e);
            return null;
        }
    }

    static Notification getPushServiceNotification(Context context) {
        Intent intent = new Intent(context, (Class<?>) XMPushService.class);
        if (Build.VERSION.SDK_INT >= 11) {
            Notification.Builder builder = new Notification.Builder(context);
            builder.setSmallIcon(context.getApplicationInfo().icon);
            builder.setContentTitle("Push Service");
            builder.setContentText("Push Service");
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
            return builder.getNotification();
        }
        Notification notification = new Notification();
        try {
            notification.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class).invoke(notification, context, "Push Service", "Push Service", PendingIntent.getService(context, 0, intent, 0));
        } catch (Exception e) {
            MyLog.e(e);
        }
        return notification;
    }

    static boolean isExtremePowerSaveMode(XMPushService service) {
        if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(service.getPackageName())) {
            return false;
        }
        return Settings.Secure.getInt(service.getContentResolver(), EXTREME_POWER_MODE, 0) == 1;
    }

    static boolean isSuperPowerModeEnable(XMPushService service) {
        if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(service.getPackageName())) {
            return false;
        }
        return Settings.System.getInt(service.getContentResolver(), SUPER_POWER_MODE, 0) == 1;
    }

    static boolean isInFalldownTimeRange(int falldownStart, int falldownEnd) {
        int intValue = Integer.valueOf(String.format("%tH", new java.util.Date())).intValue();
        if (falldownStart > falldownEnd) {
            return intValue >= falldownStart || intValue < falldownEnd;
        }
        return falldownStart < falldownEnd && intValue >= falldownStart && intValue < falldownEnd;
    }

    static boolean shouldFalldown(XMPushService service, int falldownStart, int falldownEnd) {
        return service.getApplicationContext().getPackageName().equals(PushConstants.PUSH_SERVICE_PACKAGE_NAME)
            && isInFalldownTimeRange(falldownStart, falldownEnd)
            && !DeviceInfo.isScreenOn(service)
            && !DeviceInfo.isCharging(service.getApplicationContext());
    }

    static String resolveXmppRegionHost(String regionName) {
        if (Region.Global.name().equals(regionName)) {
            return ConnectionConfiguration.XMPP_SERVER_GLOBAL_HOST_P;
        }
        if (Region.Europe.name().equals(regionName)) {
            return ConnectionConfiguration.XMPP_SERVER_EUROPE_HOST_P;
        }
        if (Region.Russia.name().equals(regionName)) {
            return ConnectionConfiguration.XMPP_SERVER_RUSSIA_HOST_P;
        }
        if (Region.India.name().equals(regionName)) {
            return ConnectionConfiguration.XMPP_SERVER_INDIA_HOST_P;
        }
        return ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P;
    }
}
