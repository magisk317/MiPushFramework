package com.xiaomi.clientreport.job

import android.content.Context
import co.touchlab.kermit.Logger
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
                Logger.v { "${mContext.packageName} perf  begin upload" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "fail to send perf data: $e" }
        }
    }
}
