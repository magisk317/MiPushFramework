package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;
import android.util.Log;
import com.xiaomi.push.service.PushConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/ManifestChecker.class */
public class ManifestChecker {

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/ManifestChecker$IllegalManifestException.class */
    public static class IllegalManifestException extends RuntimeException {
        private static final long serialVersionUID = 1;

        public IllegalManifestException(String str) {
            super(str);
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/ManifestChecker$ServiceCheckInfo.class */
    public static class ServiceCheckInfo {
        public boolean enabled;
        public boolean exported;
        public String permission;
        public String serviceName;

        public ServiceCheckInfo(String str, boolean z, boolean z2, String str2) {
            this.serviceName = str;
            this.enabled = z;
            this.exported = z2;
            this.permission = str2;
        }
    }

    public static void asynCheckManifest(final Context context) {
        new Thread(new Runnable() { // from class: com.xiaomi.mipush.sdk.ManifestChecker.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 4612);
                    ManifestChecker.checkReceivers(context);
                    ManifestChecker.checkServices(context, packageInfo);
                    ManifestChecker.checkPermissions(context, packageInfo);
                } catch (Throwable th) {
                    Log.e("ManifestChecker", "", th);
                }
            }
        }).start();
    }

    private static void checkAssembleReceiver(Context context, String str, String str2) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        Intent intent = new Intent(str);
        intent.setPackage(packageName);
        boolean z = false;
        Iterator<ResolveInfo> it = packageManager.queryBroadcastReceivers(intent, 16384).iterator();
        while (it.hasNext()) {
            ActivityInfo activityInfo = it.next().activityInfo;
            z = (activityInfo == null || TextUtils.isEmpty(activityInfo.name) || !activityInfo.name.equals(str2)) ? false : true;
            if (z) {
                break;
            }
        }
        if (!z) {
            throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> is missing or disabled in AndroidManifest.", str2));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkPermissions(Context context, PackageInfo packageInfo) {
        HashSet<String> hashSet = new HashSet<>();
        String str = context.getPackageName() + ".permission.MIPUSH_RECEIVE";
        hashSet.addAll(Arrays.asList("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", str, "android.permission.ACCESS_WIFI_STATE", "android.permission.VIBRATE"));
        boolean z = false;
        if (packageInfo.permissions != null) {
            PermissionInfo[] permissionInfoArr = packageInfo.permissions;
            int length = permissionInfoArr.length;
            int i = 0;
            while (true) {
                z = false;
                if (i >= length) {
                    break;
                }
                if (str.equals(permissionInfoArr[i].name)) {
                    z = true;
                    break;
                }
                i++;
            }
        }
        if (!z) {
            throw new IllegalManifestException(String.format("<permission android:name=\"%1$s\" .../> is undefined in AndroidManifest.", str));
        }
        if (packageInfo.requestedPermissions != null) {
            for (String str2 : packageInfo.requestedPermissions) {
                if (!TextUtils.isEmpty(str2) && hashSet.contains(str2)) {
                    hashSet.remove(str2);
                    if (hashSet.isEmpty()) {
                        break;
                    }
                }
            }
        }
        if (!hashSet.isEmpty()) {
            throw new IllegalManifestException(String.format("<uses-permission android:name=\"%1$s\"/> is missing in AndroidManifest.", hashSet.iterator().next()));
        }
    }

