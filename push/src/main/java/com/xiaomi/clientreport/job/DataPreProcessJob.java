package com.xiaomi.clientreport.job;

import android.content.Context;
import com.xiaomi.clientreport.data.BaseClientReport;
import com.xiaomi.clientreport.processor.IWrite;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/job/DataPreProcessJob.class */
public class DataPreProcessJob implements Runnable {
    private BaseClientReport mClientReport;
    private Context mContext;
    private IWrite mWriter;

    public DataPreProcessJob(Context context, BaseClientReport baseClientReport, IWrite iWrite) {
        this.mContext = context;
        this.mClientReport = baseClientReport;
        this.mWriter = iWrite;
    }

    @Override // java.lang.Runnable
    public void run() {
        IWrite iWrite = this.mWriter;
        if (iWrite != null) {
            iWrite.preProcess(this.mClientReport);
        }
    }
}
