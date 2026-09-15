package com.xiaomi.push.mpcd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.mpcd.IntentHandler

/*
 * Stock 7.4.67-C handles the receiver flow through vb.b; no stock same-path receiver source was found.
 */
class BroadcastActionsReceiver(private val mHandler: IntentHandler?) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        mHandler?.handle(context, intent)
    }
}
