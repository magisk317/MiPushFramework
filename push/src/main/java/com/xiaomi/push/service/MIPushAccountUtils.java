package com.xiaomi.push.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.android.Region;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.channel.commonutils.msa.MsaIdManager;
import com.xiaomi.channel.commonutils.network.HttpResponse;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.smack.ConnectionConfiguration;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushAccountUtils.class */
public class MIPushAccountUtils {
    private static final String MIPUSH_ACCOUNT_HOST_P = "register.xmpush.xiaomi.com";
    private static final String MIPUSH_ACCOUNT_HOST_S = "sandbox.xmpush.xiaomi.com";
    private static final String MIPUSH_CHINA_ACCOUNT_HOST_P = "cn.register.xmpush.xiaomi.com";
    private static final String MIPUSH_EURPOSE_ACCOUNT_HOST_P = "fr.register.xmpush.global.xiaomi.com";
    private static final String MIPUSH_GLOBAL_ACCOUNT_HOST_P = "register.xmpush.global.xiaomi.com";
    private static final String MIPUSH_INDIA_ACCOUNT_HOST_P = "idmb.register.xmpush.global.xiaomi.com";
    public static final String MIPUSH_MIUI_APPID = "1000271";
    public static final String MIPUSH_MIUI_APP_TOKEN = "420100086271";
    private static final String MIPUSH_RUSSIA_ACCOUNT_HOST_P = "ru.register.xmpush.global.xiaomi.com";
    private static final String PREF_KEY_ACCOUNT = "uuid";
    private static final String PREF_KEY_APP_ID = "app_id";
    private static final String PREF_KEY_APP_TOKEN = "app_token";
    private static final String PREF_KEY_DEVICE_ID = "device_id";
    private static final String PREF_KEY_ENV_TYPE = "env_type";
    private static final String PREF_KEY_GAID = "gaid";
    private static final String PREF_KEY_PACKAGENAME = "package_name";
    private static final String PREF_KEY_SECURITY = "security";
    private static final String PREF_KEY_TOKEN = "token";
    private static final String PREF_NAME = "mipush_account";
    private static PushAccountChangeListener accountChangeListener;
    private static MIPushAccount sAccount;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushAccountUtils$PushAccountChangeListener.class */
    public interface PushAccountChangeListener {
        void onChange();
    }

    public static void clearAccount(Context context) {
        context.getSharedPreferences(PREF_NAME, 0).edit().clear().commit();
        sAccount = null;
        notifyAccountChange();
    }

