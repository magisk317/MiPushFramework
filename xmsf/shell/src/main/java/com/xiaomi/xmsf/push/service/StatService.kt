package com.xiaomi.xmsf.push.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class StatService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
