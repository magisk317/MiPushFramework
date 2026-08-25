package com.xiaomi.xmsf.services.keepalive.strategy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter

class KeepAliveConfigService : Service() {
    private val binder = object : IKeepAliveStrategy.Stub() {
        override fun updateKeepAliveStrategy(configJson: String?) {
            KeepAliveRuntimeAdapter.updateStrategy(this@KeepAliveConfigService, configJson)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }
}
