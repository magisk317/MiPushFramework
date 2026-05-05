package com.xiaomi.push.mpcd

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/u9/b.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/CDActionProviderHolder.java
 * Stock holder is obfuscated as u9.b and stores com.xiaomi.mipush.sdk.i; this file keeps the deobfuscated CDActionProvider holder API.
 */
class CDActionProviderHolder {
    private var mCDActionProvider: CDActionProvider? = null

    fun getCDActionProvider(): CDActionProvider? = mCDActionProvider

    fun setCDActionProvider(cDActionProvider: CDActionProvider?) {
        mCDActionProvider = cDActionProvider
    }

    companion object {
        @Volatile
        private var sInstance: CDActionProviderHolder? = null

        @JvmStatic
        fun getInstance(): CDActionProviderHolder {
            if (sInstance == null) {
                synchronized(CDActionProviderHolder::class.java) {
                    if (sInstance == null) {
                        sInstance = CDActionProviderHolder()
                    }
                }
            }
            return sInstance!!
        }
    }
}
