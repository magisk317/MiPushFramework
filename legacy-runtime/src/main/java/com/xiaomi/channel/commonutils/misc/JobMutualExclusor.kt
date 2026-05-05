package com.xiaomi.channel.commonutils.misc

import android.content.Context
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlin.math.abs

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/misc/JobMutualExclusor.java
 */
object JobMutualExclusor {
    const val CHECK_FRACTION = 0.9f
    private const val INFO_SEPARATOR = ","
    private const val LAST_COLLECT_FILE_PATH = "lcfp"
    private const val LAST_COLLECT_FILE_PATH_LOCK = "lcfp.lock"
    private const val WRITE_FILE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"

    @JvmStatic
    fun checkPeriodAndRecordWithFileLock(context: Context, str: String?, period: Long): Boolean {
        if (Build.VERSION.SDK_INT >= 23 && !AppInfoUtils.checkSelfPermission(context, WRITE_FILE_PERMISSION)) {
            return true
        }
        var randomAccessFile: RandomAccessFile? = null
        var fileLock: FileLock? = null
        return try {
            val file = File(File(Environment.getExternalStorageDirectory(), DeviceInfo.VIRTUAL_DEVICE_DIR), LAST_COLLECT_FILE_PATH_LOCK)
            IOUtils.createFileQuietly(file)
            randomAccessFile = RandomAccessFile(file, "rw")
            fileLock = randomAccessFile.channel.lock()
            checkPeriodAndRecordWorking(context, str, period)
        } catch (e: IOException) {
            e.printStackTrace()
            true
        } finally {
            if (fileLock != null && fileLock!!.isValid) {
                try {
                    fileLock!!.release()
                } catch (e: IOException) {
                    // ignore
                }
            }
            IOUtils.closeQuietly(randomAccessFile)
        }
    }

    private fun checkPeriodAndRecordWorking(context: Context, str: String?, period: Long): Boolean {
        if (TextUtils.isEmpty(str) || period <= 0) {
            return true
        }
        val file = File(File(Environment.getExternalStorageDirectory(), DeviceInfo.VIRTUAL_DEVICE_DIR), LAST_COLLECT_FILE_PATH)
        IOUtils.createFileQuietly(file)
        val existing = IOUtils.fileToStr(file)
        val now = System.currentTimeMillis()
        val threshold = (period * CHECK_FRACTION).toLong()
        val packageName = context.packageName
        val lines = ArrayList<String>()
        var allowed = true
        var found = false
        if (!TextUtils.isEmpty(existing)) {
            for (line in existing!!.split("\n")) {
                if (TextUtils.isEmpty(line)) {
                    continue
                }
                val parts = line.split(INFO_SEPARATOR)
                if (parts.size < 3) {
                    continue
                }
                if (TextUtils.equals(str, parts[0])) {
                    found = true
                    try {
                        val last = parts[2].toLong()
                        if (abs(now - last) < threshold) {
                            allowed = false
                        }
                    } catch (e: NumberFormatException) {
                        MyLog.e(e)
                    }
                    if (allowed) {
                        lines.add(str + INFO_SEPARATOR + packageName + INFO_SEPARATOR + now)
                    } else {
                        lines.add(line)
                    }
                } else {
                    lines.add(line)
                }
            }
        }
        if (allowed && !found) {
            lines.add(str + INFO_SEPARATOR + packageName + INFO_SEPARATOR + now)
        }
        if (allowed) {
            IOUtils.strToFile(file, lines.filter { !TextUtils.isEmpty(it) }.joinToString("\n"))
        }
        return allowed
    }
}
