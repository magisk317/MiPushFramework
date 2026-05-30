package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import java.io.File
import java.io.FileOutputStream

/*
 * Local legacy crash storage helper retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class CrashStorage private constructor(private val mContext: Context) {
    companion object {
        const val CRASH_FILE_PATH = "/crash"
        private val mCrashFileLock = Any()

        @Volatile
        private var sInstance: CrashStorage? = null

        fun getInstance(context: Context): CrashStorage {
            return sInstance ?: synchronized(this) {
                sInstance ?: CrashStorage(context).also { sInstance = it }
            }
        }
    }

    private fun getCrashFileBySummary(summary: String): File? {
        val file = File(mContext.filesDir.path + CRASH_FILE_PATH)
        if (!file.exists()) {
            file.mkdirs()
            return null
        }
        file.listFiles()?.forEach {
            if (it.isFile && it.name.startsWith(summary)) {
                return it
            }
        }
        return null
    }

    fun getAllCrashFile(): ArrayList<File> {
        val arrayList = ArrayList<File>()
        val file = File(mContext.filesDir.path + CRASH_FILE_PATH)
        if (!file.exists()) {
            file.mkdirs()
            return arrayList
        }
        file.listFiles()?.forEach { crashFile ->
            val strArrSplit = crashFile.name.split(":")
            if (strArrSplit.size >= 2 && strArrSplit[1].toInt() >= 1 && crashFile.isFile) {
                arrayList.add(crashFile)
            }
        }
        return arrayList
    }

    fun getCrashSummary(file: File?): String {
        if (file == null) return ""
        val strArrSplit = file.name.split(":")
        return if (strArrSplit.size != 2) "" else strArrSplit[0]
    }

    fun writeCrash2File(crashContent: String, summary: String) {
        if (TextUtils.isEmpty(crashContent) || TextUtils.isEmpty(summary)) return
        synchronized(mCrashFileLock) {
            val crashFile = getCrashFileBySummary(summary)
            if (crashFile != null) {
                val strArrSplit = crashFile.name.split(":")
                if (strArrSplit.size < 2) return
                crashFile.renameTo(
                    File(mContext.filesDir.path + CRASH_FILE_PATH + "/" + summary + ":" + (strArrSplit[1].toInt() + 1))
                )
            } else {
                var fos: FileOutputStream? = null
                try {
                    val sb = StringBuilder()
                    sb.append(mContext.filesDir)
                    sb.append(CRASH_FILE_PATH)
                    sb.append("/")
                    sb.append(summary)
                    sb.append(":")
                    sb.append("1")
                    fos = FileOutputStream(File(sb.toString()))
                    fos.write(crashContent.toByteArray())
                    fos.flush()
                } catch (e: Exception) {
                    MyLog.e(e)
                } finally {
                    IOUtils.closeQuietly(fos)
                }
            }
        }
    }
}
