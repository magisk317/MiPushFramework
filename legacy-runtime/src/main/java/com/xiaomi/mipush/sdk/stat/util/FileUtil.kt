package com.xiaomi.mipush.sdk.stat.util

import android.content.Context
import android.os.Build
import android.system.Os
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import java.io.File

object FileUtil {
    fun getFileSize(str: String): Long {
        var j: Long = 0
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                j = 0
                if (File(str).exists()) {
                    j = Os.stat(str).st_size
                }
            } catch (e: Exception) {
                MyLog.e(e)
                j = 0
            }
        }
        return j
    }

    fun getSuitableLimit(context: Context): Int {
        val intValue = OnlineConfig.getInstance(context)
            .getIntValue(ConfigKey.StatDataProcessFrequency.value, DataBaseConfig.DEFAULT_NUM)
        val jFreeMemory = Runtime.getRuntime().freeMemory() shr 20
        val j = if (jFreeMemory <= 0) 1L else jFreeMemory
        return (intValue.toLong() * j).toInt()
    }
}
