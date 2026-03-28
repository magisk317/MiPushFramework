package com.xiaomi.clientreport.util;

import android.content.Context;
import android.content.SharedPreferences;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/util/SPManager.class */
public class SPManager {
    private static volatile SPManager sInstance;
    private Context mContext;

    private SPManager(Context context) {
        this.mContext = context;
    }

    public static SPManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SPManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new SPManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public long getLongValue(String str, String str2, long j) {
        long j2;
        synchronized (this) {
            try {
                j2 = this.mContext.getSharedPreferences(str, 4).getLong(str2, j);
            } catch (Throwable th) {
                return j;
            }
        }
        return j2;
    }

    public String getStringValue(String str, String str2, String str3) {
        String string;
        synchronized (this) {
            try {
                string = this.mContext.getSharedPreferences(str, 4).getString(str2, str3);
            } catch (Throwable th) {
                return str3;
            }
        }
        return string;
    }

    public void setLongValue(String str, String str2, long j) {
        synchronized (this) {
            SharedPreferences.Editor editorEdit = this.mContext.getSharedPreferences(str, 4).edit();
            editorEdit.putLong(str2, j);
            editorEdit.commit();
        }
    }

    public void setStringnValue(String str, String str2, String str3) {
        synchronized (this) {
            SharedPreferences.Editor editorEdit = this.mContext.getSharedPreferences(str, 4).edit();
            editorEdit.putString(str2, str3);
            editorEdit.commit();
        }
    }
}
