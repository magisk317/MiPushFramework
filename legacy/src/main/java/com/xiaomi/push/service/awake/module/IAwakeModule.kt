package com.xiaomi.push.service.awake.module
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent

interface IAwakeModule {
    fun doAwake(context: Context, awakeInfo: AwakeInfo)

    fun doSendAwakeResult(context: Context, intent: Intent, str: String)
}
