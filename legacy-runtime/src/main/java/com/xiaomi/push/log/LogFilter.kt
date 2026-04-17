package com.xiaomi.push.log

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

internal class LogFilter {
    private var mCurrentLen = 0
    private var mEndTime: String = ""
    private var mFromTime: String = ""
    private var mStartFound = false
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var mMaxLen = 2097152
    private val mFiles = ArrayList<File>()

    private fun doFilter(reader: BufferedReader, writer: BufferedWriter, pattern: Pattern) {
        val buffer = CharArray(4096)
        var bytesRead = reader.read(buffer)
        var shouldBreak = false
        while (bytesRead != -1 && !shouldBreak) {
            val str = String(buffer, 0, bytesRead)
            val matcher = pattern.matcher(str)
            var pos = 0
            var startOffset = 0
            var currentBytes = bytesRead
            while (pos < bytesRead) {
                if (!matcher.find(pos)) break
                val matchStart = matcher.start()
                val timeStr = str.substring(matchStart, mFromTime.length + matchStart)
                if (mStartFound) {
                    if (timeStr > mEndTime) {
                        currentBytes = matchStart
                        shouldBreak = true
                        break
                    }
                } else {
                    if (timeStr >= mFromTime) {
                        startOffset = matchStart
                        mStartFound = true
                    }
                }
                val lineEnd = str.indexOf('\n', matchStart)
                pos = if (lineEnd != -1) {
                    matchStart + lineEnd
                } else {
                    matchStart + mFromTime.length
                }
            }
            if (mStartFound) {
                val writeLen = currentBytes - startOffset
                mCurrentLen += writeLen
                writer.write(buffer, startOffset, writeLen)
                if (mCurrentLen > mMaxLen) return
            }
            bytesRead = reader.read(buffer)
        }
    }

    private fun filter2File(outputFile: File) {
        if (mFiles.isEmpty()) return
        val pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile)))
            for (logFile in mFiles) {
                if (!logFile.exists()) continue
                var reader: BufferedReader? = null
                try {
                    reader = BufferedReader(InputStreamReader(FileInputStream(logFile)))
                    doFilter(reader, writer, pattern)
                    if (mCurrentLen > mMaxLen) break
                } finally {
                    IOUtils.closeQuietly(reader)
                }
            }
        } catch (e: IOException) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(writer)
        }
    }

    private fun filterXmsfLog2File(context: Context, outputFile: File) {
        try {
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile)))
            var index = 0
            val pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
            var reader: BufferedReader? = null
            while (true) {
                try {
                    val contentResolver = context.contentResolver
                    val uri = Uri.parse("content://com.xiaomi.xmsf/log/$index")
                    val inputStream = contentResolver.openInputStream(uri)
                    reader = BufferedReader(InputStreamReader(inputStream))
                    doFilter(reader, writer, pattern)
                    reader.close()
                    index++
                } catch (e: FileNotFoundException) {
                    IOUtils.closeQuietly(reader)
                    IOUtils.closeQuietly(writer)
                    return
                } catch (e: Exception) {
                    IOUtils.closeQuietly(reader)
                    IOUtils.closeQuietly(writer)
                    return
                } catch (th: Throwable) {
                    IOUtils.closeQuietly(reader)
                    IOUtils.closeQuietly(writer)
                    throw th
                }
            }
        } catch (e: FileNotFoundException) {
        }
    }

    fun addFile(file: File): LogFilter {
        if (file.exists()) mFiles.add(file)
        return this
    }

    fun filter(context: Context, startDate: Date, endDate: Date, outputDir: File): File? {
        val basePath: File
        if ("com.xiaomi.xmsf".equals(context.packageName, ignoreCase = true)) {
            val filesDir = File(context.getExternalFilesDir(null), "dump")
            basePath = if (filesDir.exists()) filesDir else context.filesDir
            addFile(File(basePath, "xmsf.log.1"))
            addFile(File(basePath, "xmsf.log"))
        } else {
            basePath = File(context.getExternalFilesDir(null).toString() + MIPUSH_LOG_PATH)
            addFile(File(basePath, "log0.txt"))
            addFile(File(basePath, "log1.txt"))
        }
        if (!basePath.isDirectory) return null
        val zipFile = File(outputDir, "${startDate.time}-${endDate.time}.zip")
        if (zipFile.exists()) return null
        setRange(startDate, endDate)
        val startTime = System.currentTimeMillis()
        val logFile = File(outputDir, "log.txt")
        filter2File(logFile)
        MyLog.v("LOG: filter cost = ${System.currentTimeMillis() - startTime}")
        if (!logFile.exists()) return null
        val zipStartTime = System.currentTimeMillis()
        try {
            IOUtils.zip(zipFile, logFile)
        } catch (e: IOException) {
            MyLog.e(e)
            return null
        }
        MyLog.v("LOG: zip cost = ${System.currentTimeMillis() - zipStartTime}")
        logFile.delete()
        return if (zipFile.exists()) zipFile else null
    }

    fun setMaxLen(len: Int) {
        if (len != 0) mMaxLen = len
    }

    fun setRange(date1: Date, date2: Date): LogFilter {
        if (date1.after(date2)) {
            mFromTime = dateFormatter.format(date2)
            mEndTime = dateFormatter.format(date1)
        } else {
            mFromTime = dateFormatter.format(date1)
            mEndTime = dateFormatter.format(date2)
        }
        return this
    }

    companion object {
        private var MIPUSH_LOG_PATH = "/MiPushLog"
    }
}
