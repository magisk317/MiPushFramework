package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.timers.Alarm
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.service.PushServiceStarter

class MiPushPingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        logV("${intent.`package`} is the package name")
        if (PushConstants.ACTION_PING_TIMER == intent.action) {
            if (TextUtils.equals(context.packageName, intent.`package`)) {
                logV("Ping XMChannelService on timer")
                try {
                    val localIntent = Intent(context, com.xiaomi.push.service.XMPushServiceCore::class.java)
                    localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis())
                    localIntent.action = PushServiceConstants.ACTION_TIMER
                    PushServiceStarter.start(context, localIntent)
                } catch (localException: Exception) {
                    logE("Ping timer start failed", localException)
                }
            } else {
                logW("cancel the old ping timer")
                Alarm.stop()
            }
        }
    }
}
