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

class PkgUninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null ||
            "android.intent.action.PACKAGE_FULLY_REMOVED" != intent.action
        ) {
            return
        }

        val encodedSchemeSpecificPart = intent.data?.encodedSchemeSpecificPart
        if (TextUtils.isEmpty(encodedSchemeSpecificPart)) {
            return
        }

        try {
            val serviceIntent = Intent().apply {
                component = ComponentName(context, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
                action = PushServiceConstants.ACTION_PACKAGE_UNINSTALLED
                putExtra(PushServiceConstants.EXTRA_UNINSTALLED_PKG_NAME, encodedSchemeSpecificPart)
            }
            ServiceClient.getInstance(context).startServiceSafely(serviceIntent)
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }
}
