package com.xiaomi.channel.commonutils.msa;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MdidIdManager.class */
class MdidIdManager implements IdManager, InvocationHandler {
    private static final String CORE_CLASS_MDID = "com.bun.miitmdid.core.MdidSdk";
    private static final String[][] LISTENER_SUPPLIER_CLASS_ARRAY = {new String[]{"com.bun.supplier.IIdentifierListener", "com.bun.supplier.IdSupplier"}, new String[]{"com.bun.miitmdid.core.IIdentifierListener", "com.bun.miitmdid.supplier.IdSupplier"}};
    private static final String LOG_PREFIX = "mdid:";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int TIME_WAIT_LOCK = 3000;
    private Context mContext;
    private Class mClassMdid = null;
    private Class mClassIIdentifierListener = null;
    private Method mMethodInitSdk = null;
    private Method mMethodGetUDID = null;
    private Method mMethodGetOAID = null;
    private Method mMethodGetVAID = null;
    private Method mMethodGetAAID = null;
    private Method mMethodIsSupported = null;
    private Method mMethodShutDown = null;
    private final Object mLockObj = new Object();
    private volatile int mRetryCount = 0;
    private volatile long mGettingOrGotTime = 0;
    private volatile IdData mIdData = null;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MdidIdManager$IdData.class */
    private class IdData {
        String aaid;
        Boolean isSupport;
        String oaid;
        String udid;
        String vaid;

        private IdData() {
            this.isSupport = null;
            this.udid = null;
            this.oaid = null;
            this.vaid = null;
            this.aaid = null;
        }

        boolean reviseSelf() {
            boolean z = true;
            if (!TextUtils.isEmpty(this.udid) || !TextUtils.isEmpty(this.oaid) || !TextUtils.isEmpty(this.vaid) || !TextUtils.isEmpty(this.aaid)) {
                this.isSupport = true;
            }
            if (this.isSupport == null) {
                z = false;
            }
            return z;
        }
    }

    public MdidIdManager(Context context) {
        this.mContext = context.getApplicationContext();
        initClass(context);
        callInitSdk(context);
    }

    private void callInitSdk(Context context) {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long j = -jElapsedRealtime;
        Class cls = this.mClassIIdentifierListener;
        long j2 = j;
        if (cls != null) {
            try {
                ClassLoader classLoader = cls.getClassLoader();
                ClassLoader classLoader2 = classLoader;
                if (classLoader == null) {
                    classLoader2 = context.getClassLoader();
                }
                invokeMethod(this.mMethodInitSdk, this.mClassMdid.newInstance(), context, Proxy.newProxyInstance(classLoader2, new Class[]{this.mClassIIdentifierListener}, this));
                j2 = jElapsedRealtime;
            } catch (Throwable th) {
                outLog("call init sdk error:" + th);
                j2 = j;
            }
        }
        this.mGettingOrGotTime = j2;
    }

    private void cancelWait() {
        synchronized (this.mLockObj) {
            try {
                this.mLockObj.notifyAll();
            } catch (Exception e) {
            }
        }
    }

    private static Method getMethod(Class<?> cls, String str, Class<?>... clsArr) {
        if (cls == null) {
            return null;
        }
        try {
            return cls.getMethod(str, clsArr);
        } catch (Throwable th) {
            return null;
        }
    }

    private void initClass(Context context) {
        Class<?> clsLoadClass = loadClass(context, CORE_CLASS_MDID);
        Class<?> clsLoadClass2 = null;
        Class<?> clsLoadClass3 = null;
        int i = 0;
        while (true) {
            String[][] strArr = LISTENER_SUPPLIER_CLASS_ARRAY;
            if (i >= strArr.length) {
                break;
            }
            String[] strArr2 = strArr[i];
            clsLoadClass2 = loadClass(context, strArr2[0]);
            clsLoadClass3 = loadClass(context, strArr2[1]);
            if (clsLoadClass2 != null && clsLoadClass3 != null) {
                outLog("found class in index " + i);
                break;
            }
            i++;
        }
        this.mClassMdid = clsLoadClass;
        this.mMethodInitSdk = getMethod(clsLoadClass, "InitSdk", Context.class, clsLoadClass2);
        this.mClassIIdentifierListener = clsLoadClass2;
        this.mMethodGetUDID = getMethod(clsLoadClass3, "getUDID", new Class[0]);
        this.mMethodGetOAID = getMethod(clsLoadClass3, "getOAID", new Class[0]);
        this.mMethodGetVAID = getMethod(clsLoadClass3, "getVAID", new Class[0]);
        this.mMethodGetAAID = getMethod(clsLoadClass3, "getAAID", new Class[0]);
        this.mMethodIsSupported = getMethod(clsLoadClass3, "isSupported", new Class[0]);
        this.mMethodShutDown = getMethod(clsLoadClass3, "shutDown", new Class[0]);
    }

