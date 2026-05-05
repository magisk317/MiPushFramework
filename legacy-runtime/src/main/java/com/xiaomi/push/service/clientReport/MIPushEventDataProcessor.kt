package com.xiaomi.push.service.clientReport

import android.content.Context
import com.xiaomi.clientreport.processor.DefaultEventProcessor

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ea/a.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/clientReport/MIPushEventDataProcessor.java
 * Stock class name is obfuscated as ea.a; this file keeps the deobfuscated com.xiaomi.push.service.clientReport.MIPushEventDataProcessor API.
 */
class MIPushEventDataProcessor(context: Context) : DefaultEventProcessor(context) {
    override fun send(list: List<String>) {
        PushClientReportHelper.sendData(mContext, list)
    }
}
