package com.xiaomi.xms.subscribe_remind

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Stock-compatible Subscribe Remind service boundary.
 *
 * The feature is intentionally disabled until its server protocol and persisted state are
 * implemented. Returning the stock unsupported result keeps the client contract deterministic.
 */
class SubscribeRemindService : Service() {
    private val binder = object : ISubscribeRemind.Stub() {
        override fun subscribeRemind(bundle: Bundle?, callback: IResultCallback?) = unsupported(callback)

        override fun cancelSubscribeRemind(bundle: Bundle?, callback: IResultCallback?) = unsupported(callback)

        override fun deviceInfoReport(bundle: Bundle?, callback: IResultCallback?) = unsupported(callback)

        override fun send(bundle: Bundle?, callback: IResultCallback?) = unsupported(callback)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun unsupported(callback: IResultCallback?) {
        runCatching { callback?.onResult(ERROR_NOT_SUPPORT, Bundle()) }
    }

    private companion object {
        const val ERROR_NOT_SUPPORT = -100
    }
}