    private static void checkReceiverInfo(ActivityInfo activityInfo, Boolean[] boolArr) {
        if (boolArr[0].booleanValue() != activityInfo.enabled) {
            throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> in AndroidManifest had the wrong enabled attribute, which should be android:enabled=%2$b.", activityInfo.name, boolArr[0]));
        }
        if (boolArr[1].booleanValue() != activityInfo.exported) {
            throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> in AndroidManifest had the wrong exported attribute, which should be android:exported=%2$b.", activityInfo.name, boolArr[1]));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkReceivers(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.setPackage(packageName);
        ActivityInfo receiverInfo = findReceiverInfo(packageManager, intent, PushServiceReceiver.class);
        if (receiverInfo == null) {
            throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> is missing or disabled in AndroidManifest.", PushServiceReceiver.class.getCanonicalName()));
        }
        checkReceiverInfo(receiverInfo, new Boolean[]{Boolean.TRUE, Boolean.TRUE});
        if (!MiPushClient.shouldUseMIUIPush(context)) {
            Intent intent2 = new Intent(PushConstants.ACTION_PING_TIMER);
            intent2.setPackage(packageName);
            ActivityInfo receiverInfo2 = findReceiverInfo(packageManager, intent2, com.xiaomi.push.service.receivers.PingReceiver.class);
            if (receiverInfo2 == null) {
                throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> is missing or disabled in AndroidManifest.", com.xiaomi.push.service.receivers.PingReceiver.class.getCanonicalName()));
            }
            checkReceiverInfo(receiverInfo2, new Boolean[]{Boolean.TRUE, Boolean.FALSE});
        }
        if (MiPushClient.getOpenHmsPush(context)) {
            checkOptionalAssembleReceiver(context, PushConstants.HMS_PUSH_ACTION_NEW_MESSAGE, PushConstants.HMS_PUSH_RECEIVER_CLASS_NAME, PushConstants.HMS_OLD_PUSH_ACTION_NEW_MESSAGE, PushConstants.HMS_PUSH_OLD_RECEIVER_CLASS_NAME);
        }
        if (MiPushClient.getOpenVIVOPush(context)) {
            checkOptionalAssembleReceiver(context, PushConstants.VIVO_PUSH_MESSAGE_RECEIVER_ACTION_NAME, PushConstants.VIVO_PUSH_MESSAGE_RECEIVER_CLASS_NAME);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkServices(Context context, PackageInfo packageInfo) {
        HashMap<String, String> map = new HashMap<>();
        HashMap<String, ServiceCheckInfo> map2 = new HashMap<>();
        map2.put(PushMessageHandler.class.getCanonicalName(), new ServiceCheckInfo(PushMessageHandler.class.getCanonicalName(), true, true, ""));
        map2.put(MessageHandleService.class.getCanonicalName(), new ServiceCheckInfo(MessageHandleService.class.getCanonicalName(), true, false, ""));
        if (!MiPushClient.shouldUseMIUIPush(context) || containAnyService(packageInfo, new String[]{PushConstants.XM_SERVICE_CLASS_NAME_JAR, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR})) {
            map2.put(PushConstants.XM_SERVICE_CLASS_NAME_JAR, new ServiceCheckInfo(PushConstants.XM_SERVICE_CLASS_NAME_JAR, true, false, "android.permission.BIND_JOB_SERVICE"));
            map2.put(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR, new ServiceCheckInfo(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR, true, false, ""));
        }
        if (MiPushClient.getOpenFCMPush(context)) {
            map2.put(PushConstants.FCM_PUSH_INSTANCE_ID_SERVICE_NAME, new ServiceCheckInfo(PushConstants.FCM_PUSH_INSTANCE_ID_SERVICE_NAME, true, false, ""));
            map2.put(PushConstants.FCM_PUSH_MESSAGE_SERVICE_NAME, new ServiceCheckInfo(PushConstants.FCM_PUSH_MESSAGE_SERVICE_NAME, true, false, ""));
        }
        if (MiPushClient.getOpenOPPOPush(context)) {
            map2.put(PushConstants.OPPO_PUSH_MESSAGE_SERVICE_NAME, new ServiceCheckInfo(PushConstants.OPPO_PUSH_MESSAGE_SERVICE_NAME, true, true, "com.coloros.mcs.permission.SEND_MCS_MESSAGE"));
        }
        if (packageInfo.services != null) {
            for (ServiceInfo serviceInfo : packageInfo.services) {
                if (!TextUtils.isEmpty(serviceInfo.name) && map2.containsKey(serviceInfo.name)) {
                    ServiceCheckInfo serviceCheckInfo = map2.remove(serviceInfo.name);
                    boolean z = serviceCheckInfo.enabled;
                    boolean z2 = serviceCheckInfo.exported;
                    String str = serviceCheckInfo.permission;
                    if (z != serviceInfo.enabled) {
                        throw new IllegalManifestException(String.format("<service android:name=\"%1$s\" .../> in AndroidManifest had the wrong enabled attribute, which should be android:enabled=%2$b.", serviceInfo.name, Boolean.valueOf(z)));
                    }
                    if (z2 != serviceInfo.exported) {
                        throw new IllegalManifestException(String.format("<service android:name=\"%1$s\" .../> in AndroidManifest had the wrong exported attribute, which should be android:exported=%2$b.", serviceInfo.name, Boolean.valueOf(z2)));
                    }
                    if (!TextUtils.isEmpty(str) && !TextUtils.equals(str, serviceInfo.permission)) {
                        throw new IllegalManifestException(String.format("<service android:name=\"%1$s\" .../> in AndroidManifest had the wrong permission attribute, which should be android:permission=\"%2$s\".", serviceInfo.name, str));
                    }
                    map.put(serviceInfo.name, serviceInfo.processName);
                    if (map2.isEmpty()) {
                        break;
                    }
                }
            }
        }
        if (!map2.isEmpty()) {
            throw new IllegalManifestException(String.format("<service android:name=\"%1$s\" .../> is missing or disabled in AndroidManifest.", map2.keySet().iterator().next()));
        }
        if (!TextUtils.equals((CharSequence) map.get(PushMessageHandler.class.getCanonicalName()), (CharSequence) map.get(MessageHandleService.class.getCanonicalName()))) {
            throw new IllegalManifestException(String.format("\"%1$s\" and \"%2$s\" must be running in the same process.", PushMessageHandler.class.getCanonicalName(), MessageHandleService.class.getCanonicalName()));
        }
        if (map.containsKey(PushConstants.XM_SERVICE_CLASS_NAME_JAR) && map.containsKey(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR) && !TextUtils.equals((CharSequence) map.get(PushConstants.XM_SERVICE_CLASS_NAME_JAR), (CharSequence) map.get(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR))) {
            throw new IllegalManifestException(String.format("\"%1$s\" and \"%2$s\" must be running in the same process.", PushConstants.XM_SERVICE_CLASS_NAME_JAR, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR));
        }
    }

    private static boolean containAnyService(PackageInfo packageInfo, String[] strArr) {
        for (ServiceInfo serviceInfo : packageInfo.services) {
            if (containString(strArr, serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containString(String[] strArr, String str) {
        if (strArr == null || str == null) {
            return false;
        }
        for (String str2 : strArr) {
            if (TextUtils.equals(str2, str)) {
                return true;
            }
        }
        return false;
    }

    private static ActivityInfo findReceiverInfo(PackageManager packageManager, Intent intent, Class<?> cls) {
        Iterator<ResolveInfo> it = packageManager.queryBroadcastReceivers(intent, 16384).iterator();
        while (it.hasNext()) {
            ActivityInfo activityInfo = it.next().activityInfo;
            if (activityInfo != null && cls.getCanonicalName().equals(activityInfo.name)) {
                return activityInfo;
            }
        }
        return null;
    }

    private static ActivityInfo findReceiverInfo(PackageManager packageManager, Intent intent, String str) {
        Iterator<ResolveInfo> it = packageManager.queryBroadcastReceivers(intent, 16384).iterator();
        while (it.hasNext()) {
            ActivityInfo activityInfo = it.next().activityInfo;
            if (activityInfo != null && TextUtils.equals(str, activityInfo.name)) {
                return activityInfo;
            }
        }
        return null;
    }

    private static void checkOptionalAssembleReceiver(Context context, String... strArr) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        for (int i = 0; i + 1 < strArr.length; i += 2) {
            String str = strArr[i];
            String str2 = strArr[i + 1];
            if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
                continue;
            }
            Intent intent = new Intent(str);
            intent.setPackage(packageName);
            ActivityInfo receiverInfo = findReceiverInfo(packageManager, intent, str2);
            if (receiverInfo != null) {
                checkReceiverInfo(receiverInfo, new Boolean[]{Boolean.TRUE, Boolean.TRUE});
                return;
            }
        }
        throw new IllegalManifestException(String.format("<receiver android:name=\"%1$s\" .../> is missing or disabled in AndroidManifest.", strArr[1]));
    }
}
