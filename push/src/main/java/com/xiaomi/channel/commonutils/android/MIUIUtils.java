package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.PushConstants;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/MIUIUtils.class */
public class MIUIUtils {
    private static final String ANDROID_SYSTEM_PROPERTIES = "android.os.SystemProperties";
    public static final int IS_MIUI = 1;
    private static final String KEY_MIUI_VERSION_CODE = "ro.miui.ui.version.code";
    private static final String KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name";
    public static final String MIUI_OS_VERSION_ALPHA = "alpha";
    public static final String MIUI_OS_VERSION_DEVELOPMENT = "development";
    public static final String MIUI_OS_VERSION_STABLE = "stable";
    public static final int NOT_MIUI = 2;
    public static final int UNKNOWN = 0;
    private static int isMIUI = 0;
    private static int isInXMS = -1;
    private static Map<String, Region> locale2RegionMap = null;

    private MIUIUtils() {
    }

    private static Region findServerRegionByLocale(String str) {
        initLocale2RegionMap();
        return locale2RegionMap.get(str.toUpperCase());
    }

    public static String getCountryCode() {
        String str = SystemProperties.get("ro.miui.region", "");
        String str2 = str;
        if (TextUtils.isEmpty(str)) {
            str2 = SystemProperties.get("persist.sys.oppo.region", "");
        }
        String str3 = str2;
        if (TextUtils.isEmpty(str2)) {
            str3 = SystemProperties.get("ro.oppo.regionmark", "");
        }
        String str4 = str3;
        if (TextUtils.isEmpty(str3)) {
            str4 = SystemProperties.get("ro.hw.country", "");
        }
        String str5 = str4;
        if (TextUtils.isEmpty(str4)) {
            str5 = SystemProperties.get("ro.csc.countryiso_code", "");
        }
        String str6 = str5;
        if (TextUtils.isEmpty(str5)) {
            str6 = SystemProperties.get("ro.product.country.region", "");
        }
        String str7 = str6;
        if (TextUtils.isEmpty(str6)) {
            str7 = SystemProperties.get("gsm.vivo.countrycode", "");
        }
        String str8 = str7;
        if (TextUtils.isEmpty(str7)) {
            str8 = SystemProperties.get("persist.sys.oem.region", "");
        }
        String str9 = str8;
        if (TextUtils.isEmpty(str8)) {
            str9 = SystemProperties.get("ro.product.locale.region", "");
        }
        String str10 = str9;
        if (TextUtils.isEmpty(str9)) {
            str10 = SystemProperties.get("persist.sys.country", "");
        }
        if (!TextUtils.isEmpty(str10)) {
            MyLog.w("get region from system, region = " + str10);
        }
        String country = str10;
        if (TextUtils.isEmpty(str10)) {
            country = Locale.getDefault().getCountry();
            MyLog.w("locale.default.country = " + country);
        }
        return country;
    }

    public static int getIsMIUI() {
        int i;
        synchronized (MIUIUtils.class) {
            try {
                if (isMIUI == 0) {
                    try {
                        isMIUI = !TextUtils.isEmpty(getProperty(KEY_MIUI_VERSION_CODE)) || !TextUtils.isEmpty(getProperty(KEY_MIUI_VERSION_NAME)) ? 1 : 2;
                    } catch (Throwable th) {
                        MyLog.e("get isMIUI failed", th);
                        isMIUI = 0;
                    }
                    MyLog.i("isMIUI's value is: " + isMIUI);
                }
                i = isMIUI;
            } catch (Throwable th2) {
                throw th2;
            }
        }
        return i;
    }