    public static String getAccountURL(Context context) {
        String region = AppRegionStorage.getInstance(context).getRegion();
        if (BuildSettings.IsOneBoxBuild()) {
            return "http://" + ConnectionConfiguration.XMPP_SERVER_HOST_ONEBOX + ":9085/pass/v2/register";
        }
        if (Region.China.name().equals(region)) {
            return "https://cn.register.xmpush.xiaomi.com/pass/v2/register";
        }
        if (Region.Global.name().equals(region)) {
            return "https://register.xmpush.global.xiaomi.com/pass/v2/register";
        }
        if (Region.Europe.name().equals(region)) {
            return "https://fr.register.xmpush.global.xiaomi.com/pass/v2/register";
        }
        if (Region.Russia.name().equals(region)) {
            return "https://ru.register.xmpush.global.xiaomi.com/pass/v2/register";
        }
        if (Region.India.name().equals(region)) {
            return "https://idmb.register.xmpush.global.xiaomi.com/pass/v2/register";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("https://");
        sb.append(BuildSettings.IsSandBoxBuild() ? "sandbox.xmpush.xiaomi.com" : MIPUSH_ACCOUNT_HOST_P);
        sb.append("/pass/v2/register");
        return sb.toString();
    }

    public static MIPushAccount getMIPushAccount(Context context) {
        synchronized (MIPushAccountUtils.class) {
            try {
                MIPushAccount mIPushAccount = sAccount;
                if (mIPushAccount != null) {
                    return mIPushAccount;
                }
                SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, 0);
                String string = sharedPreferences.getString("uuid", null);
                String string2 = sharedPreferences.getString("token", null);
                String string3 = sharedPreferences.getString("security", null);
                String string4 = sharedPreferences.getString("app_id", null);
                String string5 = sharedPreferences.getString("app_token", null);
                String string6 = sharedPreferences.getString("package_name", null);
                String string7 = sharedPreferences.getString("device_id", null);
                int i = sharedPreferences.getInt(PREF_KEY_ENV_TYPE, 1);
                String simpleDeviceId = string7;
                if (!TextUtils.isEmpty(string7)) {
                    simpleDeviceId = string7;
                    if (DeviceInfo.startsWithDevPrefix(string7)) {
                        simpleDeviceId = DeviceInfo.getSimpleDeviceId(context);
                        sharedPreferences.edit().putString("device_id", simpleDeviceId).commit();
                    }
                }
                if (TextUtils.isEmpty(string) || TextUtils.isEmpty(string2) || TextUtils.isEmpty(string3)) {
                    return null;
                }
                String simpleDeviceId2 = DeviceInfo.getSimpleDeviceId(context);
                if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.getPackageName()) && !TextUtils.isEmpty(simpleDeviceId2) && !TextUtils.isEmpty(simpleDeviceId) && !simpleDeviceId.equals(simpleDeviceId2)) {
                    MyLog.w("read_phone_state permission changes.");
                }
                MIPushAccount mIPushAccount2 = new MIPushAccount(string, string2, string3, string4, string5, string6, i);
                sAccount = mIPushAccount2;
                return mIPushAccount2;
            } finally {
            }
        }
    }

    private static boolean isMIUIPush(Context context) {
        return context.getPackageName().equals(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
    }

    public static void notifyAccountChange() {
        PushAccountChangeListener pushAccountChangeListener = accountChangeListener;
        if (pushAccountChangeListener != null) {
            pushAccountChangeListener.onChange();
        }
    }

    public static void persist(Context context, MIPushAccount mIPushAccount) {
        SharedPreferences.Editor editorEdit = context.getSharedPreferences(PREF_NAME, 0).edit();
        editorEdit.putString("uuid", mIPushAccount.account);
        editorEdit.putString("security", mIPushAccount.security);
        editorEdit.putString("token", mIPushAccount.token);
        editorEdit.putString("app_id", mIPushAccount.appId);
        editorEdit.putString("package_name", mIPushAccount.packageName);
        editorEdit.putString("app_token", mIPushAccount.appToken);
        editorEdit.putString("device_id", DeviceInfo.getSimpleDeviceId(context));
        editorEdit.putInt(PREF_KEY_ENV_TYPE, mIPushAccount.envType);
        editorEdit.commit();
        notifyAccountChange();
    }

    public static MIPushAccount register(Context context, String str, String str2, String str3) throws JSONException, IOException {
        synchronized (MIPushAccountUtils.class) {
            Map<String, String> treeMap = new TreeMap<>();
            String deviceId = DeviceInfo.getDeviceId(context, false);
            MyLog.w("account register:" + deviceId + " mim:" + MsaIdManager.getInstance(context).toShortString());
            treeMap.put("devid", deviceId);
            String str4 = null;
            MIPushAccount mIPushAccount = sAccount;
            if (mIPushAccount != null && !TextUtils.isEmpty(mIPushAccount.account)) {
                treeMap.put("uuid", sAccount.account);
                int iLastIndexOf = sAccount.account.lastIndexOf("/");
                if (iLastIndexOf != -1) {
                    str4 = sAccount.account.substring(iLastIndexOf + 1);
                }
            }
            MsaIdManager.getInstance(context).fillData(treeMap);
            String virtDevId = DeviceInfo.getVirtDevId(context);
            if (!TextUtils.isEmpty(virtDevId)) {
                treeMap.put("vdevid", virtDevId);
            }
            String gaid = DeviceInfo.getGaid(context);
            if (!TextUtils.isEmpty(gaid)) {
                treeMap.put(PREF_KEY_GAID, gaid);
            }
            if (isMIUIPush(context)) {
                str2 = MIPUSH_MIUI_APPID;
                str3 = MIPUSH_MIUI_APP_TOKEN;
            }
            String str5 = isMIUIPush(context) ? PushConstants.PUSH_SERVICE_PACKAGE_NAME : str;
            treeMap.put("appid", str2);
            treeMap.put("apptoken", str3);
            String str6 = String.valueOf(AppInfoUtils.getVersionCode(context, str5));
            treeMap.put("appversion", str6);
            treeMap.put("sdkversion", Integer.toString(30709));
            treeMap.put("packagename", str5);
            treeMap.put("model", Build.MODEL);
            treeMap.put("board", Build.BOARD);
            if (!MIUIUtils.isGlobalRegion()) {
                String str7 = "";
                String strBlockingGetIMEI = DeviceInfo.blockingGetIMEI(context);
                if (!TextUtils.isEmpty(strBlockingGetIMEI)) {
                    str7 = "" + XMStringUtils.getMd5Digest(strBlockingGetIMEI);
                }
                String strBlockingGetSubIMEISMd5 = DeviceInfo.blockingGetSubIMEISMd5(context);
                if (!TextUtils.isEmpty(str7) && !TextUtils.isEmpty(strBlockingGetSubIMEISMd5)) {
                    str7 = str7 + "," + strBlockingGetSubIMEISMd5;
                }
                if (!TextUtils.isEmpty(str7)) {
                    treeMap.put("imei_md5", str7);
                }
            }
            treeMap.put("os", Build.VERSION.RELEASE + "-" + Build.VERSION.INCREMENTAL);
            int spaceId = DeviceInfo.getSpaceId();
            if (spaceId >= 0) {
                treeMap.put("space_id", Integer.toString(spaceId));
            }
            treeMap.put("brand", Build.BRAND + "");
            treeMap.put("ram", DeviceInfo.getRamSize());
            treeMap.put("rom", DeviceInfo.getRomSize());
            HttpResponse httpResponseDoHttpPost = Network.doHttpPost(context, getAccountURL(context), treeMap);
            String responseString = httpResponseDoHttpPost != null ? httpResponseDoHttpPost.getResponseString() : "";
            if (!TextUtils.isEmpty(responseString)) {
                JSONObject jSONObject = new JSONObject(responseString);
                if (jSONObject.getInt("code") == 0) {
                    JSONObject jSONObject2 = jSONObject.getJSONObject("data");
                    String string = jSONObject2.getString("ssecurity");
                    String string2 = jSONObject2.getString("token");
                    String string3 = jSONObject2.getString("userId");
                    if (TextUtils.isEmpty(str4)) {
                        str4 = "an" + XMStringUtils.generateRandomString(6);
                    }
                    MIPushAccount mIPushAccount2 = new MIPushAccount(string3 + "@xiaomi.com/" + str4, string2, string, str2, str3, str5, BuildSettings.getEnvType());
                    persist(context, mIPushAccount2);
                    DeviceInfo.updateVirtDevId(context, jSONObject2.optString("vdevid"));
                    sAccount = mIPushAccount2;
                    return mIPushAccount2;
                }
                MIPushClientManager.notifyRegisterError(context, jSONObject.getInt("code"), jSONObject.optString("description"));
                MyLog.w(responseString);
            }
            return null;
        }
    }

    public static void setAccountChangeListener(PushAccountChangeListener pushAccountChangeListener) {
        accountChangeListener = pushAccountChangeListener;
    }
}
