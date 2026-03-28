package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PushConstants;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePushHelper.class */
public class AssemblePushHelper {
    private static final String COS_PUSH_ERROR = "cos_push_error";
    private static final String COS_PUSH_TOKEN = "cos_push_token";
    private static final String FCM_PUSH_ERROR = "fcm_push_error";
    private static final String FCM_PUSH_TOKEN = "fcm_push_token";
    private static final String FTOS_PUSH_ERROR = "ftos_push_error";
    private static final String FTOS_PUSH_TOKEN = "ftos_push_token";
    protected static final String HMS_NOTIFICATION_CONTENT = "pushMsg";
    protected static final String HMS_PASS_MESSAGE_CONTENT = "content";
    private static final String HMS_PUSH_ERROR = "hms_push_error";
    private static final String HMS_PUSH_TOKEN = "hms_push_token";
    private static final String KEY_ALIAS = "alias";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_DESC = "description";
    private static final String KEY_EXTRA = "extra";
    private static final String KEY_MESSAGE_ID = "messageId";
    private static final String KEY_MESSAGE_TYPE = "messageType";
    private static final String KEY_NOTIFIED = "isNotified";
    private static final String KEY_NOTIFY_ID = "notifyId";
    private static final String KEY_NOTIFY_TYPE = "notifyType";
    private static final String KEY_PASS_THROUGH = "passThrough";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_USER_ACCOUNT = "user_account";
    private static HashMap<String, String> mTokens = new HashMap<>();

    /* JADX INFO: renamed from: com.xiaomi.mipush.sdk.AssemblePushHelper$2, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePushHelper$2.class */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush;