    public static String getMIUIType() {
        synchronized (MIUIUtils.class) {
            try {
                int mIUIType = SystemUtils.getMIUIType();
                return (!isMIUI() || mIUIType <= 0) ? "" : mIUIType < 2 ? MIUI_OS_VERSION_ALPHA : mIUIType < 3 ? MIUI_OS_VERSION_DEVELOPMENT : MIUI_OS_VERSION_STABLE;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static int getMiuiVersionCode(Context context) {
        String property = getProperty(KEY_MIUI_VERSION_CODE);
        return (TextUtils.isEmpty(property) || !TextUtils.isDigitsOnly(property)) ? 0 : Integer.parseInt(property);
    }

    public static String getProperty(String str) {
        String str2;
        try {
            try {
                str2 = (String) JavaCalls.callStaticMethod(ANDROID_SYSTEM_PROPERTIES, "get", str, "");
            } catch (Exception e) {
                MyLog.e("fail to get property. " + e);
                str2 = null;
            }
            return str2;
        } catch (Throwable th) {
            return null;
        }
    }

    public static Region getRegion(String str) {
        Region regionFindServerRegionByLocale = findServerRegionByLocale(str);
        return regionFindServerRegionByLocale == null ? Region.Global : regionFindServerRegionByLocale;
    }

    private static void initLocale2RegionMap() {
        if (locale2RegionMap != null) {
            return;
        }
        HashMap<String, Region> map = new HashMap<>();
        locale2RegionMap = map;
        map.put("CN", Region.China);
        locale2RegionMap.put("FI", Region.Europe);
        locale2RegionMap.put("SE", Region.Europe);
        locale2RegionMap.put("NO", Region.Europe);
        locale2RegionMap.put("FO", Region.Europe);
        locale2RegionMap.put("EE", Region.Europe);
        locale2RegionMap.put("LV", Region.Europe);
        locale2RegionMap.put("LT", Region.Europe);
        locale2RegionMap.put("BY", Region.Europe);
        locale2RegionMap.put("MD", Region.Europe);
        locale2RegionMap.put("UA", Region.Europe);
        locale2RegionMap.put("PL", Region.Europe);
        locale2RegionMap.put("CZ", Region.Europe);
        locale2RegionMap.put("SK", Region.Europe);
        locale2RegionMap.put("HU", Region.Europe);
        locale2RegionMap.put("DE", Region.Europe);
        locale2RegionMap.put("AT", Region.Europe);
        locale2RegionMap.put("CH", Region.Europe);
        locale2RegionMap.put("LI", Region.Europe);
        locale2RegionMap.put("GB", Region.Europe);
        locale2RegionMap.put("IE", Region.Europe);
        locale2RegionMap.put("NL", Region.Europe);
        locale2RegionMap.put("BE", Region.Europe);
        locale2RegionMap.put("LU", Region.Europe);
        locale2RegionMap.put("FR", Region.Europe);
        locale2RegionMap.put("RO", Region.Europe);
        locale2RegionMap.put("BG", Region.Europe);
        locale2RegionMap.put("RS", Region.Europe);
        locale2RegionMap.put("MK", Region.Europe);
        locale2RegionMap.put("AL", Region.Europe);
        locale2RegionMap.put("GR", Region.Europe);
        locale2RegionMap.put("SI", Region.Europe);
        locale2RegionMap.put("HR", Region.Europe);
        locale2RegionMap.put("IT", Region.Europe);
        locale2RegionMap.put("SM", Region.Europe);
        locale2RegionMap.put("MT", Region.Europe);
        locale2RegionMap.put("ES", Region.Europe);
        locale2RegionMap.put("PT", Region.Europe);
        locale2RegionMap.put("AD", Region.Europe);
        locale2RegionMap.put("CY", Region.Europe);
        locale2RegionMap.put("DK", Region.Europe);
        locale2RegionMap.put("RU", Region.Russia);
        locale2RegionMap.put("IN", Region.India);
    }

    public static boolean isGlobalRegion() {
        return !Region.China.name().equalsIgnoreCase(getRegion(getCountryCode()).name());
    }

    public static boolean isMIUI() {
        boolean z;
        synchronized (MIUIUtils.class) {
            try {
                z = true;
                if (getIsMIUI() != 1) {
                    z = false;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return z;
    }

    public static boolean isNotMIUI() {
        boolean z;
        synchronized (MIUIUtils.class) {
            try {
                z = getIsMIUI() == 2;
            } finally {
            }
        }
        return z;
    }

    public static boolean isXMS() {
        boolean z = true;
        if (isInXMS < 0) {
            Object objCallStaticMethod = JavaCalls.callStaticMethod("miui.external.SdkHelper", "isMiuiSystem", new Object[0]);
            isInXMS = 0;
            if (objCallStaticMethod != null && (objCallStaticMethod instanceof Boolean) && !((Boolean) Boolean.class.cast(objCallStaticMethod)).booleanValue()) {
                isInXMS = 1;
            }
        }
        if (isInXMS <= 0) {
            z = false;
        }
        return z;
    }

    public static boolean isXMSF(Context context) {
        return context != null && isXMSF(context.getPackageName());
    }

    public static boolean isXMSF(String str) {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(str);
    }
}
