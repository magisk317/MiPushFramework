package com.xiaomi.channel.commonutils.file

import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import android.util.Log
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.File

object SDCardUtils {
    @JvmStatic
    fun getSDCardAvailableBytes(): Long {
        val externalStorageDirectory = Environment.getExternalStorageDirectory()
        if (!isSDCardBusy() || externalStorageDirectory == null || TextUtils.isEmpty(externalStorageDirectory.path)) {
            return 0L
        }
        return try {
            val statFs = StatFs(externalStorageDirectory.path)
            (statFs.availableBlocksLong - 4L) * statFs.blockSizeLong
        } catch (t: Throwable) {
            0L
        }
    }

    @JvmStatic
    fun getSDCardPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    @JvmStatic
    fun isSDCardBusy(): Boolean {
        return try {
            Environment.getExternalStorageState() != "mounted"
        } catch (e: Exception) {
            Log.e("XMPush-", "check SDCard is busy: $e")
            true
        }
    }

    @JvmStatic
    fun isSDCardFull(): Boolean {
        return getSDCardAvailableBytes() <= 102400
    }

    @JvmStatic
    fun isSDCardUnavailable(): Boolean {
        return try {
            Environment.getExternalStorageState() == "removed"
        } catch (e: Exception) {
            MyLog.e(e)
            true
        }
    }

    @JvmStatic
    fun isSDCardUseful(): Boolean {
        return !isSDCardBusy() && !isSDCardFull() && !isSDCardUnavailable()
    }
}
