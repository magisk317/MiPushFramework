package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/HWPushHelper.class */
public class HWPushHelper {
    private static final String LAST_CONNECT_TIME = "last_connect_time";
    private static final String LAST_GET_TOKEN_TIME = "last_get_token_time";
    private static boolean isFailed = false;

    public static void convertMessage(Intent intent) {
        AssemblePushHelper.convertMessage(intent);
    }

    public static boolean hasNetwork(Context context) {
        return AssemblePushHelper.hasNetwork(context);
    }

    public static boolean isHmsTokenSynced(Context context) {
        String tokenKey = AssemblePushHelper.getTokenKey(AssemblePush.ASSEMBLE_PUSH_HUAWEI);
        if (TextUtils.isEmpty(tokenKey)) {
            return false;
        }
        String assemblePushToken = AssemblePushHelper.getAssemblePushToken(context, tokenKey);
        String syncStatus = OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN);
        return (TextUtils.isEmpty(assemblePushToken) || TextUtils.isEmpty(syncStatus) || !OperatePushHelper.SYNCED.equals(syncStatus)) ? false : true;
    }

    public static boolean isUserOpenHmsPush(Context context) {
        return MiPushClient.getOpenHmsPush(context);
    }

    public static boolean needConnect() {
        return isFailed;
    }

    public static void notifyHmsNotificationMessageClicked(Context context, String str) {
        String str2 = "";
        if (!TextUtils.isEmpty(str)) {
            try {
                JSONArray jSONArray = new JSONArray(str);
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject = jSONArray.getJSONObject(i);
                    if (jSONObject.has("pushMsg")) {
                        str2 = jSONObject.getString("pushMsg");
                        break;
                    }
                }
            } catch (Exception e) {
                MyLog.e(e.toString());
                str2 = "";
            }
        }
        PushMessageReceiver miPushReceiver = AssemblePushHelper.getMiPushReceiver(context);
        if (miPushReceiver == null) {
            return;
        }
        MiPushMessage miPushMessageParseMiPushMessage = AssemblePushHelper.parseMiPushMessage(str2);
        if (miPushMessageParseMiPushMessage.getExtra() != null && miPushMessageParseMiPushMessage.getExtra().containsKey("notify_effect")) {
            return;
        }
        miPushReceiver.onNotificationMessageClicked(context, miPushMessageParseMiPushMessage);
    }

    public static void notifyHmsPassThoughMessageArrived(Context context, String str) {
        String string = "";
        try {
            if (!TextUtils.isEmpty(str)) {
                JSONObject jSONObject = new JSONObject(str);
                string = "";
                if (jSONObject.has("content")) {
                    string = jSONObject.getString("content");
                }
            }
        } catch (Exception e) {
            MyLog.e(e.toString());
            string = "";
        }
        PushMessageReceiver miPushReceiver = AssemblePushHelper.getMiPushReceiver(context);
        if (miPushReceiver != null) {
            miPushReceiver.onReceivePassThroughMessage(context, AssemblePushHelper.parseMiPushMessage(string));
        }
    }

    public static void registerHuaWeiAssemblePush(Context context) {
        AbstractPushManager manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI);
        if (manager != null) {
            manager.register();
        }
    }

    public static void reportError(String str, int i) {
        AssemblePushHelper.reportError(str, i);
    }

    public static void setConnectTime(Context context) {
        synchronized (HWPushHelper.class) {
            try {
                context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_CONNECT_TIME, System.currentTimeMillis()).commit();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void setGetTokenTime(Context context) {
        synchronized (HWPushHelper.class) {
            try {
                context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_GET_TOKEN_TIME, System.currentTimeMillis()).commit();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void setNeedConnect(boolean z) {
        isFailed = z;
    }

    public static boolean shouldGetToken(Context context) {
        boolean z;
        synchronized (HWPushHelper.class) {
            z = false;
            try {
                if (Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_GET_TOKEN_TIME, -1L)) > 172800000) {
                    z = true;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return z;
    }

    public static boolean shouldTryConnect(Context context) {
        boolean z;
        synchronized (HWPushHelper.class) {
            z = false;
            try {
                if (Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_CONNECT_TIME, -1L)) > 5000) {
                    z = true;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return z;
    }

    public static void uploadToken(Context context, String str) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_HUAWEI, str);
    }
}
