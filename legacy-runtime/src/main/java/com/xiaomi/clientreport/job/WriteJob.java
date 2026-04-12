package com.xiaomi.clientreport.job;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.clientreport.processor.IWrite;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/job/WriteJob.class */
public class WriteJob implements Runnable {
    private Context mContext;
    private IWrite mWriter;

    public WriteJob(Context context, IWrite iWrite) {
        this.mContext = context;
        this.mWriter = iWrite;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            IWrite iWrite = this.mWriter;
            if (iWrite != null) {
                iWrite.process();
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }
}
