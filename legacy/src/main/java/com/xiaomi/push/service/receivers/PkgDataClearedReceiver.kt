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

class PkgDataClearedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null ||
            "android.intent.action.PACKAGE_DATA_CLEARED" != intent.action ||
            intent.data == null
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
                action = PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED
                putExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME, encodedSchemeSpecificPart)
            }
            ServiceClient.getInstance(context).startServiceSafely(serviceIntent)
        } catch (e: Exception) {
            MyLog.e("data cleared broadcast error: $e")
        }
    }
}
