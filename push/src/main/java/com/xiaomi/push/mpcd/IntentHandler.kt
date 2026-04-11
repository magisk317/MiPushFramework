package com.xiaomi.push.mpcd

import android.content.Context
import android.content.Intent

fun interface IntentHandler {
    fun handle(context: Context, intent: Intent)
}
