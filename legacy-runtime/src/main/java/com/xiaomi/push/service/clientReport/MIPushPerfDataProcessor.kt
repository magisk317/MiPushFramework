package com.xiaomi.push.service.clientReport

import android.content.Context
import com.xiaomi.clientreport.processor.DefaultPerfProcessor

class MIPushPerfDataProcessor(context: Context) : DefaultPerfProcessor(context) {
    override fun send(list: List<String>) {
        PushClientReportHelper.sendData(mContext, list)
    }
}
