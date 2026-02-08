package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.xiaomi.push.service.PushServiceConstants

class PkgUninstallReceiver : BroadcastReceiver() {
    private val logger = XLog.tag("PkgUninstallReceiver").build()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.extras != null && "android.intent.action.PACKAGE_REMOVED" == intent.action) {
            val replacing = intent.extras?.getBoolean("android.intent.extra.REPLACING") ?: false
            val data = intent.data
            if (data != null && !replacing) {
                try {
                    val serviceIntent = Intent(context, com.xiaomi.push.service.XMPushService::class.java)
                    serviceIntent.action = PushServiceConstants.ACTION_UNINSTALL
                    serviceIntent.putExtra(
                        PushServiceConstants.EXTRA_UNINSTALL_PKG_NAME,
                        data.encodedSchemeSpecificPart
                    )
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    logger.e(e.message, e)
                }
            }
        }
    }
}
