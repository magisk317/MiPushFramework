package com.xiaomi.push.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.CollectionUtils;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.util.HashSet;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/OnlineConfig.class */
public class OnlineConfig {
    private static final String CUSTOM_OC_PREFIX = "custom_oc_";
    private static final String NORMAL_OC_PREFIX = "normal_oc_";
    private static volatile OnlineConfig instance;
    private HashSet<OCUpdateCallback> mCallbacks = new HashSet<>();
    protected SharedPreferences preferences;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/OnlineConfig$OCUpdateCallback.class */
    public static abstract class OCUpdateCallback implements Runnable {
        private String mDescription;
        private int mId;

        public OCUpdateCallback(int i, String str) {
            this.mId = i;
            this.mDescription = str;
        }

        public boolean equals(Object obj) {
            boolean z = false;
            if (!(obj instanceof OCUpdateCallback)) {
                return false;
            }
            if (this.mId == ((OCUpdateCallback) obj).mId) {
                z = true;
            }
            return z;
        }

        public int hashCode() {
            return this.mId;
        }

        protected abstract void onCallback();

        @Override // java.lang.Runnable
        public final void run() {
            onCallback();
        }
    }

    private OnlineConfig(Context context) {
        this.preferences = context.getSharedPreferences(PushConstants.SP_NAME_MIPUSH_OC, 0);
    }

    private String getCustomOcKey(int i) {
        return CUSTOM_OC_PREFIX + i;
    }

    public static OnlineConfig getInstance(Context context) {
        if (instance == null) {
            synchronized (OnlineConfig.class) {
                try {
                    if (instance == null) {
                        instance = new OnlineConfig(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return instance;
    }

    private String getNormalOcKey(int i) {
        return NORMAL_OC_PREFIX + i;
    }

    private void putConfig(SharedPreferences.Editor editor, Pair<Integer, Object> pair, String str) {
        if (pair.second instanceof Integer) {
            editor.putInt(str, ((Integer) pair.second).intValue());
            return;
        }
        if (pair.second instanceof Long) {
            editor.putLong(str, ((Long) pair.second).longValue());
            return;
        }
        if (!(pair.second instanceof String)) {
            if (pair.second instanceof Boolean) {
                editor.putBoolean(str, ((Boolean) pair.second).booleanValue());
            }
        } else {
            String str2 = (String) pair.second;
            if (str.equals(getNormalOcKey(ConfigKey.AppIsInstalledList.getValue()))) {
                editor.putString(str, Base64Coder.encodeString(str2));
            } else {
                editor.putString(str, str2);
            }
        }
    }

    public void addOCUpdateCallbacks(OCUpdateCallback oCUpdateCallback) {
        synchronized (this) {
            if (!this.mCallbacks.contains(oCUpdateCallback)) {
                this.mCallbacks.add(oCUpdateCallback);
            }
        }
    }

    public void clearCallbacks() {
        synchronized (this) {
            this.mCallbacks.clear();
        }
    }

    public boolean getBooleanValue(int i, boolean z) {
        String customOcKey = getCustomOcKey(i);
        if (this.preferences.contains(customOcKey)) {
            return this.preferences.getBoolean(customOcKey, false);
        }
        String normalOcKey = getNormalOcKey(i);
        return this.preferences.contains(normalOcKey) ? this.preferences.getBoolean(normalOcKey, false) : z;
    }

    public int getIntValue(int i, int i2) {
        String customOcKey = getCustomOcKey(i);
        if (this.preferences.contains(customOcKey)) {
            return this.preferences.getInt(customOcKey, 0);
        }
        String normalOcKey = getNormalOcKey(i);
        return this.preferences.contains(normalOcKey) ? this.preferences.getInt(normalOcKey, 0) : i2;
    }

    public long getLongValue(int i, long j) {
        String customOcKey = getCustomOcKey(i);
        if (this.preferences.contains(customOcKey)) {
            return this.preferences.getLong(customOcKey, 0L);
        }
        String normalOcKey = getNormalOcKey(i);
        return this.preferences.contains(normalOcKey) ? this.preferences.getLong(normalOcKey, 0L) : j;
    }

    public String getStringValue(int i, String str) {
        String customOcKey = getCustomOcKey(i);
        if (this.preferences.contains(customOcKey)) {
            return this.preferences.getString(customOcKey, null);
        }
        String normalOcKey = getNormalOcKey(i);
        return this.preferences.contains(normalOcKey) ? this.preferences.getString(normalOcKey, null) : str;
    }

    void runCallback() {
        MyLog.v("OC_Callback : receive new oc data");
        HashSet<OCUpdateCallback> hashSet = new HashSet();
        synchronized (this) {
            hashSet.addAll(this.mCallbacks);
        }
        for (OCUpdateCallback oCUpdateCallback : hashSet) {
            if (oCUpdateCallback != null) {
                oCUpdateCallback.run();
            }
        }
        hashSet.clear();
    }

    public void updateCustomConfigs(List<Pair<Integer, Object>> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        SharedPreferences.Editor editorEdit = this.preferences.edit();
        for (Pair<Integer, Object> pair : list) {
            if (pair.first != null) {
                String customOcKey = getCustomOcKey(((Integer) pair.first).intValue());
                if (pair.second == null) {
                    editorEdit.remove(customOcKey);
                } else {
                    putConfig(editorEdit, pair, customOcKey);
                }
            }
        }
        editorEdit.commit();
    }

    public void updateNormalConfigs(List<Pair<Integer, Object>> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        SharedPreferences.Editor editorEdit = this.preferences.edit();
        for (Pair<Integer, Object> pair : list) {
            if (pair.first != null && pair.second != null) {
                putConfig(editorEdit, pair, getNormalOcKey(((Integer) pair.first).intValue()));
            }
        }
        editorEdit.commit();
    }
}
