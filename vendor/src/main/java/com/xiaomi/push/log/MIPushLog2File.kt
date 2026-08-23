package com.xiaomi.push.log

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.xiaomi.channel.commonutils.file.SDCardUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.KermitLoggerCompat
import java.io.*
import java.nio.channels.FileLock
import java.text.SimpleDateFormat
import java.util.*

class MIPushLog2File(context: Context) : LoggerInterface {
    private var mHandler: Handler
    private var mSDCardRootPath: String = ""
    private var mTag: String
    private var sAppContext: Context

    init {
        sAppContext = context
        if (context.applicationContext != null) {
            sAppContext = context.applicationContext
        }
        mTag = sAppContext.packageName
        val handlerThread = HandlerThread("Log2FileHandlerThread")
        handlerThread.start()
        mHandler = Handler(handlerThread.looper)
    }

    private fun writeLog2File() {
        var randomAccessFile: RandomAccessFile? = null
        var fileLock: FileLock? = null
        var bufferedWriter: BufferedWriter? = null
        try {
            if (mSDCardRootPath.isEmpty()) {
                val externalFilesDir = sAppContext.getExternalFilesDir(null)
                if (externalFilesDir != null) {
                    mSDCardRootPath = externalFilesDir.absolutePath
                }
            }
            val logDir = File(mSDCardRootPath + MIPUSH_LOG_PATH)
            if ((!logDir.exists() || !logDir.isDirectory) && !logDir.mkdirs()) {
                KermitLoggerCompat.w(message = "Create mipushlog directory fail.", tag = mTag)
                return
            }
            val lockFile = File(logDir, LOCK_FILE)
            if (!lockFile.exists() || lockFile.isDirectory) {
                lockFile.createNewFile()
            }
            randomAccessFile = RandomAccessFile(lockFile, "rw")
            fileLock = randomAccessFile.channel.lock()
            bufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(File(logDir, NEW_FILE_NAME), true)))
            while (logs.isNotEmpty()) {
                val pair = logs.removeAt(0)
                var logLine = pair.first
                if (pair.second != null) {
                    logLine += "\n" + pair.second?.stackTraceToString()
                }
                bufferedWriter.write(logLine)
                bufferedWriter.write("\n")
            }
            bufferedWriter.flush()
            val currentLog = File(logDir, NEW_FILE_NAME)
            if (currentLog.length() >= FILE_MAX_SIZE) {
                val oldFile = File(logDir, OLD_FILE_NAME)
                if (oldFile.exists() && oldFile.isFile) {
                    oldFile.delete()
                }
                currentLog.renameTo(oldFile)
            }
        } catch (e: IOException) {
            KermitLoggerCompat.e(message = "Failed to write log file", throwable = e, tag = mTag)
        } catch (e: Exception) {
            KermitLoggerCompat.e(message = "Failed to write log file", throwable = e, tag = mTag)
        } finally {
            try { bufferedWriter?.close() } catch (e: IOException) { KermitLoggerCompat.e(message = "Close bufferedWriter failed", throwable = e, tag = mTag) }
            if (fileLock != null && fileLock.isValid) {
                try { fileLock.release() } catch (e: IOException) { KermitLoggerCompat.e(message = "Release lock failed", throwable = e, tag = mTag) }
            }
            try { randomAccessFile?.close() } catch (e: IOException) { KermitLoggerCompat.e(message = "Close randomAccessFile failed", throwable = e, tag = mTag) }
        }
    }

    private fun tryWriteLog() {
        if (logs.size > LOGS_MAX_LINE) {
            val removeCount = (logs.size - LOGS_MAX_LINE) + 50
            for (i in 0 until removeCount) {
                try {
                    if (logs.isNotEmpty()) logs.removeAt(0)
                } catch (_: IndexOutOfBoundsException) {
                }
            }
            logs.add(
                Pair(
                    String.format("%1\$s %2\$s %3\$s ", dateFormatter.format(Date()), mTag, "flush $removeCount lines logs."),
                    null
                )
            )
        }
        try {
            if (SDCardUtils.isSDCardUseful()) {
                writeLog2File()
            } else {
                KermitLoggerCompat.w(message = "SDCard is unavailable.", tag = mTag)
            }
        } catch (e: Exception) {
            KermitLoggerCompat.e(message = "tryWriteLog failed", throwable = e, tag = mTag)
        }
    }

    override fun log(str: String) {
        mHandler.post {
            logs.add(
                Pair(
                    String.format("%1\$s %2\$s %3\$s ", dateFormatter.format(Date()), mTag, str),
                    null
                )
            )
            tryWriteLog()
        }
    }

    override fun log(str: String, th: Throwable) {
        mHandler.post {
            logs.add(
                Pair(
                    String.format("%1\$s %2\$s %3\$s ", dateFormatter.format(Date()), mTag, str),
                    th
                )
            )
            tryWriteLog()
        }
    }

    override fun setTag(str: String) {
        mTag = str
    }

    companion object {
        private const val FILE_MAX_SIZE = 1048576L
        private const val LOCK_FILE = "log.lock"
        private const val LOGS_MAX_LINE = 20000
        private const val NEW_FILE_NAME = "log1.txt"
        private const val OLD_FILE_NAME = "log0.txt"
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss aaa", Locale.US)

        var MIPUSH_LOG_PATH = "/MiPushLog"
        private val logs: MutableList<Pair<String, Throwable?>> = Collections.synchronizedList(ArrayList())
    }
}
