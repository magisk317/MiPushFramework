package com.xiaomi.clientreport.job
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.clientreport.data.BaseClientReport
import com.xiaomi.clientreport.processor.IWrite

class DataPreProcessJob(
    private val mContext: Context,
    private val mClientReport: BaseClientReport,
    private val mWriter: IWrite,
) : Runnable {

    override fun run() {
        mWriter.preProcess(mClientReport)
    }
}
