package com.xiaomi.xmsf

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered
import top.trumeet.common.Constants.APP_ID
import top.trumeet.common.Constants.APP_KEY
import java.util.Objects

class FirstRegister(private val context: Context) : Runnable {
    override fun run() {
        Objects.requireNonNull(context)
        MiPushClient.registerPush(context, APP_ID, APP_KEY)
        if (pushRegistered(context)) {
            MyLog.i("register successed")
        } else {
            PushControllerUtils.registerPush(context, 0)
        }
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            MyLog.e("register push interrupted error", e)
        }
    }
}
