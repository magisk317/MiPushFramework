package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushServiceConstants

/**
 * @author zts
 */
class KeepAliveReceiver : BroadcastReceiver() {
    private val logger: Logger = XLog.tag(KeepAliveReceiver::class.java.simpleName).build()
    private var lastActive: Long = System.currentTimeMillis()

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastActive < 1000 * 60 * 2) {
                return
            }

            lastActive = now
            logger.d("start service when ${intent?.action}")
            val localIntent = Intent(context, com.xiaomi.push.service.XMPushService::class.java)
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now)
            localIntent.action = PushServiceConstants.ACTION_CHECK_ALIVE
            ContextCompat.startForegroundService(context, localIntent)
        } catch (localException: Throwable) {
            MyLog.e(localException)
        }
    }
}
