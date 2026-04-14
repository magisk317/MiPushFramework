package com.xiaomi.tinyData

import android.content.Context
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ByteUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.TinyDataStorage
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock

class TinyDataCacheReader private constructor() {
    private class TinyDataReadJob(
        private val mContext: Context,
        private val mUploader: TinyDataUploader?
    ) : Runnable {
        override fun run() {
            try {
                extractTinyData(mContext, mUploader)
            } catch (th: Throwable) {
                MyLog.e(th)
            }
        }
    }

    companion object {
        private const val TINY_DATA_MAX_UPLOAD_ITEM_COUNT = 8
        private const val TINY_DATA_READ_TEMP_FILE_DIR = "/tdReadTemp"
        private var mTinyDataJobIsRunning = false

        fun addTinyDataCacheReadJob(context: Context, tinyDataUploader: TinyDataUploader?) {
            ScheduledJobManager.getInstance(context).addOneShootJob(TinyDataReadJob(context, tinyDataUploader))
        }

        @Throws(Throwable::class)
        private fun extractTinyData(context: Context, tinyDataUploader: TinyDataUploader?) {
            if (mTinyDataJobIsRunning) {
                MyLog.w("TinyData extractTinyData is running")
                return
            }
            mTinyDataJobIsRunning = true
            val file = File(context.filesDir, TinyDataStorage.TINY_DATA_CACHE_FILE_NAME)
            if (!file.exists()) {
                MyLog.w("TinyData no ready file to get data.")
                return
            }
            verifyFileDir(context)
            val tinyDataKeyWithDefault = TinyDataStorage.getTinyDataKeyWithDefault(context)
            var randomAccessFile: RandomAccessFile? = null
            var fileLock: FileLock? = null
            try {
                val lockFile = File(context.filesDir, TinyDataStorage.TINY_DATA_CACHE_FILE_LOCK)
                IOUtils.createFileQuietly(lockFile)
                randomAccessFile = RandomAccessFile(lockFile, "rw")
                fileLock = randomAccessFile.channel.lock()
                val tempFile = File(context.filesDir.toString() + TINY_DATA_READ_TEMP_FILE_DIR + "/" + TinyDataStorage.TINY_DATA_CACHE_FILE_NAME)
                file.renameTo(tempFile)
            } catch (e: Exception) {
                MyLog.e(e)
            } finally {
                if (fileLock != null && fileLock.isValid) {
                    try {
                        fileLock.release()
                    } catch (e: java.io.IOException) {
                        MyLog.e(e)
                    }
                }
                IOUtils.closeQuietly(randomAccessFile)
            }
            try {
                val tempFile = File(context.filesDir.toString() + TINY_DATA_READ_TEMP_FILE_DIR + "/" + TinyDataStorage.TINY_DATA_CACHE_FILE_NAME)
                if (!tempFile.exists()) {
                    MyLog.w("TinyData no ready file to get data.")
                    return
                }
                readTinyDataFromFile(context, tinyDataUploader, tempFile, tinyDataKeyWithDefault)
                updateTinyDataUploadTimeStamp(context)
            } catch (th: Throwable) {
                throw th
            } finally {
                TinyDataCacheProcessor.setIsTinyDataExtracting(false)
                mTinyDataJobIsRunning = false
            }
        }

        @Throws(Throwable::class)
        private fun readTinyDataFromFile(
            context: Context,
            tinyDataUploader: TinyDataUploader?,
            file: File,
            key: ByteArray
        ) {
            var bufferedInputStream: BufferedInputStream? = null
            val arrayList = ArrayList<ClientUploadDataItem>(TINY_DATA_MAX_UPLOAD_ITEM_COUNT)
            try {
                bufferedInputStream = BufferedInputStream(FileInputStream(file))
                val lengthBuffer = ByteArray(4)
                while (true) {
                    val read = bufferedInputStream.read()
                    if (read == -1) break
                    lengthBuffer[0] = read.toByte()
                    if (bufferedInputStream.read(lengthBuffer, 1, 3) != 3) {
                        MyLog.e("TinyData read from cache file failed cause lengthBuffer error.")
                        break
                    }
                    val length = ByteUtils.toInt(lengthBuffer)
                    if (length < 1 || length > 10240) {
                        MyLog.e("TinyData read from cache file failed cause lengthBuffer < 1 || too big. length:$length")
                        break
                    }
                    val buffer = ByteArray(length)
                    var bytesRead = 0
                    while (bytesRead < length) {
                        val read2 = bufferedInputStream.read(buffer, bytesRead, length - bytesRead)
                        if (read2 == -1) break
                        bytesRead += read2
                    }
                    if (bytesRead != length) {
                        MyLog.e("TinyData read from cache file failed cause buffer size not equal length. size:${bytesRead}__length:$length")
                        break
                    }
                    try {
                        val decrypted = DataCryptUtils.mipushDecrypt(key, buffer)
                        val item = ClientUploadDataItem()
                        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(item, decrypted)
                        arrayList.add(item)
                        if (arrayList.size >= TINY_DATA_MAX_UPLOAD_ITEM_COUNT) {
                            TinyDataCacheUploader.uploadTinyData(context, tinyDataUploader, arrayList)
                            arrayList.clear()
                        }
                    } catch (e: Exception) {
                        MyLog.e(e)
                    }
                }
                if (arrayList.isNotEmpty()) {
                    TinyDataCacheUploader.uploadTinyData(context, tinyDataUploader, arrayList)
                }
            } finally {
                IOUtils.closeQuietly(bufferedInputStream)
                if (file.exists() && !file.delete()) {
                    MyLog.w("TinyData delete reading temp file failed")
                }
            }
        }

        private fun updateTinyDataUploadTimeStamp(context: Context) {
            context.getSharedPreferences("mipush_extra", 4).edit()
                .putLong(TinyDataCacheProcessor.LAST_TINY_DATA_UPLOAD_TIMESTAMP, System.currentTimeMillis() / 1000)
                .commit()
        }

        private fun verifyFileDir(context: Context) {
            val dir = File(context.filesDir.toString() + TINY_DATA_READ_TEMP_FILE_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
}
