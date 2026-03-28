package com.xiaomi.mipush.sdk.stat.client;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import java.util.Map;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/client/PushStatClient.class */
public class PushStatClient {
    private static Context sContext;

    public static void init(Context context, String str, String str2) {
        sContext = context.getApplicationContext();
        String str3 = TextUtils.isEmpty(str) ? "appId can not be null. " : "";
        String str4 = str3;
        if (TextUtils.isEmpty(str2)) {
            str4 = str3 + "channel can not be null. ";
        }
        if (!TextUtils.isEmpty(str4)) {
            throw new IllegalArgumentException(str4);
        }
        PushStatClientManager.getInstance(sContext).init(str, str2);
        if (AppInfoUtils.isAppMainProc(sContext)) {
            PushStatClientManager.getInstance(sContext).schedule();
        }
    }

    public static void record(ClientUploadDataItem clientUploadDataItem) {
        if (clientUploadDataItem != null) {
            PushStatClientManager.getInstance(sContext).record(clientUploadDataItem);
        }
    }

    public static void record(String str) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        PushStatClientManager.getInstance(sContext).record(str);
    }

    private static void record(JSONObject jSONObject) {
        if (jSONObject != null) {
            record(jSONObject.toString());
        }
    }

    public static void recordCalculateEvent(String str, String str2, long j) {
        record(EventDataItem.getCalculateEvent(str, str2, j).toJson());
    }

    public static void recordCalculateEvent(String str, String str2, long j, Map<String, String> map) {
        record(EventDataItem.getCalculateEvent(str, str2, j, map).toJson());
    }

    public static void recordCountEvent(String str, String str2) {
        record(EventDataItem.getCountEvent(str, str2).toJson());
    }

    public static void recordCountEvent(String str, String str2, Map<String, String> map) {
        record(EventDataItem.getCountEvent(str, str2, map).toJson());
    }

    public static void recordNumericPropertyEvent(String str, String str2, long j) {
        record(EventDataItem.getNumericPropertyEvent(str, str2, j).toJson());
    }

    public static void recordStringPropertyEvent(String str, String str2, String str3) {
        record(EventDataItem.getPropertyEvent(str, str2, str3).toJson());
    }
}
