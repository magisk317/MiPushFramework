package com.xiaomi.clientreport.util
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.clientreport.data.ClientReportConstants
import com.xiaomi.clientreport.manager.ClientReportLogicManager
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.Locale

object ClientReportUtil {
    fun getEventKeyWithDefault(context: Context): String {
        var stringValue = SPManager.getInstance(context).getStringValue(
            ClientReportConstants.SP_FILE_STATUS,
            ClientReportConstants.SP_KEY_KEY,
            "",
        ) ?: ""
        if (stringValue.isEmpty()) {
            stringValue = XMStringUtils.generateRandomString(20)
            SPManager.getInstance(context).setStringnValue(
                ClientReportConstants.SP_FILE_STATUS,
                ClientReportConstants.SP_KEY_KEY,
                stringValue,
            )
        }
        return stringValue
    }

    val os: String
        get() = "${android.os.Build.VERSION.RELEASE}-${android.os.Build.VERSION.INCREMENTAL}"

    fun getReadFileName(context: Context, str: String): Array<File>? {
        val externalFilesDir = context.getExternalFilesDir(str)
        if (externalFilesDir != null) {
            return externalFilesDir.listFiles { _, str2 ->
                str2.isNotEmpty() && !str2.lowercase(Locale.getDefault()).endsWith(".lock")
            }
        }
        return null
    }

    fun isFileCanBeUse(context: Context, str: String): Boolean {
        val file = File(str)
        val maxFileLength = ClientReportLogicManager.getInstance(context).config.maxFileLength
        return if (file.exists()) {
            try {
                file.length() <= maxFileLength
            } catch (e: Exception) {
                MyLog.e(e)
                false
            }
        } else {
            IOUtils.createFileQuietly(file)
            true
        }
    }

    fun isSupportXMSFUpload(context: Context): Boolean {
        return try {
            val packageInfo = context.applicationContext.packageManager
                .getPackageInfo("com.xiaomi.xmsf", 0)
            getVersionCode(packageInfo) >= 108
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            false
        }
    }

    fun moveFiles(context: Context, str: String, str2: String) {
        val externalFilesDir = context.getExternalFilesDir(str)
        val externalFilesDir2 = context.getExternalFilesDir(str2)
        if (externalFilesDir == null || externalFilesDir2 == null || !externalFilesDir.exists()) {
            return
        }
        if (!externalFilesDir2.exists()) {
            externalFilesDir2.mkdirs()
        }
        val listFiles = externalFilesDir.listFiles { _, str3 ->
            str3.isNotEmpty() && !str3.lowercase(Locale.getDefault()).endsWith(".lock")
        }
        if (listFiles.isNullOrEmpty()) return
        for (file in listFiles) {
            if (file != null && file.exists() && file.isFile) {
                val file2 = File("${file.absolutePath}.lock")
                var randomAccessFile: RandomAccessFile? = null
                var fileLock: FileLock? = null
                try {
                    IOUtils.createFileQuietly(file2)
                    randomAccessFile = RandomAccessFile(file2, "rw")
                    fileLock = randomAccessFile.channel.lock()
                    var file3 = File(externalFilesDir2, file.name)
                    var i = 0
                    while (file3.exists()) {
                        file3 = File(externalFilesDir2, "${file.name}_$i")
                        i++
                    }
                    if (!file.renameTo(file3)) {
                        IOUtils.copyFile(file, file3)
                        file.delete()
                    }
                } catch (e: Exception) {
                    MyLog.e(e)
                } finally {
                    if (fileLock != null && fileLock.isValid) {
                        try {
                            fileLock.release()
                        } catch (e: IOException) {
                            MyLog.e(e)
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile)
                    if (file2.exists()) {
                        file2.delete()
                    }
                }
            }
        }
    }

    fun parseKey(str: String): ByteArray {
        val bArrCopyOf = Base64Coder.decode(str).copyOf(16)
        bArrCopyOf[0] = 68
        bArrCopyOf[15] = 84
        return bArrCopyOf
    }

    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return try {
            (PackageInfo::class.java.getMethod("getLongVersionCode")
                .invoke(packageInfo) as Number).toLong()
        } catch (unused: ReflectiveOperationException) {
            try {
                (PackageInfo::class.java.getField("versionCode")
                    .get(packageInfo) as Number).toLong()
            } catch (e: ReflectiveOperationException) {
                MyLog.e(e)
                0L
            }
        }
    }

    fun sendData(context: Context, str: String) {
        val intent = Intent(ClientReportConstants.XMSF_UPLOAD)
        intent.putExtra("pkgname", context.packageName)
        intent.putExtra("category", "category_client_report_data")
        intent.putExtra("name", ClientReportConstants.NAME)
        intent.putExtra("data", str)
        context.sendBroadcast(intent, ClientReportConstants.XMSF_UPLOAD_PERMISSION)
    }

    fun sendFile(context: Context, list: List<String>) {
        if (list.isNullOrEmpty() || !isSupportXMSFUpload(context)) return
        for (str in list) {
            if (str.isNotEmpty()) {
                sendData(context, str)
            }
        }
    }
}
