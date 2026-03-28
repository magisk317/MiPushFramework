package com.xiaomi.mipush.sdk.stat.client;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter;
import java.util.Map;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/client/PushStatClient4Hybrid.class */
public class PushStatClient4Hybrid {
    private static Context sContext;

    public static void init(Context context, String str, String str2, String str3, IDbPathGetter iDbPathGetter) {
        sContext = context.getApplicationContext();
        String str4 = TextUtils.isEmpty(str2) ? "appId can not be null. " : "";
        String str5 = str4;
        if (TextUtils.isEmpty(str3)) {
            str5 = str4 + "channel can not be null. ";
        }
        String str6 = str5;
        if (TextUtils.isEmpty(str)) {
            str6 = str5 + "packageName can not be null.";
        }
        if (!TextUtils.isEmpty(str6)) {
            throw new IllegalArgumentException(str6);
        }
        PushStatClientManager.getInstance(sContext).init(str, str2, str3, iDbPathGetter);
    }

    private static void record(String str) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        PushStatClientManager.getInstance(sContext).record(str);
    }

    private static void record(JSONObject jSONObject) {
        if (jSONObject != null) {
            PushStatClientManager.getInstance(sContext).record(jSONObject.toString());
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

    public static void schedule() {
        PushStatClientManager.getInstance(sContext).schedule();
    }
}
