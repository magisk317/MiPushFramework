package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 */
object PreferenceUtils {
    private fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(context.packageName + "_preferences", 0)
    }

    @JvmStatic
    fun checkProcess(context: Context) {
    }

    @JvmStatic
    fun clearPreference(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit().clear().apply()
    }

    @JvmStatic
    fun dumpDefaultPreference(context: Context) {
        checkProcess(context)
        dumpPreference(getDefaultSharedPreferences(context), "default preference:")
    }

    @JvmStatic
    fun dumpDefaultPreference(context: Context, str: String) {
        dumpPreference(context.getSharedPreferences(str, 0), str)
    }

    private fun dumpPreference(sharedPreferences: SharedPreferences, str: String) {
        val sb = StringBuffer()
        sb.append(str).append("\n")
        val all = sharedPreferences.all
        for (key in all.keys) {
            sb.append(key).append(":").append(all[key]).append("\n")
        }
        MyLog.w(sb.toString())
    }

    @JvmStatic
    fun getSettingBoolean(context: Context, str: String, z: Boolean): Boolean {
        checkProcess(context)
        return getDefaultSharedPreferences(context).getBoolean(str, z)
    }

    @JvmStatic
    fun getSettingFloat(context: Context, str: String, f: Float): Float {
        checkProcess(context)
        return getDefaultSharedPreferences(context).getFloat(str, f)
    }

    @JvmStatic
    fun getSettingInt(context: Context, str: String, i: Int): Int {
        checkProcess(context)
        return getDefaultSharedPreferences(context).getInt(str, i)
    }

    @JvmStatic
    fun getSettingLong(context: Context, str: String, j: Long): Long {
        checkProcess(context)
        return getDefaultSharedPreferences(context).getLong(str, j)
    }

    @JvmStatic
    fun getSettingString(context: Context, str: String, str2: String): String? {
        checkProcess(context)
        return getDefaultSharedPreferences(context).getString(str, str2)
    }

    @JvmStatic
    fun hasKey(context: Context, str: String): Boolean {
        checkProcess(context)
        return getDefaultSharedPreferences(context).contains(str)
    }

    @JvmStatic
    fun increaseSettingInt(context: Context, str: String) {
        checkProcess(context)
        increaseSettingInt(getDefaultSharedPreferences(context), str)
    }

    @JvmStatic
    fun increaseSettingInt(sharedPreferences: SharedPreferences, str: String) {
        sharedPreferences.edit().putInt(str, sharedPreferences.getInt(str, 0) + 1).apply()
    }

    @JvmStatic
    fun increaseSettingInt(sharedPreferences: SharedPreferences, str: String, i: Int) {
        sharedPreferences.edit().putInt(str, sharedPreferences.getInt(str, 0) + i).apply()
    }

    @JvmStatic
    fun increaseSettingLong(sharedPreferences: SharedPreferences, str: String, j: Long) {
        sharedPreferences.edit().putLong(str, sharedPreferences.getLong(str, 0L) + j).apply()
    }

    @JvmStatic
    fun putNotNullExtra(map: MutableMap<String, String>?, str: String?, str2: String?) {
        if (map == null || str == null || str2 == null) {
            return
        }
        map[str] = str2
    }

    @JvmStatic
    fun removePreference(context: Context, str: String) {
        checkProcess(context)
        getDefaultSharedPreferences(context).edit().remove(str).apply()
    }

    @JvmStatic
    fun setSettingBoolean(context: Context, str: String, z: Boolean) {
        checkProcess(context)
        getDefaultSharedPreferences(context).edit().putBoolean(str, z).apply()
    }

    @JvmStatic
    fun setSettingFloat(context: Context, str: String, f: Float) {
        checkProcess(context)
        getDefaultSharedPreferences(context).edit().putFloat(str, f).apply()
    }

    @JvmStatic
    fun setSettingInt(context: Context, str: String, i: Int) {
        checkProcess(context)
        getDefaultSharedPreferences(context).edit().putInt(str, i).apply()
    }

    @JvmStatic
    fun setSettingInt(sharedPreferences: SharedPreferences, str: String, i: Int) {
        sharedPreferences.edit().putInt(str, i).apply()
    }

    @JvmStatic
    fun setSettingLong(context: Context, str: String, j: Long) {
        try {
            checkProcess(context)
            getDefaultSharedPreferences(context).edit().putLong(str, j).apply()
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    @JvmStatic
    fun setSettingString(context: Context, str: String, str2: String?) {
        checkProcess(context)
        getDefaultSharedPreferences(context).edit().putString(str, str2).apply()
    }
}
