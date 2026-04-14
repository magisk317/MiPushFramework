package com.xiaomi.push.mpcd

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
