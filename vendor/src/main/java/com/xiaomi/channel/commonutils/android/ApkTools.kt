package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.text.TextUtils

object ApkTools {
    const val ARMEABI = "armeabi"

    @JvmStatic
    fun extractSo(context: Context, apkPath: String?, outputDir: String?) {
    }

    @JvmStatic
    fun getAbiList(context: Context): List<String> {
        val list = ArrayList<String>()
        val primaryAbi = getAppPrimaryAbi(context)
        if (!TextUtils.isEmpty(primaryAbi)) {
            list.add(primaryAbi!!)
        }
        val abi = SystemProperties.get("ro.product.cpu.abi", "")
        if (!TextUtils.isEmpty(abi)) {
            list.add(abi)
        }
        val abi2 = SystemProperties.get("ro.product.cpu.abi2", "")
        if (!TextUtils.isEmpty(abi2)) {
            list.add(abi2)
        }
        val abiList = SystemProperties.get("ro.product.cpu.abilist", "")
        if (!TextUtils.isEmpty(abiList)) {
            val parts = abiList.split(",")
            for (part in parts) {
                if (!TextUtils.isEmpty(part)) {
                    list.add(part)
                }
            }
        }
        list.add(ARMEABI)
        return list
    }

    @JvmStatic
    fun getAppPrimaryAbi(context: Context): String? {
        return try {
            val applicationInfo = context.applicationInfo
            val field = SystemUtils.loadClass(context, "android.content.pm.ApplicationInfo").getDeclaredField("primaryCpuAbi")
            field.isAccessible = true
            field.get(applicationInfo) as String?
        } catch (throwable: Throwable) {
            null
        }
    }

    private fun getZipAbi(str: String?): String {
        val parts = str?.split("/")
        return if (parts == null || parts.size <= 1) ARMEABI else parts[parts.size - 2]
    }

    private fun getZipName(str: String?): String? {
        val parts = str?.split("/")
        return if (parts == null || parts.isEmpty()) str else parts[parts.size - 1]
    }

    private fun indexOf(list: List<String>?, str: String?): Int {
        var i = 0
        while (list != null && i < list.size) {
            if (!TextUtils.isEmpty(str) && str.equals(list[i], ignoreCase = true)) {
                return i
            }
            i++
        }
        return -1
    }
}
