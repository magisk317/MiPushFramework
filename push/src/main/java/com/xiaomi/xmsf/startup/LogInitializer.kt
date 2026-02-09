package com.xiaomi.xmsf.startup

import android.content.Context
import androidx.startup.Initializer
import com.xiaomi.xmsf.utils.LogUtils

class LogInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        LogUtils.init(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
