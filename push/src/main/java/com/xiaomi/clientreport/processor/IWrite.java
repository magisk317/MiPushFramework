package com.xiaomi.clientreport.processor;

import com.xiaomi.clientreport.data.BaseClientReport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/processor/IWrite.class */
public interface IWrite {
    void preProcess(BaseClientReport baseClientReport);

    void process();

    void write(BaseClientReport[] baseClientReportArr);
}
