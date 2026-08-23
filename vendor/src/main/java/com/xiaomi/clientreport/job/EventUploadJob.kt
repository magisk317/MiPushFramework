package com.xiaomi.clientreport.job

import android.content.Context
import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.clientreport.manager.ClientReportLogicManager

class EventUploadJob(private val mContext: Context) : ScheduledJobManager.Job() {

    private fun checkEventNeedUpload(): Boolean {
        return ClientReportLogicManager.getInstance(mContext).config.isEventUploadSwitchOpen
    }

    override fun getJobId(): String = ScheduledJobConstants.EVENT_UPLOAD_JOB_ID

    override fun run() {
        try {
            if (checkEventNeedUpload()) {
                Logger.v { "${mContext.packageName} begin upload event" }
                ClientReportLogicManager.getInstance(mContext).sendEvent()
            }
        } catch (e: Exception) {
            Logger.e(e) { "EventUploadJob error" }
        }
    }
}
