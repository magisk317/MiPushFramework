package com.xiaomi.clientreport.job

import android.content.Context
import co.touchlab.kermit.Logger
import com.xiaomi.clientreport.processor.IWrite

class WriteJob(
    private val mContext: Context,
    private val mWriter: IWrite,
) : Runnable {

    override fun run() {
        try {
            mWriter.process()
        } catch (e: Exception) {
            Logger.e(e) { "WriteJob error" }
        }
    }
}
