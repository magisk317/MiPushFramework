package com.xiaomi.clientreport.job

import android.content.Context
import co.touchlab.kermit.Logger
import com.xiaomi.clientreport.processor.IDataSend
import com.xiaomi.clientreport.processor.IEventProcessor
import com.xiaomi.clientreport.processor.IPerfProcessor
import com.xiaomi.clientreport.util.SPManager
import com.xiaomi.clientreport.data.ClientReportConstants

class ReadAndSendJob : Runnable {
    private var mContext: Context? = null
    private var mReadAndSender: IDataSend? = null

    override fun run() {
        try {
            mReadAndSender?.readAndSend()
            Logger.v { "begin read and send perf / event" }
            val context = mContext ?: return
            if (mReadAndSender is IEventProcessor) {
                SPManager.getInstance(context).setLongValue(
                    ClientReportConstants.SP_FILE_STATUS,
                    "event_last_upload_time",
                    System.currentTimeMillis(),
                )
            } else if (mReadAndSender is IPerfProcessor) {
                SPManager.getInstance(context).setLongValue(
                    ClientReportConstants.SP_FILE_STATUS,
                    "perf_last_upload_time",
                    System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            Logger.e(e) { "ReadAndSendJob error" }
        }
    }

    fun setContext(context: Context) {
        mContext = context
    }

    fun setReadAndSender(iDataSend: IDataSend) {
        mReadAndSender = iDataSend
    }
}
