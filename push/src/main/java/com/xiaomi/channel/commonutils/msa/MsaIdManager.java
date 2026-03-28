package com.xiaomi.channel.commonutils.msa;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MsaIdManager.class */
public class MsaIdManager implements IdManager {
    public static final String KEY_AAID = "aaid";
    public static final String KEY_OAID = "oaid";
    public static final String KEY_TYPE = "oaid_type";
    public static final String KEY_UDID = "udid";
    public static final String KEY_VAID = "vaid";
    private static final int SHORT_STRING_LENGTH = 5;
    private static volatile MsaIdManager sInstance;
    private IdManager mIdManager;
    private int mManagerType = MsaIdFactory.sType;

    private MsaIdManager(Context context) {
        this.mIdManager = MsaIdFactory.instance(context);
        MyLog.w("create id manager is: " + this.mManagerType);
    }

    public static MsaIdManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (MsaIdManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new MsaIdManager(context.getApplicationContext());
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    private String substringShort(String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        String strSubstring = str;
        if (str.length() > 5) {
            strSubstring = str.substring(str.length() - 5);
        }
        return strSubstring;
    }

    private String trim(String str) {
        if (str == null) {
            str = "";
        }
        return str;
    }

    public void fillData(Map<String, String> map) {
        if (map == null) {
            return;
        }
        String udid = getUDID();
        if (!TextUtils.isEmpty(udid)) {
            map.put(KEY_UDID, udid);
        }
        String oaid = getOAID();
        if (!TextUtils.isEmpty(oaid)) {
            map.put(KEY_OAID, oaid);
        }
        String vaid = getVAID();
        if (!TextUtils.isEmpty(vaid)) {
            map.put(KEY_VAID, vaid);
        }
        String aaid = getAAID();
        if (!TextUtils.isEmpty(aaid)) {
            map.put(KEY_AAID, aaid);
        }
        map.put(KEY_TYPE, String.valueOf(this.mManagerType));
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getAAID() {
        return trim(this.mIdManager.getAAID());
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getOAID() {
        return trim(this.mIdManager.getOAID());
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getUDID() {
        return trim(this.mIdManager.getUDID());
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getVAID() {
        return trim(this.mIdManager.getVAID());
    }

    public void init() {
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isAllowOAID() {
        return this.mIdManager.isAllowOAID();
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isSupported() {
        return this.mIdManager.isSupported();
    }

    public String toShortString() {
        return "t:" + this.mManagerType + " s:" + isSupported() + " d:" + substringShort(getUDID()) + " | " + substringShort(getOAID()) + " | " + substringShort(getVAID()) + " | " + substringShort(getAAID());
    }
}
