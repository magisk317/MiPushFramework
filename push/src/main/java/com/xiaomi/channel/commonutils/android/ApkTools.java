package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/ApkTools.class */
public class ApkTools {
    public static final String ARMEABI = "armeabi";

    public static void extractSo(Context context, String apkPath, String outputDir) {
    }

    public static List<String> getAbiList(Context context) {
        ArrayList arrayList = new ArrayList();
        String appPrimaryAbi = getAppPrimaryAbi(context);
        if (!TextUtils.isEmpty(appPrimaryAbi)) {
            arrayList.add(appPrimaryAbi);
        }
        String str = SystemProperties.get("ro.product.cpu.abi", "");
        if (!TextUtils.isEmpty(str)) {
            arrayList.add(str);
        }
        String str2 = SystemProperties.get("ro.product.cpu.abi2", "");
        if (!TextUtils.isEmpty(str2)) {
            arrayList.add(str2);
        }
        String str3 = SystemProperties.get("ro.product.cpu.abilist", "");
        if (!TextUtils.isEmpty(str3)) {
            String[] strArrSplit = str3.split(",");
            for (int i = 0; strArrSplit != null && i < strArrSplit.length; i++) {
                if (!TextUtils.isEmpty(strArrSplit[i])) {
                    arrayList.add(strArrSplit[i]);
                }
            }
        }
        arrayList.add(ARMEABI);
        return arrayList;
    }

    public static String getAppPrimaryAbi(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            Field declaredField = SystemUtils.loadClass(context, "android.content.pm.ApplicationInfo").getDeclaredField("primaryCpuAbi");
            declaredField.setAccessible(true);
            return (String) declaredField.get(applicationInfo);
        } catch (Throwable th) {
            return null;
        }
    }

    private static String getZipAbi(String str) {
        String[] strArrSplit;
        return (str == null || (strArrSplit = str.split("/")) == null || strArrSplit.length <= 1) ? ARMEABI : strArrSplit[strArrSplit.length - 2];
    }

    private static String getZipName(String str) {
        String[] strArrSplit;
        return (str == null || (strArrSplit = str.split("/")) == null || strArrSplit.length <= 0) ? str : strArrSplit[strArrSplit.length - 1];
    }

    private static int indexOf(List<String> list, String str) {
        for (int i = 0; list != null && i < list.size(); i++) {
            if (!TextUtils.isEmpty(str) && str.equalsIgnoreCase(list.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
