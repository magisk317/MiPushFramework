package com.xiaomi.push.service.clientReport;

import android.content.Context;
import com.xiaomi.clientreport.processor.DefaultPerfProcessor;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/clientReport/MIPushPerfDataProcessor.class */
public class MIPushPerfDataProcessor extends DefaultPerfProcessor {
    public MIPushPerfDataProcessor(Context context) {
        super(context);
    }

    @Override // com.xiaomi.clientreport.processor.DefaultPerfProcessor, com.xiaomi.clientreport.processor.IDataSend
    public void send(List<String> list) {
        PushClientReportHelper.sendData(this.mContext, list);
    }
}
