package com.xiaomi.mipush.sdk.help

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import com.xiaomi.mipush.sdk.AwakeHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HelpService : Service() {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executorService.execute {
            handleIntent(intent)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executorService.shutdown()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (TextUtils.isEmpty(intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO))) return
        AwakeHelper.doAWork(this, intent, null)
    }
}
