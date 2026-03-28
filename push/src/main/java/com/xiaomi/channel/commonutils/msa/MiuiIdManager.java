package com.xiaomi.channel.commonutils.msa;

import android.content.Context;
import android.provider.Settings;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MiuiIdManager.class */
class MiuiIdManager implements IdManager {
    private static final String CLASS_NAME = "com.android.id.impl.IdProviderImpl";
    private static final String KEY_ALLOW_OAID_USED = "allow_oaid_used";
    private static final String METHOD_AAID = "getAAID";
    private static final String METHOD_OAID = "getOAID";
    private static final String METHOD_UDID = "getUDID";
    private static final String METHOD_VAID = "getVAID";
    private static final String MIUI_PLATFORM_PKG = "com.xiaomi.xmsf";
    private Class<?> mClass;
    private Context mContext;
    private Object mIdProivderImpl;
    private Method mGetUDID = null;
    private Method mGetOAID = null;
    private Method mGetVAID = null;
    private Method mGetAAID = null;

    public MiuiIdManager(Context context) {
        this.mContext = context;
        loadClass(context);
    }

    private String invokeMethod(Context context, Method method) {
        Object obj = this.mIdProivderImpl;
        if (obj == null || method == null) {
            return null;
        }
        try {
            Object objInvoke = method.invoke(obj, context);
            if (objInvoke != null) {
                return (String) objInvoke;
            }
            return null;
        } catch (Exception e) {
            MyLog.e("miui invoke error", e);
            return null;
        }
    }

    public static boolean isMiuiPhone(Context context) {
        return "com.xiaomi.xmsf".equals(context.getPackageName());
    }

    private void loadClass(Context context) {
        try {
            Class<?> clsLoadClass = SystemUtils.loadClass(context, CLASS_NAME);
            this.mClass = clsLoadClass;
            this.mIdProivderImpl = clsLoadClass.newInstance();
            this.mGetUDID = this.mClass.getMethod(METHOD_UDID, Context.class);
            this.mGetOAID = this.mClass.getMethod(METHOD_OAID, Context.class);
            this.mGetVAID = this.mClass.getMethod(METHOD_VAID, Context.class);
            this.mGetAAID = this.mClass.getMethod(METHOD_AAID, Context.class);
        } catch (Exception e) {
            MyLog.e("miui load class error", e);
        }
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getAAID() {
        return invokeMethod(this.mContext, this.mGetAAID);
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getOAID() {
        return invokeMethod(this.mContext, this.mGetOAID);
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getUDID() {
        return null;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getVAID() {
        return invokeMethod(this.mContext, this.mGetVAID);
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isAllowOAID() {
        boolean z = false;
        try {
            if (Settings.Secure.getInt(this.mContext.getContentResolver(), KEY_ALLOW_OAID_USED, 1) == 1) {
                z = true;
            }
            return z;
        } catch (Exception e) {
            MyLog.w("miui is allow oaid error" + e);
            return false;
        }
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isSupported() {
        return (this.mClass == null || this.mIdProivderImpl == null) ? false : true;
    }
}
