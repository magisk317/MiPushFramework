package com.xiaomi.channel.commonutils.file

import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object FileUtils {
    private val mFileTypes = hashMapOf(
        "FFD8FF" to "jpg",
        "89504E47" to "png",
        "47494638" to "gif",
        "474946" to "gif",
        "424D" to "bmp"
    )

    private fun bytesToHexString(bArr: ByteArray): String? {
        if (bArr.isEmpty()) return null
        val sb = StringBuilder()
        for (b in bArr) {
            val upperCase = Integer.toHexString(b.toInt() and 255).uppercase()
            if (upperCase.length < 2) {
                sb.append(0)
            }
            sb.append(upperCase)
        }
        return sb.toString()
    }

    private fun getFileHeader(path: String): String? {
        var fileInputStream: FileInputStream? = null
        return try {
            fileInputStream = FileInputStream(path)
            val bArr = ByteArray(3)
            if (fileInputStream.read(bArr, 0, bArr.size) <= 0) {
                null
            } else {
                bytesToHexString(bArr)
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                fileInputStream?.close()
            } catch (e2: IOException) {
                // ignore
            }
        }
    }

    @JvmStatic
    fun getFileType(path: String): String? {
        return mFileTypes[getFileHeader(path)]
    }

    @JvmStatic
    fun getFolderSize(file: File): Long {
        var size = 0L
        try {
            val files = file.listFiles() ?: return 0L
            for (f in files) {
                size += if (f.isDirectory) {
                    getFolderSize(f)
                } else {
                    f.length()
                }
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
        return size
    }

    @JvmStatic
    fun isGif(path: String): Boolean {
        return "gif" == getFileType(path)
    }
}
