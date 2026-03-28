package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushConstants;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AppInfoHolder.class */
public class AppInfoHolder {
    private static final String PREF_KEY_APP_ID = "appId";
    private static final String PREF_KEY_APP_REGION = "appRegion";
    private static final String PREF_KEY_APP_TOKEN = "appToken";
    private static final String PREF_KEY_DEVICE_ID = "devId";
    private static final String PREF_KEY_ENV_TYPE = "envType";
    private static final String PREF_KEY_HYBRID_APP_INFO_PREFIX = "hybrid_app_info_";
    private static final String PREF_KEY_PAUSED = "paused";
    private static final String PREF_KEY_REG_ID = "regId";
    private static final String PREF_KEY_REG_RESOURCE = "regResource";
    private static final String PREF_KEY_REG_SECRET = "regSec";
    private static final String PREF_KEY_VALID = "valid";
    private static final String PREF_KEY_VERSION_NAME = "vName";
    private static volatile AppInfoHolder sInstance;
    String appRegRequestId;
    private Context mContext;
    private Map<String, ClientInfoData> mHybridAppInfoCache;
    private ClientInfoData mInfoData;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AppInfoHolder$ClientInfoData.class */
    public static class ClientInfoData {
        public String appID;
        public String appRegion;
        public String appToken;
        public String deviceId;
        private Context mContext;
        public String regID;
        public String regResource;
        public String regSecret;
        public String versionName;
        public boolean isValid = true;
        public boolean isPaused = false;
        public int envType = 1;

        public ClientInfoData(Context context) {
            this.mContext = context;
        }

        private String getVersionName() {
            Context context = this.mContext;
            return AppInfoUtils.getVersionName(context, context.getPackageName());
        }

        public static ClientInfoData parseClientInfoData(Context context, String str) {
            try {
                JSONObject jSONObject = new JSONObject(str);
                ClientInfoData clientInfoData = new ClientInfoData(context);
                clientInfoData.appID = jSONObject.getString("appId");
                clientInfoData.appToken = jSONObject.getString(AppInfoHolder.PREF_KEY_APP_TOKEN);
                clientInfoData.regID = jSONObject.getString(AppInfoHolder.PREF_KEY_REG_ID);
                clientInfoData.regSecret = jSONObject.getString(AppInfoHolder.PREF_KEY_REG_SECRET);
                clientInfoData.deviceId = jSONObject.getString(AppInfoHolder.PREF_KEY_DEVICE_ID);
                clientInfoData.versionName = jSONObject.getString(AppInfoHolder.PREF_KEY_VERSION_NAME);
                clientInfoData.isValid = jSONObject.getBoolean(AppInfoHolder.PREF_KEY_VALID);
                clientInfoData.isPaused = jSONObject.getBoolean(AppInfoHolder.PREF_KEY_PAUSED);
                clientInfoData.envType = jSONObject.getInt(AppInfoHolder.PREF_KEY_ENV_TYPE);
                clientInfoData.regResource = jSONObject.getString(AppInfoHolder.PREF_KEY_REG_RESOURCE);
                return clientInfoData;
            } catch (Throwable th) {
                MyLog.e(th);
                return null;
            }
        }

        public static String toString(ClientInfoData clientInfoData) {
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("appId", clientInfoData.appID);
                jSONObject.put(AppInfoHolder.PREF_KEY_APP_TOKEN, clientInfoData.appToken);
                jSONObject.put(AppInfoHolder.PREF_KEY_REG_ID, clientInfoData.regID);
                jSONObject.put(AppInfoHolder.PREF_KEY_REG_SECRET, clientInfoData.regSecret);
                jSONObject.put(AppInfoHolder.PREF_KEY_DEVICE_ID, clientInfoData.deviceId);
                jSONObject.put(AppInfoHolder.PREF_KEY_VERSION_NAME, clientInfoData.versionName);
                jSONObject.put(AppInfoHolder.PREF_KEY_VALID, clientInfoData.isValid);
                jSONObject.put(AppInfoHolder.PREF_KEY_PAUSED, clientInfoData.isPaused);
                jSONObject.put(AppInfoHolder.PREF_KEY_ENV_TYPE, clientInfoData.envType);
                jSONObject.put(AppInfoHolder.PREF_KEY_REG_RESOURCE, clientInfoData.regResource);
                return jSONObject.toString();
            } catch (Throwable th) {
                MyLog.e(th);
                return null;
            }
        }

