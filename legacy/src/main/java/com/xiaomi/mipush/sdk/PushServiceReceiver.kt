package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PushServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val intent2 = Intent(context, PushMessageHandler::class.java)
        intent2.putExtras(intent)
        intent2.action = intent.action
        PushMessageHandler.addJob(context, intent2)
    }
}
