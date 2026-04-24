package com.xiaomi.clientreport.job
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.clientreport.processor.IDataSend
import com.xiaomi.clientreport.processor.IEventProcessor
import com.xiaomi.clientreport.processor.IPerfProcessor
import com.xiaomi.clientreport.util.SPManager
import com.xiaomi.clientreport.data.ClientReportConstants
import com.xiaomi.channel.commonutils.logger.MyLog

class ReadAndSendJob : Runnable {
    private var mContext: Context? = null
    private var mReadAndSender: IDataSend? = null

    override fun run() {
        try {
            mReadAndSender?.readAndSend()
            MyLog.v("begin read and send perf / event")
            if (mReadAndSender is IEventProcessor) {
                SPManager.getInstance(mContext!!).setLongValue(
                    ClientReportConstants.SP_FILE_STATUS,
                    "event_last_upload_time",
                    System.currentTimeMillis(),
                )
            } else if (mReadAndSender is IPerfProcessor) {
                SPManager.getInstance(mContext!!).setLongValue(
                    ClientReportConstants.SP_FILE_STATUS,
                    "perf_last_upload_time",
                    System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    fun setContext(context: Context) {
        mContext = context
    }

    fun setReadAndSender(iDataSend: IDataSend) {
        mReadAndSender = iDataSend
    }
}
