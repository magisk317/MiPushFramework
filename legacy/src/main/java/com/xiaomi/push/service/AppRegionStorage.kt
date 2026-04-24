package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock

class AppRegionStorage private constructor(
    private val context: Context,
) {
    @Volatile
    private var countryCode: String? = null

    @Volatile
    private var region: String? = null

    private val regionLock = Any()
    private val countryCodeLock = Any()

    private val regionCacheFileName = "mipush_region"
    private val countryCodeCacheFileName = "mipush_country_code"
    private val regionCacheFileLock = "mipush_region.lock"
    private val countryCodeCacheFileLock = "mipush_country_code.lock"

    companion object {
        @Volatile
        private var instance: AppRegionStorage? = null

        @JvmStatic
        fun getInstance(context: Context): AppRegionStorage {
            return instance ?: synchronized(AppRegionStorage::class.java) {
                instance ?: AppRegionStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun readFromFileWithLock(
        cacheFileName: String,
        lockFileName: String,
        monitor: Any,
    ): String? {
        val cacheFile = File(context.filesDir, cacheFileName)
        if (!cacheFile.exists()) {
            MyLog.w("No ready file to get data from $cacheFileName")
            return null
        }
        synchronized(monitor) {
            var randomAccessFile: RandomAccessFile? = null
            var fileLock: FileLock? = null
            return try {
                val lockFile = File(context.filesDir, lockFileName)
                IOUtils.createFileQuietly(lockFile)
                randomAccessFile = RandomAccessFile(lockFile, "rw")
                fileLock = randomAccessFile.channel.lock()
                IOUtils.fileToStr(cacheFile)
            } catch (e: Exception) {
                MyLog.e(e)
                null
            } finally {
                if (fileLock?.isValid == true) {
                    try {
                        fileLock.release()
                    } catch (e: IOException) {
                        MyLog.e(e)
                    }
                }
                IOUtils.closeQuietly(randomAccessFile)
            }
        }
    }

    private fun write2FileWithLock(
        content: String?,
        cacheFileName: String,
        lockFileName: String,
        monitor: Any,
    ) {
        synchronized(monitor) {
            var randomAccessFile: RandomAccessFile? = null
            var fileLock: FileLock? = null
            try {
                val lockFile = File(context.filesDir, lockFileName)
                IOUtils.createFileQuietly(lockFile)
                randomAccessFile = RandomAccessFile(lockFile, "rw")
                fileLock = randomAccessFile.channel.lock()
                IOUtils.strToFile(File(context.filesDir, cacheFileName), content)
            } catch (e: Exception) {
                MyLog.e(e)
            } finally {
                if (fileLock?.isValid == true) {
                    try {
                        fileLock.release()
                    } catch (e: IOException) {
                        MyLog.e(e)
                    }
                }
                IOUtils.closeQuietly(randomAccessFile)
            }
        }
    }

    fun getCountryCode(): String? {
        if (TextUtils.isEmpty(countryCode)) {
            countryCode = readFromFileWithLock(
                countryCodeCacheFileName,
                countryCodeCacheFileLock,
                countryCodeLock,
            )
        }
        return countryCode
    }

    fun getRegion(): String? {
        if (TextUtils.isEmpty(region)) {
            region = readFromFileWithLock(
                regionCacheFileName,
                regionCacheFileLock,
                regionLock,
            )
        }
        return region
    }

    fun setCountryCode(value: String?) {
        if (TextUtils.equals(value, countryCode)) {
            return
        }
        countryCode = value
        write2FileWithLock(
            countryCode,
            countryCodeCacheFileName,
            countryCodeCacheFileLock,
            countryCodeLock,
        )
    }

    fun setRegion(value: String?) {
        if (TextUtils.equals(value, region)) {
            return
        }
        region = value
        write2FileWithLock(
            region,
            regionCacheFileName,
            regionCacheFileLock,
            regionLock,
        )
    }
}
