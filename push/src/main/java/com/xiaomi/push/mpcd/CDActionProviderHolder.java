package com.xiaomi.push.mpcd;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/CDActionProviderHolder.class */
public class CDActionProviderHolder {
    private static volatile CDActionProviderHolder sInstance;
    private CDActionProvider mCDActionProvider;

    public static CDActionProviderHolder getInstance() {
        if (sInstance == null) {
            synchronized (CDActionProviderHolder.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new CDActionProviderHolder();
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public CDActionProvider getCDActionProvider() {
        return this.mCDActionProvider;
    }

    public void setCDActionProvider(CDActionProvider cDActionProvider) {
        this.mCDActionProvider = cDActionProvider;
    }
}
