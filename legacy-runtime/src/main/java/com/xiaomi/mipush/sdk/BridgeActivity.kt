package com.xiaomi.mipush.sdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/BridgeActivity.java
 * No current same-path BridgeActivity.java is present in the 2026-04-13 current override.
 */
class BridgeActivity : Activity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        val window = window
        val attributes = window.attributes
        attributes.height = 1
        attributes.width = 1
        attributes.gravity = 51
        window.attributes = attributes
    }

    override fun onResume() {
        super.onResume()
        try {
            try {
                val extras = intent?.extras
                val payloadIntent = if (extras != null) {
                    BundleCompat.getParcelable(extras, PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, Intent::class.java)
                } else {
                    null
                }
                if (payloadIntent != null) {
                    PushMessageHandler.addJob(applicationContext, payloadIntent)
                }
            } catch (e: Exception) {
                MyLog.e(e)
            }
        } finally {
            finish()
        }
    }
}