    private static <T> T invokeMethod(Method method, Object obj, Object... objArr) {
        if (method == null) {
            return null;
        }
        try {
            T t = (T) method.invoke(obj, objArr);
            if (t != null) {
                return t;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static boolean isPrimitive(Object obj) {
        return (obj instanceof Boolean) || (obj instanceof Character) || (obj instanceof Byte) || (obj instanceof Short) || (obj instanceof Integer) || (obj instanceof Long) || (obj instanceof Float) || (obj instanceof Double);
    }

    private static Class<?> loadClass(Context context, String str) {
        try {
            return SystemUtils.loadClass(context, str);
        } catch (Throwable th) {
            return null;
        }
    }

    private static void outLog(String str) {
        MyLog.w(LOG_PREFIX + str);
    }

    private void waitAndGettingIfNeed(String str) {
        if (this.mIdData != null) {
            return;
        }
        long j = this.mGettingOrGotTime;
        long jElapsedRealtime = SystemClock.elapsedRealtime() - Math.abs(j);
        int i = this.mRetryCount;
        long j2 = j;
        long jElapsedRealtime2 = jElapsedRealtime;
        if (jElapsedRealtime > 3000) {
            j2 = j;
            jElapsedRealtime2 = jElapsedRealtime;
            if (i < 3) {
                synchronized (this.mLockObj) {
                    j2 = j;
                    jElapsedRealtime2 = jElapsedRealtime;
                    if (this.mGettingOrGotTime == j) {
                        j2 = j;
                        jElapsedRealtime2 = jElapsedRealtime;
                        if (this.mRetryCount == i) {
                            outLog("retry, current count is " + i);
                            this.mRetryCount = this.mRetryCount + 1;
                            callInitSdk(this.mContext);
                            j2 = this.mGettingOrGotTime;
                            jElapsedRealtime2 = SystemClock.elapsedRealtime() - Math.abs(j2);
                        }
                    }
                }
            }
        }
        if (this.mIdData != null || j2 < 0 || jElapsedRealtime2 > 3000 || Looper.myLooper() == Looper.getMainLooper()) {
            return;
        }
        synchronized (this.mLockObj) {
            if (this.mIdData == null) {
                try {
                    outLog(str + " wait...");
                    this.mLockObj.wait(3000L);
                } catch (Exception e) {
                }
            }
        }
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getAAID() {
        waitAndGettingIfNeed("getAAID");
        return this.mIdData == null ? null : this.mIdData.aaid;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getOAID() {
        waitAndGettingIfNeed("getOAID");
        return this.mIdData == null ? null : this.mIdData.oaid;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getUDID() {
        waitAndGettingIfNeed("getUDID");
        return this.mIdData == null ? null : this.mIdData.udid;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getVAID() {
        waitAndGettingIfNeed("getVAID");
        return this.mIdData == null ? null : this.mIdData.vaid;
    }

    @Override // java.lang.reflect.InvocationHandler
    public Object invoke(Object obj, Method method, Object[] objArr) {
        this.mGettingOrGotTime = SystemClock.elapsedRealtime();
        if (objArr != null) {
            IdData idData = new IdData();
            int length = objArr.length;
            boolean z = false;
            int i = 0;
            while (true) {
                if (i >= length) {
                    break;
                }
                Object obj2 = objArr[i];
                if (obj2 != null && !isPrimitive(obj2)) {
                    idData.udid = (String) invokeMethod(this.mMethodGetUDID, obj2, new Object[0]);
                    idData.oaid = (String) invokeMethod(this.mMethodGetOAID, obj2, new Object[0]);
                    idData.vaid = (String) invokeMethod(this.mMethodGetVAID, obj2, new Object[0]);
                    idData.aaid = (String) invokeMethod(this.mMethodGetAAID, obj2, new Object[0]);
                    idData.isSupport = (Boolean) invokeMethod(this.mMethodIsSupported, obj2, new Object[0]);
                    invokeMethod(this.mMethodShutDown, obj2, new Object[0]);
                    if (idData.reviseSelf()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("has get succ, check duplicate:");
                        if (this.mIdData != null) {
                            z = true;
                        }
                        sb.append(z);
                        outLog(sb.toString());
                        synchronized (MdidIdManager.class) {
                            try {
                                if (this.mIdData == null) {
                                    this.mIdData = idData;
                                }
                            } catch (Throwable th) {
                                throw th;
                            }
                        }
                    }
                }
                i++;
            }
        }
        cancelWait();
        return null;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isAllowOAID() {
        return true;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isSupported() {
        waitAndGettingIfNeed("isSupported");
        return this.mIdData != null && Boolean.TRUE.equals(this.mIdData.isSupport);
    }
}
