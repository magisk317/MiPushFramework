package com.xiaomi.clientreport.processor

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.clientreport.data.BaseClientReport
import com.xiaomi.clientreport.data.PerfClientReport
import com.xiaomi.clientreport.util.ClientReportUtil
import java.io.File

open class DefaultPerfProcessor(context: Context) : IPerfProcessor {

    companion object {
        private const val FOLDER = "perf"
        private const val MAX_SAME_PRODUCTION_FILE_NUM = 20
        const val SEPARATOR = "#"
        private const val SLEEP_NUM = 25
        private const val UPLOAD_FOLDER = "perfUploading"
    }

    @JvmField
    protected var mContext: Context = context
    protected var mPerfMap: HashMap<String, HashMap<String, BaseClientReport>>? = null

    fun getFirstPerfFileName(baseClientReport: BaseClientReport): String {
        return "${baseClientReport.production}#${baseClientReport.clientInterfaceId}"
    }

    private fun getOriginalFilePath(baseClientReport: BaseClientReport): String? {
        val i = baseClientReport.production
        val str = baseClientReport.clientInterfaceId
        if (i <= 0 || TextUtils.isEmpty(str)) {
            return ""
        }
        val externalFilesDir = mContext.getExternalFilesDir(FOLDER)
        if (externalFilesDir == null) {
            MyLog.e("cannot get folder when to write perf")
            return null
        }
        if (!externalFilesDir.exists()) {
            externalFilesDir.mkdirs()
        }
        return File(externalFilesDir, "$i#$str").absolutePath
    }

    private fun getWriteFileName(baseClientReport: BaseClientReport): String? {
        val originalFilePath = getOriginalFilePath(baseClientReport)
        if (TextUtils.isEmpty(originalFilePath)) return null
        var i = 0
        while (i < MAX_SAME_PRODUCTION_FILE_NUM) {
            val str = "$originalFilePath$i"
            if (ClientReportUtil.isFileCanBeUse(mContext, str)) {
                return str
            }
            i++
        }
        return null
    }

    override fun preProcess(baseClientReport: BaseClientReport) {
        if (baseClientReport is PerfClientReport && mPerfMap != null) {
            val perfClientReport = baseClientReport as PerfClientReport
            val firstPerfFileName = getFirstPerfFileName(perfClientReport)
            val strGenerateKey = PerfKVFileHelper.generateKey(perfClientReport)
            var map2 = mPerfMap!![firstPerfFileName]
            if (map2 == null) {
                map2 = HashMap()
            }
            val perfClientReport2 = map2[strGenerateKey] as PerfClientReport?
            if (perfClientReport2 != null) {
                perfClientReport.perfCounts += perfClientReport2.perfCounts
                perfClientReport.perfLatencies += perfClientReport2.perfLatencies
            }
            map2[strGenerateKey] = perfClientReport
            mPerfMap!![firstPerfFileName] = map2
        }
    }

    override fun process() {
        val map = mPerfMap ?: return
        if (map.isNotEmpty()) {
            for (key in map.keys) {
                val map2 = map[key]
                if (map2 != null && map2.isNotEmpty()) {
                    val baseClientReportArr = map2.values.toTypedArray()
                    write(baseClientReportArr)
                }
            }
        }
        mPerfMap!!.clear()
    }

    override fun readAndSend() {
        ClientReportUtil.moveFiles(mContext, FOLDER, UPLOAD_FOLDER)
        val readFileName = ClientReportUtil.getReadFileName(mContext, UPLOAD_FOLDER)
        if (readFileName.isNullOrEmpty()) return
        for (file in readFileName) {
            if (file != null) {
                val listExtractToDatas = PerfKVFileHelper.extractToDatas(mContext, file.absolutePath)
                file.delete()
                send(listExtractToDatas)
            }
        }
    }

    override fun send(list: List<String>) {
        ClientReportUtil.sendFile(mContext, list)
    }

    override fun setPerfMap(map: HashMap<String, HashMap<String, BaseClientReport>>) {
        mPerfMap = map
    }

    override fun write(baseClientReportArr: Array<BaseClientReport>) {
        val writeFileName = getWriteFileName(baseClientReportArr[0]) ?: return
        PerfKVFileHelper.put(writeFileName, baseClientReportArr)
    }
}
