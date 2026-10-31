package com.xiaomi.clientreport.job

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
import com.xiaomi.clientreport.processor.IWrite

class WriteJob(
    private val mContext: Context,
    private val mWriter: IWrite,
) : Runnable {

    override fun run() {
        try {
            mWriter.process()
        } catch (e: Exception) {
            XLog.e("WriteJob error", e)
        }
    }
}
