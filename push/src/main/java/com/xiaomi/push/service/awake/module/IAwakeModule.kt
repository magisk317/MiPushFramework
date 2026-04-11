package com.xiaomi.push.service.awake.module

import android.content.Context
import android.content.Intent

interface IAwakeModule {
    fun doAwake(context: Context, awakeInfo: AwakeInfo)

    fun doSendAwakeResult(context: Context, intent: Intent, str: String)
}
