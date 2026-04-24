package com.xiaomi.push.service.receivers
import io.github.magisk317.mipush.protocol.model.*

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
