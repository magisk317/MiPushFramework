package com.xiaomi.clientreport.job
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
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
                MyLog.v("${mContext.packageName} begin upload event")
                ClientReportLogicManager.getInstance(mContext).sendEvent()
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }
}
