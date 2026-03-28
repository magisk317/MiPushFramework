package com.xiaomi.push.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Messenger;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.NameValuePair;

final class ServiceClientIntentSupport {
    private ServiceClientIntentSupport() {
    }

    static Intent createServiceIntent(Context context, boolean isMiuiPushServiceEnabled) {
        Intent intent;
        if (isMiuiPushServiceEnabled) {
            intent = new Intent();
            intent.setPackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
            intent.setClassName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, getPushServiceName(context));
            intent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, context.getPackageName());
            disableMyPushService(context);
        } else {
            intent = new Intent(context, (Class<?>) XMPushService.class);
            intent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, context.getPackageName());
            enableMyPushService(context);
        }
        return intent;
    }

    static String getPushServiceName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4).versionCode >= 106 ? PushConstants.PUSH_SERVICE_CLASS_NAME_JAR : PushConstants.PUSH_SERVICE_CLASS_NAME;
        } catch (Exception e) {
            return PushConstants.PUSH_SERVICE_CLASS_NAME;
        }
    }

    static void putOpenParamsIntoIntent(Intent intent, String str, String str2, String str3, String str4, String str5, boolean z, Map<String, String> map, Map<String, String> map2, String str6, Messenger messenger) {
        intent.putExtra(PushConstants.EXTRA_USER_ID, str);
        intent.putExtra(PushConstants.EXTRA_CHANNEL_ID, str2);
        intent.putExtra(PushConstants.EXTRA_TOKEN, str3);
        intent.putExtra(PushConstants.EXTRA_SECURITY, str5);
        intent.putExtra(PushConstants.EXTRA_AUTH_METHOD, str4);
        intent.putExtra(PushConstants.EXTRA_KICK, z);
        intent.putExtra(PushConstants.EXTRA_SESSION, str6);
        intent.putExtra(PushConstants.EXTRA_MESSENGER, messenger);
        putJoinedAttributes(intent, PushConstants.EXTRA_CLIENT_ATTR, map);
        putJoinedAttributes(intent, PushConstants.EXTRA_CLOUD_ATTR, map2);
    }

    static Map<String, String> translate(List<NameValuePair> list) {
        HashMap map = new HashMap();
        if (list != null && list.size() > 0) {
            for (NameValuePair nameValuePair : list) {
                if (nameValuePair != null) {
                    map.put(nameValuePair.getName(), nameValuePair.getValue());
                }
            }
        }
        return map;
    }

    static String joinAttributes(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey());
            sb.append(":");
            sb.append(entry.getValue());
            if (i < map.size()) {
                sb.append(",");
            }
            i++;
        }
        return sb.toString();
    }

    private static void disableMyPushService(Context context) {
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, (Class<?>) XMPushService.class), 2, 1);
    }

    private static void enableMyPushService(Context context) {
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, (Class<?>) XMPushService.class), 1, 1);
    }

    private static void putJoinedAttributes(Intent intent, String str, Map<String, String> map) {
        if (map == null || map.size() <= 0) {
            return;
        }
        String strJoinAttributes = joinAttributes(map);
        if (TextUtils.isEmpty(strJoinAttributes)) {
            return;
        }
        intent.putExtra(str, strJoinAttributes);
    }
}
