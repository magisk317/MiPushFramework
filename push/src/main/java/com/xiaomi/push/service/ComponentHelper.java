package com.xiaomi.push.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/ComponentHelper.class */
public class ComponentHelper {
    public static boolean checkActivity(Context context, ComponentName componentName) {
        try {
            new Intent().setComponent(componentName);
            context.getPackageManager().getActivityInfo(componentName, 128);
            return true;
        } catch (Exception e) {
            MyLog.w("checkActivity componentName: " + componentName + ", " + e);
            return false;
        }
    }

    public static boolean checkActivity(Context context, String str, String str2) {
        boolean z = false;
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = new Intent(str2);
            intent.setPackage(str);
            z = packageManager.resolveActivity(intent, 65536) != null;
        } catch (Exception e) {
            MyLog.w("checkActivity action: " + str2 + ", " + e);
        }
        return z;
    }

    public static boolean checkProvider(Context context, String str) {
        boolean z;
        boolean z2 = false;
        boolean z3 = false;
        try {
            PackageManager packageManager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 19) {
                List<ProviderInfo> listQueryContentProviders = packageManager.queryContentProviders((String) null, 0, 8);
                z = false;
                if (listQueryContentProviders != null) {
                    z = false;
                    if (!listQueryContentProviders.isEmpty()) {
                        Iterator<ProviderInfo> it = listQueryContentProviders.iterator();
                        while (true) {
                            z = z2;
                            z3 = z2;
                            if (!it.hasNext()) {
                                break;
                            }
                            ProviderInfo next = it.next();
                            boolean z4 = z2;
                            if (next.enabled) {
                                z4 = z2;
                                if (next.exported) {
                                    z4 = z2;
                                    if (next.authority.equals(str)) {
                                        z4 = true;
                                    }
                                }
                            }
                            z2 = z4;
                        }
                    }
                }
            } else {
                z = true;
            }
            z3 = z;
        } catch (Exception e) {
            MyLog.w("checkProvider " + e);
        }
        return z3;
    }

    public static boolean checkService(Context context, String str) {
        boolean z;
        try {
            ServiceInfo[] serviceInfoArr = context.getPackageManager().getPackageInfo(str, 4).services;
            z = false;
            if (serviceInfoArr != null) {
                int length = serviceInfoArr.length;
                int i = 0;
                while (true) {
                    z = false;
                    if (i >= length) {
                        break;
                    }
                    ServiceInfo serviceInfo = serviceInfoArr[i];
                    if (serviceInfo.exported && serviceInfo.enabled && "com.xiaomi.mipush.sdk.PushMessageHandler".equals(serviceInfo.name) && !context.getPackageName().equals(serviceInfo.packageName)) {
                        z = true;
                        break;
                    }
                    i++;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.w("checkService " + e);
            z = false;
        }
        return z;
    }

    public static boolean checkService(Context context, String str, String str2) {
        boolean z;
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = new Intent(str2);
            intent.setPackage(str);
            List<ResolveInfo> listQueryIntentServices = packageManager.queryIntentServices(intent, 32);
            z = false;
            if (listQueryIntentServices != null) {
                z = false;
                if (!listQueryIntentServices.isEmpty()) {
                    z = true;
                }
            }
        } catch (Exception e) {
            MyLog.w("checkService action: " + str2 + ", " + e);
            z = false;
        }
        return z;
    }
}
