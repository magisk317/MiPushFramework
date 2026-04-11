package com.xiaomi.push.service

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.android.SharedPreferenceManager
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ByteUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.Arrays

object TinyDataStorage {
    const val TINY_DATA_BYTE_MAX_SIZE = 10240
    private const val TINY_DATA_CACHE_DEBUG_FILE_NAME = "tiny_data_debug.txt"
    const val TINY_DATA_CACHE_FILE_LOCK = "tiny_data.lock"
    const val TINY_DATA_CACHE_FILE_NAME = "tiny_data.data"
    @JvmField val mTinyDataLock4Thread: Any = Any()

    @JvmStatic
    fun cacheTinyData(context: Context, item: ClientUploadDataItem) {
        if (!TinyDataHelper.shouldUpload(item.pkgName)) {
            return
        }
        ScheduledJobManager.getInstance(context).addOneShootJob {
            synchronized(mTinyDataLock4Thread) {
                var randomAccessFile: RandomAccessFile? = null
                var fileLock: FileLock? = null
                try {
                    val lockFile = File(context.filesDir, TINY_DATA_CACHE_FILE_LOCK)
                    IOUtils.createFileQuietly(lockFile)
                    randomAccessFile = RandomAccessFile(lockFile, "rw")
                    fileLock = randomAccessFile.channel.lock()
                    writeTinyData2File(context, item)
                } catch (e: Exception) {
                    MyLog.e(e)
                } finally {
                    if (fileLock != null) {
                        try {
                            if (fileLock.isValid) {
                                fileLock.release()
                            }
                        } catch (e: IOException) {
                            MyLog.e(e)
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile)
                }
            }
        }
    }

    @JvmStatic
    fun getTinyDataKeyWithDefault(context: Context): ByteArray {
        var value = SharedPreferenceManager.getInstance(context)
            .getStringValue(PushConstants.SP_NAME_MIPUSH, PushConstants.SP_KEY_TINY_DATA_KEY, "")
        if (TextUtils.isEmpty(value)) {
            value = XMStringUtils.generateRandomString(20)
            SharedPreferenceManager.getInstance(context)
                .setStringnValue(PushConstants.SP_NAME_MIPUSH, PushConstants.SP_KEY_TINY_DATA_KEY, value)
        }
        return parseKey(value)
    }

    private fun parseKey(key: String): ByteArray {
        val copied = Arrays.copyOf(Base64Coder.decode(key), 16)
        copied[0] = 68
        copied[15] = 84
        return copied
    }

    private fun writeTinyData2File(context: Context, item: ClientUploadDataItem) {
        var output: BufferedOutputStream? = null
        try {
            val encrypted = DataCryptUtils.mipushEncrypt(
                getTinyDataKeyWithDefault(context),
                XmPushThriftSerializeUtils.convertThriftObjectToBytes(item),
            )
            if (encrypted == null || encrypted.isEmpty()) {
                MyLog.w("TinyData write to cache file failed case encryption fail item:${item.id}   ts:${System.currentTimeMillis()}")
                return
            }
            if (encrypted.size > TINY_DATA_BYTE_MAX_SIZE) {
                MyLog.w("TinyData write to cache file failed case too much data content item:${item.id}   ts:${System.currentTimeMillis()}")
                return
            }
            output = BufferedOutputStream(FileOutputStream(File(context.filesDir, TINY_DATA_CACHE_FILE_NAME), true))
            output.write(ByteUtils.parseInt(encrypted.size))
            output.write(encrypted)
            output.flush()
        } catch (e: IOException) {
            MyLog.e("TinyData write to cache file failed cause io exception item:${item.id}", e)
        } catch (e: Exception) {
            MyLog.e("TinyData write to cache file  failed item:${item.id}", e)
        } finally {
            IOUtils.closeQuietly(output)
        }
    }
}
