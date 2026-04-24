package com.xiaomi.clientreport.processor
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.clientreport.data.BaseClientReport

interface IWrite {
    fun preProcess(baseClientReport: BaseClientReport)

    fun process()

    fun write(baseClientReportArr: Array<BaseClientReport>)
}
