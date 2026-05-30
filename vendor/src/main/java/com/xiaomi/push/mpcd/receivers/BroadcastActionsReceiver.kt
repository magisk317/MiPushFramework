package com.xiaomi.push.mpcd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.mpcd.IntentHandler

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/receivers/BroadcastActionsReceiver.java
 * Stock 7.4.67-C handles the receiver flow through vb.b; no stock same-path receiver source was found.
 */
class BroadcastActionsReceiver(private val mHandler: IntentHandler?) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        mHandler?.handle(context, intent)
    }
}