        public void clear() {
            AppInfoHolder.getSharedPreferences(this.mContext).edit().clear().commit();
            this.appID = null;
            this.appToken = null;
            this.regID = null;
            this.regSecret = null;
            this.deviceId = null;
            this.versionName = null;
            this.isValid = false;
            this.isPaused = false;
            this.appRegion = null;
            this.envType = 1;
        }

        public void invalidate() {
            this.isValid = false;
            AppInfoHolder.getSharedPreferences(this.mContext).edit().putBoolean(AppInfoHolder.PREF_KEY_VALID, this.isValid).commit();
        }

        public boolean isVaild() {
            return isVaild(this.appID, this.appToken);
        }

        public boolean isVaild(String str, String str2) {
            return TextUtils.equals(this.appID, str) && TextUtils.equals(this.appToken, str2) && !TextUtils.isEmpty(this.regID) && !TextUtils.isEmpty(this.regSecret) && (TextUtils.equals(this.deviceId, DeviceInfo.getInstanceId(this.mContext)) || TextUtils.equals(this.deviceId, DeviceInfo.getSimpleDeviceId(this.mContext)));
        }

        public void setEnvType(int i) {
            this.envType = i;
        }

        public void setHybridIdAndTokenAndPackage(String str, String str2, String str3) {
            this.appID = str;
            this.appToken = str2;
            this.regResource = str3;
        }

        public void setHybridRegIdAndSecret(String str, String str2) {
            this.regID = str;
            this.regSecret = str2;
            this.deviceId = DeviceInfo.getInstanceId(this.mContext);
            this.versionName = getVersionName();
            this.isValid = true;
        }

        public void setIdAndToken(String str, String str2, String str3) {
            this.appID = str;
            this.appToken = str2;
            this.regResource = str3;
            SharedPreferences.Editor editorEdit = AppInfoHolder.getSharedPreferences(this.mContext).edit();
            editorEdit.putString("appId", this.appID);
            editorEdit.putString(AppInfoHolder.PREF_KEY_APP_TOKEN, str2);
            editorEdit.putString(AppInfoHolder.PREF_KEY_REG_RESOURCE, str3);
            editorEdit.commit();
        }

        public void setPaused(boolean z) {
            this.isPaused = z;
        }

