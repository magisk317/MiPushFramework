package com.xiaomi.mipush.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePushUtils.class */
public class AssemblePushUtils {
    public static final String HMS_PUSH_CLASS_NAME = "com.huawei.hms.core.service.HMSCoreService";
    public static final String HMS_PUSH_PACKAGE_NAME = "com.huawei.hwid";
    private static int isGoogleServiceSatisfied = -1;

    public static PhoneBrand getPhoneBrand(Context context) {
        try {
            return (context.getPackageManager().getServiceInfo(new ComponentName(HMS_PUSH_PACKAGE_NAME, HMS_PUSH_CLASS_NAME), 128) == null || !isAvailableEMUI()) ? PhoneBrand.OTHER : PhoneBrand.HUAWEI;
        } catch (Exception e) {
            return PhoneBrand.OTHER;
        }
    }

    private static boolean isAvailableEMUI() {
        try {
            String str = (String) JavaCalls.callStaticMethod("android.os.SystemProperties", "get", "ro.build.hw_emui_api_level", "");
            if (TextUtils.isEmpty(str)) {
                return false;
            }
            return Integer.parseInt(str) >= 9;
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }

    public static boolean isColorOSPushSupport(Context context) {
        Object objCallStaticMethod = JavaCalls.callStaticMethod("com.xiaomi.assemble.control.COSPushManager", "isSupportPush", context);
        boolean zBooleanValue = false;
        if (objCallStaticMethod != null) {
            zBooleanValue = false;
            if (objCallStaticMethod instanceof Boolean) {
                zBooleanValue = ((Boolean) Boolean.class.cast(objCallStaticMethod)).booleanValue();
            }
        }
        MyLog.v("color os push  is avaliable ? :" + zBooleanValue);
        return zBooleanValue;
    }

    public static boolean isFunTouchOSPushSupport(Context context) {
        Object objCallStaticMethod = JavaCalls.callStaticMethod("com.xiaomi.assemble.control.FTOSPushManager", "isSupportPush", context);
        boolean zBooleanValue = false;
        if (objCallStaticMethod != null) {
            zBooleanValue = false;
            if (objCallStaticMethod instanceof Boolean) {
                zBooleanValue = ((Boolean) Boolean.class.cast(objCallStaticMethod)).booleanValue();
            }
        }
        MyLog.v("fun touch os push  is avaliable ? :" + zBooleanValue);
        return zBooleanValue;
    }

    public static boolean isGoogleServiceSatisfied(Context context) {
        Object objCallMethod = JavaCalls.callMethod(JavaCalls.callStaticMethod("com.google.android.gms.common.GoogleApiAvailability", "getInstance", new Object[0]), "isGooglePlayServicesAvailable", context);
        Object staticField = JavaCalls.getStaticField("com.google.android.gms.common.ConnectionResult", "SUCCESS");
        if (staticField == null || !(staticField instanceof Integer)) {
            MyLog.v("google service is not avaliable");
            isGoogleServiceSatisfied = 0;
            return false;
        }
        int iIntValue = ((Integer) Integer.class.cast(staticField)).intValue();
        if (objCallMethod != null) {
            if (objCallMethod instanceof Integer) {
                isGoogleServiceSatisfied = ((Integer) Integer.class.cast(objCallMethod)).intValue() == iIntValue ? 1 : 0;
            } else {
                isGoogleServiceSatisfied = 0;
                MyLog.v("google service is not avaliable");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("is google service can be used");
        sb.append(isGoogleServiceSatisfied > 0);
        MyLog.v(sb.toString());
        boolean z = false;
        if (isGoogleServiceSatisfied > 0) {
            z = true;
        }
        return z;
    }
}
