package com.xiaomi.push.service.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.ServiceClientIntentSupport
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.ServiceClient

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/xmsf/push/service/receivers/PkgActionsReceiver.java
 * Current override reference: com.xiaomi.xmsf/current/base/sources/com/xiaomi/xmsf/push/service/receivers/PkgDataClearedReceiver.java
 * Stock combines package-data-cleared and package-removed handling; this file keeps the split deobfuscated API.
 */
class PkgDataClearedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null ||
            "android.intent.action.PACKAGE_DATA_CLEARED" != intent.action ||
            intent.data == null
        ) {
            return
        }

        val encodedSchemeSpecificPart = intent.data?.encodedSchemeSpecificPart
        if (encodedSchemeSpecificPart.isNullOrEmpty()) {
            return
        }

        try {
            val serviceIntent = Intent().apply {
                component = ComponentName(context, ServiceClientIntentSupport.localServiceClassName(context))
                action = PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED
                putExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME, encodedSchemeSpecificPart)
            }
            ServiceClient.getInstance(context).startServiceSafely(serviceIntent)
        } catch (e: Exception) {
            MyLog.e("data cleared broadcast error: $e")
        }
    }
}
