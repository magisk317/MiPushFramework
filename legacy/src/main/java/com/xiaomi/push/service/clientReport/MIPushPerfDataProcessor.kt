package com.xiaomi.push.service.clientReport
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.clientreport.processor.DefaultPerfProcessor

class MIPushPerfDataProcessor(context: Context) : DefaultPerfProcessor(context) {
    override fun send(list: List<String>) {
        PushClientReportHelper.sendData(mContext, list)
    }
}
