package com.xiaomi.clientreport.job;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.clientreport.processor.IDataSend;
import com.xiaomi.clientreport.processor.IEventProcessor;
import com.xiaomi.clientreport.processor.IPerfProcessor;
import com.xiaomi.clientreport.util.SPManager;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/job/ReadAndSendJob.class */
public class ReadAndSendJob implements Runnable {
    private Context mContext;
    private IDataSend mReadAndSender;

    @Override // java.lang.Runnable
    public void run() {
        try {
            IDataSend iDataSend = this.mReadAndSender;
            if (iDataSend != null) {
                iDataSend.readAndSend();
            }
            MyLog.v("begin read and send perf / event");
            IDataSend iDataSend2 = this.mReadAndSender;
            if (iDataSend2 instanceof IEventProcessor) {
                SPManager.getInstance(this.mContext).setLongValue(ClientReportConstants.SP_FILE_STATUS, "event_last_upload_time", System.currentTimeMillis());
            } else if (iDataSend2 instanceof IPerfProcessor) {
                SPManager.getInstance(this.mContext).setLongValue(ClientReportConstants.SP_FILE_STATUS, "perf_last_upload_time", System.currentTimeMillis());
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    public void setReadAndSender(IDataSend iDataSend) {
        this.mReadAndSender = iDataSend;
    }
}
