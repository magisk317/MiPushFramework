package com.xiaomi.clientreport.processor

import com.xiaomi.clientreport.data.BaseClientReport

interface IWrite {
    fun preProcess(baseClientReport: BaseClientReport)

    fun process()

    fun write(baseClientReportArr: Array<BaseClientReport>)
}
