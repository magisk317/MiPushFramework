package com.xiaomi.push.service.clientReport

import android.content.Context
import com.xiaomi.clientreport.processor.DefaultEventProcessor

class MIPushEventDataProcessor(context: Context) : DefaultEventProcessor(context) {
    override fun send(list: List<String>) {
        PushClientReportHelper.sendData(mContext, list)
    }
}
