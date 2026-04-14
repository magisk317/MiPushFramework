package com.xiaomi.push.mpcd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.mpcd.IntentHandler

class BroadcastActionsReceiver(private val mHandler: IntentHandler?) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        mHandler?.handle(context, intent)
    }
}
