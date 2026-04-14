package com.xiaomi.clientreport.processor

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.clientreport.data.BaseClientReport
import com.xiaomi.clientreport.data.ClientReportConstants
import com.xiaomi.clientreport.data.PerfClientReport
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock

object PerfKVFileHelper {
    private fun buildPerfClientReport(
        perfClientReport: PerfClientReport?,
        str: String,
    ): PerfClientReport? {
        if (perfClientReport == null) return null
        val valueStr = parseValueStr(str) ?: return null
        perfClientReport.perfCounts = valueStr[0]
        perfClientReport.perfLatencies = valueStr[1]
        return perfClientReport
    }

    fun extractToDatas(context: Context, str: String): List<String> {
        val arrayList = ArrayList<String>()
        if (str.isEmpty() || !File(str).exists()) {
            return arrayList
        }
        var bufferedReader: BufferedReader? = null
        var randomAccessFile: RandomAccessFile? = null
        var fileLock: FileLock? = null
        var file2: File? = null
        try {
            val file3 = File("$str.lock")
            IOUtils.createFileQuietly(file3)
            val randomAccessFile3 = RandomAccessFile(file3, "rw")
            val fileLockLock = randomAccessFile3.channel.lock()
            val bufferedReader3 = BufferedReader(FileReader(str))
            while (true) {
                bufferedReader = bufferedReader3
                randomAccessFile = randomAccessFile3
                fileLock = fileLockLock
                file2 = file3
                val line = bufferedReader3.readLine() ?: break
                val strArrSplit = line.split(ClientReportConstants.SEPARATOR.toRegex())
                    .filter { it.isNotEmpty() }
                    .toTypedArray()
                if (strArrSplit.size >= 2 &&
                    strArrSplit[0].isNotEmpty() &&
                    strArrSplit[1].isNotEmpty()
                ) {
                    val perfClientReportBuildPerfClientReport =
                        buildPerfClientReport(spiltKeyForModel(strArrSplit[0]), strArrSplit[1])
                    if (perfClientReportBuildPerfClientReport != null) {
                        arrayList.add(perfClientReportBuildPerfClientReport.toJsonString())
                    }
                }
            }
            if (fileLockLock.isValid) {
                try {
                    fileLockLock.release()
                } catch (e: IOException) {
                    MyLog.e(e)
                }
            }
            IOUtils.closeQuietly(randomAccessFile3)
            IOUtils.closeQuietly(bufferedReader3)
            file2.delete()
        } catch (e: Exception) {
            MyLog.e(e)
            if (fileLock != null && fileLock.isValid) {
                try {
                    fileLock.release()
                } catch (e: IOException) {
                    MyLog.e(e)
                }
            }
            IOUtils.closeQuietly(randomAccessFile)
            IOUtils.closeQuietly(bufferedReader)
        } catch (th: Throwable) {
            if (fileLock != null && fileLock.isValid) {
                try {
                    fileLock.release()
                } catch (e: IOException) {
                    MyLog.e(e)
                }
            }
            IOUtils.closeQuietly(randomAccessFile)
            IOUtils.closeQuietly(bufferedReader)
            file2?.delete()
            throw th
        }
        return arrayList
    }

    fun generateKey(perfClientReport: PerfClientReport): String {
        return "${perfClientReport.production}#${perfClientReport.clientInterfaceId}#${perfClientReport.reportType}#${perfClientReport.code}"
    }

