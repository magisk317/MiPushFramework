package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/SharedPreferenceManager.class */
public class SharedPreferenceManager {
    private static volatile SharedPreferenceManager sInstance;
    private Context mContext;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Map<String, Map<String, String>> mCaches = new HashMap<>();

    private SharedPreferenceManager(Context context) {
        this.mContext = context;
    }

    private String getDataFromCache(String str, String str2) {
        synchronized (this) {
            if (this.mCaches != null && !TextUtils.isEmpty(str)) {
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        Map<String, String> map = this.mCaches.get(str);
                        if (map == null) {
                            return "";
                        }
                        return map.get(str2);
                    } catch (Throwable th) {
                        return "";
                    }
                }
            }
            return "";
        }
    }

    public static SharedPreferenceManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SharedPreferenceManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new SharedPreferenceManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    private void putData2Cache(String str, String str2, String str3) {
        synchronized (this) {
            if (this.mCaches == null) {
                this.mCaches = new HashMap<>();
            }
            Map<String, String> map = this.mCaches.get(str);
            Map<String, String> map2 = map;
            if (map == null) {
                map2 = new HashMap<>();
            }
            map2.put(str2, str3);
            this.mCaches.put(str, map2);
        }
    }

    public boolean getBooleanValue(String str, String str2, boolean z) {
        synchronized (this) {
            try {
                String dataFromCache = getDataFromCache(str, str2);
                if (TextUtils.isEmpty(dataFromCache)) {
                    return this.mContext.getSharedPreferences(str, 4).getBoolean(str2, z);
                }
                return Boolean.parseBoolean(dataFromCache);
            } catch (Throwable th) {
                return z;
            }
        }
    }

    public String getStringValue(String str, String str2, String str3) {
        synchronized (this) {
            String dataFromCache = getDataFromCache(str, str2);
            if (!TextUtils.isEmpty(dataFromCache)) {
                return dataFromCache;
            }
            return this.mContext.getSharedPreferences(str, 4).getString(str2, str3);
        }
    }

    public void setBooleanValue(final String str, final String str2, final Boolean bool) {
        synchronized (this) {
            putData2Cache(str, str2, String.valueOf(bool));
            this.mHandler.post(new Runnable() { // from class: com.xiaomi.channel.commonutils.android.SharedPreferenceManager.1
                @Override // java.lang.Runnable
                public void run() {
                    SharedPreferences.Editor editorEdit = SharedPreferenceManager.this.mContext.getSharedPreferences(str, 4).edit();
                    editorEdit.putBoolean(str2, bool.booleanValue());
                    editorEdit.commit();
                }
            });
        }
    }

    public void setStringnValue(final String str, final String str2, final String str3) {
        synchronized (this) {
            putData2Cache(str, str2, str3);
            this.mHandler.post(new Runnable() { // from class: com.xiaomi.channel.commonutils.android.SharedPreferenceManager.2
                @Override // java.lang.Runnable
                public void run() {
                    SharedPreferences.Editor editorEdit = SharedPreferenceManager.this.mContext.getSharedPreferences(str, 4).edit();
                    editorEdit.putString(str2, str3);
                    editorEdit.commit();
                }
            });
        }
    }
}
