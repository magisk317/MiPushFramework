package com.xiaomi.clientreport.job

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.clientreport.processor.IWrite

class WriteJob(
    private val mContext: Context,
    private val mWriter: IWrite,
) : Runnable {

    override fun run() {
        try {
            mWriter.process()
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }
}