    fun parseValueStr(str: String): LongArray? {
        return try {
            val strArrSplit = str.split("#").toTypedArray()
            if (strArrSplit.size >= 2) {
                longArrayOf(
                    strArrSplit[0].trim().toLong(),
                    strArrSplit[1].trim().toLong(),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            MyLog.e(e)
            null
        }
    }

    fun put(str: String, baseClientReportArr: Array<BaseClientReport>) {
        if (baseClientReportArr.isEmpty() || str.isEmpty()) return
        var randomAccessFile2: RandomAccessFile? = null
        var fileLock: FileLock? = null
        try {
            val file = File("$str.lock")
            IOUtils.createFileQuietly(file)
            val randomAccessFile3 = RandomAccessFile(file, "rw")
            val fileLockLock = randomAccessFile3.channel.lock()
            val fromFile = readFromFile(str)
            for (baseClientReport in baseClientReportArr) {
                if (baseClientReport != null) {
                    val strGenerateKey =
                        generateKey(baseClientReport as PerfClientReport)
                    val j = (baseClientReport as PerfClientReport).perfCounts
                    val j2 = (baseClientReport as PerfClientReport).perfLatencies
                    if (strGenerateKey.isNotEmpty() && j > 0 && j2 >= 0) {
                        putInMemeory(fromFile, strGenerateKey, j, j2)
                    }
                }
            }
            randomAccessFile2 = randomAccessFile3
            fileLock = fileLockLock
            writeToFile(str, fromFile)
            if (fileLockLock.isValid) {
                try {
                    fileLockLock.release()
                } catch (e: IOException) {
                    MyLog.e(e)
                }
            }
        } catch (th: Throwable) {
            MyLog.v("failed to write perf to file ")
            if (fileLock != null && fileLock.isValid) {
                try {
                    fileLock.release()
                } catch (e: IOException) {
                    MyLog.e(e)
                }
            }
            IOUtils.closeQuietly(randomAccessFile2)
            throw th
        }
        IOUtils.closeQuietly(randomAccessFile2)
    }

    private fun putInMemeory(
        map: HashMap<String, String>,
        str: String,
        j: Long,
        j2: Long,
    ) {
        val str3 = map[str]
        if (TextUtils.isEmpty(str3)) {
            map[str] = "$j#$j2"
            return
        }
        val valueStr = parseValueStr(str3!!)
        val str2 = if (valueStr == null || valueStr[0] <= 0 || valueStr[1] < 0) {
            "$j#$j2"
        } else {
            "${valueStr[0] + j}#${valueStr[1] + j2}"
        }
        map[str] = str2
    }

    private fun readFromFile(str: String): HashMap<String, String> {
        val map = HashMap<String, String>()
        if (str.isEmpty() || !File(str).exists()) {
            return map
        }
        var bufferedReader2: BufferedReader? = null
        try {
            val bufferedReader = BufferedReader(FileReader(str))
            while (true) {
                bufferedReader2 = bufferedReader
                val line = bufferedReader.readLine() ?: break
                val strArrSplit = line.split(ClientReportConstants.SEPARATOR.toRegex())
                    .filter { it.isNotEmpty() }
                    .toTypedArray()
                if (strArrSplit.size >= 2 &&
                    strArrSplit[0].isNotEmpty() &&
                    strArrSplit[1].isNotEmpty()
                ) {
                    map[strArrSplit[0]] = strArrSplit[1]
                }
            }
        } catch (e: Exception) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(bufferedReader2)
        }
        return map
    }

    private fun spiltKey(str: String): Array<String>? {
        if (str.isEmpty()) return null
        return str.split("#").toTypedArray()
    }

    private fun spiltKeyForModel(str: String): PerfClientReport? {
        return try {
            val strArrSpiltKey = spiltKey(str)
            if (strArrSpiltKey != null &&
                strArrSpiltKey.size >= 4 &&
                strArrSpiltKey[0].isNotEmpty() &&
                strArrSpiltKey[1].isNotEmpty() &&
                strArrSpiltKey[2].isNotEmpty() &&
                strArrSpiltKey[3].isNotEmpty()
            ) {
                PerfClientReport.getBlankInstance().apply {
                    production = strArrSpiltKey[0].toInt()
                    clientInterfaceId = strArrSpiltKey[1]
                    reportType = strArrSpiltKey[2].toInt()
                    code = strArrSpiltKey[3].toInt()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            MyLog.v("parse per key error")
            null
        }
    }

    private fun writeToFile(str: String, map: HashMap<String, String>) {
        if (str.isEmpty() || map.isEmpty()) return
        val file = File(str)
        if (file.exists()) {
            file.delete()
        }
        var bufferedWriter2: BufferedWriter? = null
        try {
            val bufferedWriter3 = BufferedWriter(FileWriter(file))
            for (next in map.keys) {
                val str2 = map[next]
                bufferedWriter3.write("$next${ClientReportConstants.SEPARATOR}$str2")
                bufferedWriter3.newLine()
            }
            bufferedWriter2 = bufferedWriter3
        } catch (e: Exception) {
            MyLog.e(e)
        }
        IOUtils.closeQuietly(bufferedWriter2)
    }
}
