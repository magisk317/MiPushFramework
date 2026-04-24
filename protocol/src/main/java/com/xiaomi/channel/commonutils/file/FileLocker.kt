package com.xiaomi.channel.commonutils.file

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.Collections
import java.util.HashSet

class FileLocker private constructor(private val mContext: Context) {
    private var mLock: FileLock? = null
    private var mLockFile: RandomAccessFile? = null
    private var mLockFileName: String? = null

    companion object {
        private const val LOCK = ".LOCK"
        private val LOCK_HELD: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())

        @JvmStatic
        @Throws(IOException::class)
        fun lock(context: Context, file: File): FileLocker {
            MyLog.v("Locking: ${file.absolutePath}")
            val str = file.absolutePath + LOCK
            val lockFile = File(str)
            if (!lockFile.exists()) {
                lockFile.parentFile?.mkdirs()
                lockFile.createNewFile()
            }
            if (!LOCK_HELD.add(str)) {
                throw IOException("abtain lock failure")
            }
            val fileLocker = FileLocker(context)
            fileLocker.mLockFileName = str
            return try {
                val randomAccessFile = RandomAccessFile(lockFile, "rw")
                fileLocker.mLockFile = randomAccessFile
                fileLocker.mLock = randomAccessFile.channel.lock()
                MyLog.v("Locked: $str :${fileLocker.mLock}")
                if (fileLocker.mLock == null) {
                    fileLocker.mLockFile?.let { IOUtils.closeQuietly(it) }
                    LOCK_HELD.remove(fileLocker.mLockFileName)
                }
                fileLocker
            } catch (th: Throwable) {
                if (fileLocker.mLock == null) {
                    fileLocker.mLockFile?.let { IOUtils.closeQuietly(it) }
                    LOCK_HELD.remove(fileLocker.mLockFileName)
                }
                throw th
            }
        }
    }

    fun unlock() {
        MyLog.v("unLock: $mLock")
        val lock = mLock
        if (lock != null && lock.isValid) {
            try {
                lock.release()
            } catch (e: IOException) {
                // ignore
            }
            mLock = null
        }
        mLockFile?.let { IOUtils.closeQuietly(it) }
        mLockFileName?.let { LOCK_HELD.remove(it) }
    }
}
