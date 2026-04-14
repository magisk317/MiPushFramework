package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.push.service.PushServiceConstants
import io.github.magisk317.mipush.service.PushServiceStarter

class PkgUninstallReceiver : BroadcastReceiver() {
    private val TAG = "PkgUninstallReceiver"
    private val logger = object {
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "", t, tag = TAG)
    }

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
                    PushServiceStarter.start(context, serviceIntent)
                } catch (e: Exception) {
                    logger.e(e.message, e)
                }
            }
        }
    }
}
