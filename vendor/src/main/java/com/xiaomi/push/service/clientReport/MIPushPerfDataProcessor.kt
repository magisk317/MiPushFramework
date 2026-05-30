package com.xiaomi.push.service.clientReport

import android.content.Context
import com.xiaomi.clientreport.processor.DefaultPerfProcessor

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ea/b.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/clientReport/MIPushPerfDataProcessor.java
 * Stock class name is obfuscated as ea.b; this file keeps the deobfuscated com.xiaomi.push.service.clientReport.MIPushPerfDataProcessor API.
 */
class MIPushPerfDataProcessor(context: Context) : DefaultPerfProcessor(context) {
    override fun send(list: List<String>) {
        PushClientReportHelper.sendData(mContext, list)
    }
}
