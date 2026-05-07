package com.xiaomi.mipush.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/PushServiceReceiver.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushServiceReceiver.java
 */
class PushServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val intent2 = Intent(context, PushMessageHandler::class.java)
        intent2.putExtras(intent)
        intent2.action = intent.action
        PushMessageHandler.addJob(context, intent2)
    }
}
