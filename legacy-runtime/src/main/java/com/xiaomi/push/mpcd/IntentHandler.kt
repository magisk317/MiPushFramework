package com.xiaomi.push.mpcd

import android.content.Context

interface IntentHandler {
    fun handle(context: Context, intent: android.content.Intent)

    companion object {
        operator fun invoke(handler: (Context, android.content.Intent) -> Unit): IntentHandler {
            return object : IntentHandler {
                override fun handle(context: Context, intent: android.content.Intent) = handler(context, intent)
            }
        }
    }
}
