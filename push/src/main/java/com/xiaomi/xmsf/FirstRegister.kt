package com.xiaomi.xmsf

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered
import top.trumeet.common.Constants.APP_ID
import top.trumeet.common.Constants.APP_KEY
import java.util.Objects

class FirstRegister(private val context: Context) : Runnable {
    private val TAG = "FirstRegister"
    private val logger = object {
        fun i(msg: String) = Napier.i(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    override fun run() {
        Objects.requireNonNull(context)
        MiPushClient.registerPush(context, APP_ID, APP_KEY)
        if (pushRegistered(context)) {
            logger.i("register successed")
        } else {
            PushControllerUtils.registerPush(context, 0)
        }
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            logger.e("register push interrupted error", e)
        }
    }
}
