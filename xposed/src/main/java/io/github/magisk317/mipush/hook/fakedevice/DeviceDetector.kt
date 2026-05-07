package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build

/**
 * 设备类型检测工具
 */
object DeviceDetector {
    private var isXiaomiDeviceCached: Boolean? = null
    
    /**
     * 检测当前设备是否为小米/红米/POCO品牌
     */
    fun isXiaomiDevice(): Boolean {
        if (isXiaomiDeviceCached == null) {
            isXiaomiDeviceCached = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                                   Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                                   Build.BRAND.equals("Redmi", ignoreCase = true) ||
                                   Build.BRAND.equals("POCO", ignoreCase = true) ||
                                   Build.BRAND.equals("Blackshark", ignoreCase = true)
        }
        return isXiaomiDeviceCached!!
    }
    
    /**
     * 检测是否为MIUI/HyperOS系统
     */
    fun isMiuiSystem(): Boolean {
        return try {
            val versionName = getSystemProperty("ro.miui.ui.version.name")
            val versionCode = getSystemProperty("ro.miui.ui.version.code")
            !versionName.isNullOrEmpty() || !versionCode.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
}
