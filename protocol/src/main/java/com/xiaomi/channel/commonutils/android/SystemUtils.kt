package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.os.Build
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog

object SystemUtils {
    private const val DEFAULT_MIID = "0"
    @Volatile
    private var cachedMOSVersion: String? = null
    
    @JvmStatic
    var context: Context? = null
        private set

    private fun getCOSVersion(): String? {
        val str = SystemProperties.get("ro.build.version.opporom", "")
        if (!TextUtils.isEmpty(str) && !str.startsWith("ColorOS_")) {
            cachedMOSVersion = "ColorOS_$str"
        }
        return cachedMOSVersion
    }

    private fun getEMUIVersion(): String {
        val str = SystemProperties.get("ro.build.version.emui", "")
        cachedMOSVersion = str
        return str
    }

    private fun getFOSVersion(): String? {
        val str = SystemProperties.get("ro.vivo.os.version", "")
        if (!TextUtils.isEmpty(str) && !str.startsWith("FuntouchOS_")) {
            cachedMOSVersion = "FuntouchOS_$str"
        }
        return cachedMOSVersion
    }

    @JvmStatic
    fun getMIID(context: Context): String {
        return DEFAULT_MIID
    }

    @JvmStatic
    fun getMIUIType(): Int {
        return 0
    }

    @JvmStatic
    fun getManufacturerOSVersion(): String? {
        synchronized(SystemUtils::class.java) {
            var str = cachedMOSVersion
            if (str != null) {
                return str
            }
            val str2 = Build.VERSION.INCREMENTAL
            var eMUIVersion = str2
            if (getMIUIType() <= 0) {
                eMUIVersion = getEMUIVersion()
                if (TextUtils.isEmpty(eMUIVersion)) {
                    eMUIVersion = getCOSVersion()
                    if (TextUtils.isEmpty(eMUIVersion)) {
                        eMUIVersion = getFOSVersion()
                        if (TextUtils.isEmpty(eMUIVersion)) {
                            eMUIVersion = (SystemProperties.get("ro.product.brand", "Android") + "_" + str2)
                        }
                    }
                }
            }
            cachedMOSVersion = eMUIVersion
            return eMUIVersion
        }
    }

    @JvmStatic
    fun initialize(context: Context) {
        SystemUtils.context = context.applicationContext
    }

    @JvmStatic
    fun isBootCompleted(): Boolean {
        return TextUtils.equals(SystemProperties.get("sys.boot_completed"), "1")
    }

    @JvmStatic
    fun isDebuggable(context: Context): Boolean {
        return try {
            (context.applicationInfo.flags and 2) != 0
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }

    @JvmStatic
    fun isGlobalVersion(): Boolean {
        return false
    }

    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun loadClass(context: Context?, str: String?): Class<*> {
        if (str.isNullOrBlank()) {
            throw ClassNotFoundException("class is empty")
        }
        val hasContext = context != null
        if (context != null && Build.VERSION.SDK_INT >= 29) {
            try {
                return context.classLoader.loadClass(str)
            } catch (e: ClassNotFoundException) {
            }
        }
        return try {
            Class.forName(str)
        } catch (e: ClassNotFoundException) {
             MyLog.w("loadClass fail hasContext= $hasContext, errMsg = ${e.localizedMessage}")
            throw e
        }
    }
}
