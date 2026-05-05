package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/SharedPreferenceManager.java
 */
class SharedPreferenceManager private constructor(private val mContext: Context) {
    private val mHandler = Handler(Looper.getMainLooper())
    private var mCaches: MutableMap<String, MutableMap<String, String>>? = HashMap()

    @Synchronized
    private fun getDataFromCache(str: String?, str2: String?): String? {
        if (mCaches != null && !TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2)) {
            return try {
                val map = mCaches!![str]
                map?.get(str2) ?: ""
            } catch (throwable: Throwable) {
                ""
            }
        }
        return ""
    }

    @Synchronized
    private fun putData2Cache(str: String, str2: String, str3: String?) {
        if (mCaches == null) {
            mCaches = HashMap()
        }
        var map = mCaches!![str]
        if (map == null) {
            map = HashMap()
        }
        map[str2] = str3 ?: ""
        mCaches!![str] = map
    }

    @Synchronized
    fun getBooleanValue(str: String, str2: String, z: Boolean): Boolean {
        return try {
            val dataFromCache = getDataFromCache(str, str2)
            if (TextUtils.isEmpty(dataFromCache)) {
                mContext.getSharedPreferences(str, 4).getBoolean(str2, z)
            } else {
                dataFromCache.toBoolean()
            }
        } catch (throwable: Throwable) {
            z
        }
    }

    @Synchronized
    fun getStringValue(str: String, str2: String, str3: String): String {
        val dataFromCache = getDataFromCache(str, str2)
        if (!TextUtils.isEmpty(dataFromCache)) {
            return dataFromCache.orEmpty()
        }
        return mContext.getSharedPreferences(str, 4).getString(str2, str3) ?: str3
    }

    @Synchronized
    fun setBooleanValue(str: String, str2: String, bool: Boolean) {
        putData2Cache(str, str2, bool.toString())
        mHandler.post {
            val editor = mContext.getSharedPreferences(str, 4).edit()
            editor.putBoolean(str2, bool)
            editor.commit()
        }
    }

    @Synchronized
    fun setStringnValue(str: String, str2: String, str3: String?) {
        putData2Cache(str, str2, str3)
        mHandler.post {
            val editor = mContext.getSharedPreferences(str, 4).edit()
            editor.putString(str2, str3)
            editor.commit()
        }
    }

    companion object {
        @Volatile
        private var sInstance: SharedPreferenceManager? = null

        @JvmStatic
        fun getInstance(context: Context): SharedPreferenceManager {
            if (sInstance == null) {
                synchronized(SharedPreferenceManager::class.java) {
                    if (sInstance == null) {
                        sInstance = SharedPreferenceManager(context)
                    }
                }
            }
            return sInstance!!
        }
    }
}
