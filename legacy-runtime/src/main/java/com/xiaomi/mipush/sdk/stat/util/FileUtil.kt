package com.xiaomi.mipush.sdk.stat.util

import android.content.Context
import android.os.Build
import android.system.Os
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import java.io.File

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/util/FileUtil.java
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
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