        static {
            int[] iArr = new int[AssemblePush.values().length];
            $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush = iArr;
            try {
                iArr[AssemblePush.ASSEMBLE_PUSH_HUAWEI.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_FCM.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_COS.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_FTOS.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    static void checkAssemblePushStatus(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("mipush_extra", 0);
        String tokenKey = getTokenKey(AssemblePush.ASSEMBLE_PUSH_HUAWEI);
        String tokenKey2 = getTokenKey(AssemblePush.ASSEMBLE_PUSH_FCM);
        boolean z = false;
        if (!TextUtils.isEmpty(sharedPreferences.getString(tokenKey, ""))) {
            z = false;
            if (TextUtils.isEmpty(sharedPreferences.getString(tokenKey2, ""))) {
                z = true;
            }
        }
        if (z) {
            PushServiceClient.getInstance(context).send3rdPushHint(2, tokenKey);
        }
    }

    public static void clearToken(Context context, AssemblePush assemblePush) {
        String tokenKey = getTokenKey(assemblePush);
        if (TextUtils.isEmpty(tokenKey)) {
            return;
        }
        SharedPrefsCompat.apply(context.getSharedPreferences("mipush_extra", 0).edit().putString(tokenKey, ""));
    }

    public static void convertMessage(Intent intent) {
        Bundle extras;
        if (intent == null || (extras = intent.getExtras()) == null || !extras.containsKey(HMS_NOTIFICATION_CONTENT)) {
            return;
        }
        intent.putExtra(PushMessageHelper.KEY_MESSAGE, parseMiPushMessage(extras.getString(HMS_NOTIFICATION_CONTENT)));
    }

    public static HashMap<String, String> getAssemblePushExtra(Context context, AssemblePush assemblePush) throws PackageManager.NameNotFoundException {
        String str;
        HashMap<String, String> map = new HashMap<>();
        String tokenKey = getTokenKey(assemblePush);
        if (TextUtils.isEmpty(tokenKey)) {
            return map;
        }
        switch (AnonymousClass2.$SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[assemblePush.ordinal()]) {
            case 1:
                ApplicationInfo applicationInfo = null;
                try {
                    applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 128);
                } catch (Exception e) {
                    MyLog.e(e.toString());
                }
                int i = -1;
                if (applicationInfo != null) {
                    i = applicationInfo.metaData.getInt(Constants.HUAWEI_HMS_CLIENT_APPID);
                }
                str = "brand:" + AssemblePushUtils.getPhoneBrand(context).name() + Constants.WAVE_SEPARATOR + "token:" + getAssemblePushToken(context, tokenKey) + Constants.WAVE_SEPARATOR + "package_name:" + context.getPackageName() + Constants.WAVE_SEPARATOR + "app_id:" + i;
                break;
            case 2:
                str = "brand:" + PhoneBrand.FCM.name() + Constants.WAVE_SEPARATOR + "token:" + getAssemblePushToken(context, tokenKey) + Constants.WAVE_SEPARATOR + "package_name:" + context.getPackageName();
                break;
            case 3:
                str = "brand:" + PhoneBrand.OPPO.name() + Constants.WAVE_SEPARATOR + "token:" + getAssemblePushToken(context, tokenKey) + Constants.WAVE_SEPARATOR + "package_name:" + context.getPackageName();
                break;
            case 4:
                str = "brand:" + PhoneBrand.VIVO.name() + Constants.WAVE_SEPARATOR + "token:" + getAssemblePushToken(context, tokenKey) + Constants.WAVE_SEPARATOR + "package_name:" + context.getPackageName();
                break;
            default:
                str = null;
                break;
        }
        map.put(Constants.ASSEMBLE_PUSH_REG_INFO, str);
        return map;
    }

    protected static String getAssemblePushToken(Context context, String str) {
        String str2;
        synchronized (AssemblePushHelper.class) {
            try {
                String str3 = mTokens.get(str);
                str2 = str3;
                if (TextUtils.isEmpty(str3)) {
                    str2 = "";
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return str2;
    }

    protected static PushMessageReceiver getMiPushReceiver(Context context) {
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.setPackage(context.getPackageName());
        try {
            List<ResolveInfo> listQueryBroadcastReceivers = context.getPackageManager().queryBroadcastReceivers(intent, 32);
            ResolveInfo next = null;
            if (listQueryBroadcastReceivers != null) {
                Iterator<ResolveInfo> it = listQueryBroadcastReceivers.iterator();
                while (true) {
                    next = null;
                    if (!it.hasNext()) {
                        break;
                    }
                    next = it.next();
                    if (next.activityInfo != null && next.activityInfo.packageName.equals(context.getPackageName())) {
                        break;
                    }
                }
            }
            if (next != null) {
                return (PushMessageReceiver) SystemUtils.loadClass(context, next.activityInfo.name).newInstance();
            }
            return null;
        } catch (Exception e) {
            MyLog.e(e.toString());
            return null;
        }
    }

    public static String getSPErrorKey(AssemblePush assemblePush) {
        String str;
        switch (AnonymousClass2.$SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[assemblePush.ordinal()]) {
            case 1:
                str = HMS_PUSH_ERROR;
                break;
            case 2:
                str = FCM_PUSH_ERROR;
                break;
            case 3:
                str = COS_PUSH_ERROR;
                break;
            case 4:
                str = FTOS_PUSH_ERROR;
                break;
            default:
                str = null;
                break;
        }
        return str;
    }

    public static String getTokenKey(AssemblePush assemblePush) {
        String str;
        switch (AnonymousClass2.$SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[assemblePush.ordinal()]) {
            case 1:
                str = HMS_PUSH_TOKEN;
                break;
            case 2:
                str = FCM_PUSH_TOKEN;
                break;
            case 3:
                str = COS_PUSH_TOKEN;
                break;
            case 4:
                str = FTOS_PUSH_TOKEN;
                break;
            default:
                str = null;
                break;
        }
        return str;
    }

    public static boolean hasNetwork(Context context) {
        if (context == null) {
            return false;
        }
        return Network.hasNetwork(context);
    }

    public static boolean isOpenAssemblePushOnlineSwitch(Context context, AssemblePush assemblePush) {
        if (AssemblePushInfoHelper.getConfigKeyByType(assemblePush) != null) {
            return OnlineConfig.getInstance(context).getBooleanValue(AssemblePushInfoHelper.getConfigKeyByType(assemblePush).getValue(), true);
        }
        return false;
    }

    public static MiPushMessage parseMiPushMessage(String str) {
        MiPushMessage miPushMessage = new MiPushMessage();
        if (!TextUtils.isEmpty(str)) {
            try {
            } catch (Exception e) {
                e = e;
            }
            try {
                JSONObject jSONObject = new JSONObject(str);
                if (jSONObject.has("messageId")) {
                    miPushMessage.setMessageId(jSONObject.getString("messageId"));
                }
                if (jSONObject.has("description")) {
                    miPushMessage.setDescription(jSONObject.getString("description"));
                }
                if (jSONObject.has("title")) {
                    miPushMessage.setTitle(jSONObject.getString("title"));
                }
                if (jSONObject.has("content")) {
                    miPushMessage.setContent(jSONObject.getString("content"));
                }
                if (jSONObject.has(KEY_PASS_THROUGH)) {
                    miPushMessage.setPassThrough(jSONObject.getInt(KEY_PASS_THROUGH));
                }
                if (jSONObject.has(KEY_NOTIFY_TYPE)) {
                    miPushMessage.setNotifyType(jSONObject.getInt(KEY_NOTIFY_TYPE));
                }
                if (jSONObject.has(KEY_MESSAGE_TYPE)) {
                    miPushMessage.setMessageType(jSONObject.getInt(KEY_MESSAGE_TYPE));
                }
                if (jSONObject.has(KEY_ALIAS)) {
                    miPushMessage.setAlias(jSONObject.getString(KEY_ALIAS));
                }
                if (jSONObject.has(KEY_TOPIC)) {
                    miPushMessage.setTopic(jSONObject.getString(KEY_TOPIC));
                }
                if (jSONObject.has(KEY_USER_ACCOUNT)) {
                    miPushMessage.setUserAccount(jSONObject.getString(KEY_USER_ACCOUNT));
                }
                if (jSONObject.has(KEY_NOTIFY_ID)) {
                    miPushMessage.setNotifyId(jSONObject.getInt(KEY_NOTIFY_ID));
                }
                if (jSONObject.has(KEY_CATEGORY)) {
                    miPushMessage.setCategory(jSONObject.getString(KEY_CATEGORY));
                }
                if (jSONObject.has(KEY_NOTIFIED)) {
                    miPushMessage.setNotified(jSONObject.getBoolean(KEY_NOTIFIED));
                }
                if (jSONObject.has(KEY_EXTRA)) {
                    JSONObject jSONObject2 = jSONObject.getJSONObject(KEY_EXTRA);
                    Iterator<String> itKeys = jSONObject2.keys();
                    HashMap map = new HashMap();
                    while (itKeys != null && itKeys.hasNext()) {
                        String next = itKeys.next();
                        map.put(next, jSONObject2.getString(next));
                    }
                    if (map.size() > 0) {
                        miPushMessage.setExtra(map);
                    }
                }
            } catch (Exception e2) {
                MyLog.e(e2.toString());
            }
        }
        return miPushMessage;
    }

    public static void registerAssemblePush(Context context) {
        AssemblePushCollectionsManager.getInstance(context).register();
    }

    public static void reportError(String str, int i) {
        MiTinyDataClient.upload(HMS_PUSH_ERROR, str, 1L, "error code = " + i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void saveAssemblePushToken(Context context, AssemblePush assemblePush, String str) {
        synchronized (AssemblePushHelper.class) {
            try {
                String tokenKey = getTokenKey(assemblePush);
                if (TextUtils.isEmpty(tokenKey)) {
                    MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file");
                    return;
                }
                SharedPrefsCompat.apply(context.getSharedPreferences("mipush_extra", 0).edit().putString(tokenKey, str));
                MyLog.w("ASSEMBLE_PUSH : update sp file success!  " + str);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void saveAssemblePushTokenAfterAck(final Context context, final AssemblePush assemblePush, final String str) {
        ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.AssemblePushHelper.1
            @Override // java.lang.Runnable
            public void run() {
                String strSubstring;
                if (TextUtils.isEmpty(str)) {
                    return;
                }
                String[] strArrSplit = str.split(Constants.WAVE_SEPARATOR);
                int length = strArrSplit.length;
                int i = 0;
                while (true) {
                    strSubstring = "";
                    if (i >= length) {
                        break;
                    }
                    String str2 = strArrSplit[i];
                    if (!TextUtils.isEmpty(str2) && str2.startsWith("token:")) {
                        strSubstring = str2.substring(str2.indexOf(":") + 1);
                        break;
                    }
                    i++;
                }
                if (TextUtils.isEmpty(strSubstring)) {
                    MyLog.w("ASSEMBLE_PUSH : receive incorrect token");
                    return;
                }
                MyLog.w("ASSEMBLE_PUSH : receive correct token");
                AssemblePushHelper.saveAssemblePushToken(context, assemblePush, strSubstring);
                AssemblePushHelper.checkAssemblePushStatus(context);
            }
        });
    }

    private static void saveAssembleToken(AssemblePush assemblePush, String str) {
        synchronized (AssemblePushHelper.class) {
            try {
                String tokenKey = getTokenKey(assemblePush);
                if (TextUtils.isEmpty(tokenKey)) {
                    MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file");
                } else if (TextUtils.isEmpty(str)) {
                    MyLog.w("ASSEMBLE_PUSH : token is null");
                } else {
                    mTokens.put(tokenKey, str);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void unregisterAssemblePush(Context context) {
        AssemblePushCollectionsManager.getInstance(context).unregister();
    }

    public static void uploadToken(Context context, AssemblePush assemblePush, String str) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("mipush_extra", 0);
        String tokenKey = getTokenKey(assemblePush);
        if (TextUtils.isEmpty(tokenKey)) {
            MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file");
            return;
        }
        String string = sharedPreferences.getString(tokenKey, "");
        if (!TextUtils.isEmpty(string) && str.equals(string)) {
            MyLog.w("ASSEMBLE_PUSH : do not need to send token");
            return;
        }
        MyLog.w("ASSEMBLE_PUSH : send token upload");
        saveAssembleToken(assemblePush, str);
        RetryType retryType = AssemblePushInfoHelper.getRetryType(assemblePush);
        if (retryType == null) {
            return;
        }
        PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, retryType, assemblePush);
    }
}
