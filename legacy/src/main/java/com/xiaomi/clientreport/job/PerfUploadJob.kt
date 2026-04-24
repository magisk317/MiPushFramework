package com.xiaomi.clientreport.job
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.clientreport.manager.ClientReportLogicManager

class PerfUploadJob(private val mContext: Context) : ScheduledJobManager.Job() {

    private fun checkPerfNeedUpload(): Boolean {
        return ClientReportLogicManager.getInstance(mContext).config.isPerfUploadSwitchOpen
    }

    override fun getJobId(): String = ScheduledJobConstants.PERF_UPLOAD_JOB_ID

    override fun run() {
        try {
            if (checkPerfNeedUpload()) {
                ClientReportLogicManager.getInstance(mContext).sendPerf()
                MyLog.v("${mContext.packageName} perf  begin upload")
            }
        } catch (e: Exception) {
            MyLog.e("fail to send perf data. $e")
        }
    }
}
