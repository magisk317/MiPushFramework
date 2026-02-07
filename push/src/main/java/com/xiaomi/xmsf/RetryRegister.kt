package com.xiaomi.xmsf

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered
import top.trumeet.common.Constants.APP_ID
import top.trumeet.common.Constants.APP_KEY

class RetryRegister(private val context: Context, private val tryRegisterCount: Int) : Runnable {
    override fun run() {
        if (pushRegistered(context)) {
            MyLog.i("register successed, stop retry")
            return
        }
        MiPushClient.registerPush(context, APP_ID, APP_KEY)
        val retry = tryRegisterCount + 1
        if (retry <= 10) {
            MyLog.i("register not successed, register again, retryIndex: $retry")
            PushControllerUtils.registerPush(context, retry)
            return
        }
        MyLog.i("register not successed, but retry to many times, stop retry")
    }
}