        public void setRegIdAndSecret(String str, String str2, String str3) {
            this.regID = str;
            this.regSecret = str2;
            this.deviceId = DeviceInfo.getInstanceId(this.mContext);
            this.versionName = getVersionName();
            this.isValid = true;
            this.appRegion = str3;
            SharedPreferences.Editor editorEdit = AppInfoHolder.getSharedPreferences(this.mContext).edit();
            editorEdit.putString(AppInfoHolder.PREF_KEY_REG_ID, str);
            editorEdit.putString(AppInfoHolder.PREF_KEY_REG_SECRET, str2);
            editorEdit.putString(AppInfoHolder.PREF_KEY_DEVICE_ID, this.deviceId);
            editorEdit.putString(AppInfoHolder.PREF_KEY_VERSION_NAME, getVersionName());
            editorEdit.putBoolean(AppInfoHolder.PREF_KEY_VALID, true);
            editorEdit.putString(AppInfoHolder.PREF_KEY_APP_REGION, str3);
            editorEdit.commit();
        }
    }

    private AppInfoHolder(Context context) {
        this.mContext = context;
        init();
    }

    public static AppInfoHolder getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppInfoHolder.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new AppInfoHolder(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PushConstants.SP_NAME_MIPUSH, 0);
    }

    private void init() {
        this.mInfoData = new ClientInfoData(this.mContext);
        this.mHybridAppInfoCache = new HashMap();
        SharedPreferences sharedPreferences = getSharedPreferences(this.mContext);
        this.mInfoData.appID = sharedPreferences.getString("appId", null);
        this.mInfoData.appToken = sharedPreferences.getString(PREF_KEY_APP_TOKEN, null);
        this.mInfoData.regID = sharedPreferences.getString(PREF_KEY_REG_ID, null);
        this.mInfoData.regSecret = sharedPreferences.getString(PREF_KEY_REG_SECRET, null);
        this.mInfoData.deviceId = sharedPreferences.getString(PREF_KEY_DEVICE_ID, null);
        if (!TextUtils.isEmpty(this.mInfoData.deviceId) && DeviceInfo.startsWithDevPrefix(this.mInfoData.deviceId)) {
            this.mInfoData.deviceId = DeviceInfo.getInstanceId(this.mContext);
            sharedPreferences.edit().putString(PREF_KEY_DEVICE_ID, this.mInfoData.deviceId).commit();
        }
        this.mInfoData.versionName = sharedPreferences.getString(PREF_KEY_VERSION_NAME, null);
        this.mInfoData.isValid = sharedPreferences.getBoolean(PREF_KEY_VALID, true);
        this.mInfoData.isPaused = sharedPreferences.getBoolean(PREF_KEY_PAUSED, false);
        this.mInfoData.envType = sharedPreferences.getInt(PREF_KEY_ENV_TYPE, 1);
        this.mInfoData.regResource = sharedPreferences.getString(PREF_KEY_REG_RESOURCE, null);
        this.mInfoData.appRegion = sharedPreferences.getString(PREF_KEY_APP_REGION, null);
    }

    public boolean appRegistered() {
        return this.mInfoData.isVaild();
    }

    public boolean appRegistered(String str, String str2) {
        return this.mInfoData.isVaild(str, str2);
    }

    public boolean checkAppInfo() {
        if (this.mInfoData.isVaild()) {
            return true;
        }
        MyLog.w("Don't send message before initialization succeeded!");
        return false;
    }

    public boolean checkVersionNameChanged() {
        Context context = this.mContext;
        return !TextUtils.equals(AppInfoUtils.getVersionName(context, context.getPackageName()), this.mInfoData.versionName);
    }

    public void clear() {
        this.mInfoData.clear();
    }

    public void delHybridAppInfo(String str) {
        this.mHybridAppInfoCache.remove(str);
        getSharedPreferences(this.mContext).edit().remove(PREF_KEY_HYBRID_APP_INFO_PREFIX + str).commit();
    }

    public String getAppID() {
        return this.mInfoData.appID;
    }

    public String getAppRegion() {
        return this.mInfoData.appRegion;
    }

    public String getAppToken() {
        return this.mInfoData.appToken;
    }

    public int getEnvType() {
        return this.mInfoData.envType;
    }

    public ClientInfoData getHybridAppInfo(String str) {
        if (this.mHybridAppInfoCache.containsKey(str)) {
            return this.mHybridAppInfoCache.get(str);
        }
        String str2 = PREF_KEY_HYBRID_APP_INFO_PREFIX + str;
        SharedPreferences sharedPreferences = getSharedPreferences(this.mContext);
        if (!sharedPreferences.contains(str2)) {
            return null;
        }
        ClientInfoData clientInfoData = ClientInfoData.parseClientInfoData(this.mContext, sharedPreferences.getString(str2, ""));
        this.mHybridAppInfoCache.put(str2, clientInfoData);
        return clientInfoData;
    }

    public String getRegID() {
        return this.mInfoData.regID;
    }

    public String getRegResource() {
        return this.mInfoData.regResource;
    }

    public String getRegSecret() {
        return this.mInfoData.regSecret;
    }

    public void invalidate() {
        this.mInfoData.invalidate();
    }

    public boolean invalidated() {
        return !this.mInfoData.isValid;
    }

    public boolean isHybridAppRegistered(String str, String str2, String str3) {
        ClientInfoData hybridAppInfo = getHybridAppInfo(str3);
        return hybridAppInfo != null && TextUtils.equals(str, hybridAppInfo.appID) && TextUtils.equals(str2, hybridAppInfo.appToken);
    }

    public boolean isPaused() {
        return this.mInfoData.isPaused;
    }

    public void putAppIDAndToken(String str, String str2, String str3) {
        this.mInfoData.setIdAndToken(str, str2, str3);
    }

    public void putRegIDAndSecret(String str, String str2, String str3) {
        this.mInfoData.setRegIdAndSecret(str, str2, str3);
    }

    public void saveHybridAppInfo(String str, ClientInfoData clientInfoData) {
        this.mHybridAppInfoCache.put(str, clientInfoData);
        getSharedPreferences(this.mContext).edit().putString(PREF_KEY_HYBRID_APP_INFO_PREFIX + str, ClientInfoData.toString(clientInfoData)).commit();
    }

    public void setEnvType(int i) {
        this.mInfoData.setEnvType(i);
        getSharedPreferences(this.mContext).edit().putInt(PREF_KEY_ENV_TYPE, i).commit();
    }

    public void setPaused(boolean z) {
        this.mInfoData.setPaused(z);
        getSharedPreferences(this.mContext).edit().putBoolean(PREF_KEY_PAUSED, z).commit();
    }

    public void updateVersionName(String str) {
        SharedPreferences.Editor editorEdit = getSharedPreferences(this.mContext).edit();
        editorEdit.putString(PREF_KEY_VERSION_NAME, str);
        editorEdit.commit();
        this.mInfoData.versionName = str;
    }
}
