package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AssetUtils {
    @JvmStatic
    fun extractAssetFile(context: Context, assetName: String, outputPath: String): Boolean {
        var fileInputStream: FileInputStream? = null
        var fileOutputStream: FileOutputStream? = null
        return try {
            val assetBytes = context.assets.open(assetName).use { IOUtils.readInputStream(it) } ?: return false
            val file = File(outputPath)
            if (file.exists()) {
                fileInputStream = FileInputStream(file)
                val existingBytes = IOUtils.readInputStream(fileInputStream)
                val md5 = if (existingBytes == null) null else XMStringUtils.getMd5(existingBytes)
                val assetMd5 = XMStringUtils.getMd5(assetBytes)
                if (!TextUtils.isEmpty(md5) && md5 == assetMd5) {
                    return false
                }
            }
            fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(assetBytes)
            fileOutputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            IOUtils.closeQuietly(fileInputStream)
            IOUtils.closeQuietly(fileOutputStream)
        }
    }
}
