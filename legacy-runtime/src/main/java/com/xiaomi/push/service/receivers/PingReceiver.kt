package com.xiaomi.push.service.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.ServiceClient
import com.xiaomi.push.service.timers.Alarm

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/receivers/PingReceiver.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/receivers/PingReceiver.java
 */
class PingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MyLog.v("${intent.`package`} is the package name")

        if (PushConstants.ACTION_PING_TIMER != intent.action) {
            MyLog.w("cancel the old ping timer")
            Alarm.stop()
        } else if (TextUtils.equals(context.packageName, intent.`package`)) {
            MyLog.v("Ping XMChannelService on timer")
            try {
                val serviceIntent = Intent().apply {
                    component = ComponentName(context, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
                    putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis())
                    action = PushServiceConstants.ACTION_TIMER
                }
                ServiceClient.getInstance(context).startServiceSafely(serviceIntent)
            } catch (e: Exception) {
                MyLog.e(e)
            }
        }
    }
}
