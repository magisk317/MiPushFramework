package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.timers.Alarm
import io.github.magisk317.mipush.service.PushServiceStarter

class MiPushPingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        MyLog.v("${intent.`package`} is the package name")
        if (PushConstants.ACTION_PING_TIMER == intent.action) {
            if (TextUtils.equals(context.packageName, intent.`package`)) {
                MyLog.v("Ping XMChannelService on timer")
                try {
                    val localIntent = Intent(context, com.xiaomi.push.service.XMPushService::class.java)
                    localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis())
                    localIntent.action = PushServiceConstants.ACTION_TIMER
                    PushServiceStarter.start(context, localIntent)
                } catch (localException: Exception) {
                    MyLog.e(localException)
                }
            } else {
                MyLog.w("cancel the old ping timer")
                Alarm.stop()
            }
        }
    }
}
