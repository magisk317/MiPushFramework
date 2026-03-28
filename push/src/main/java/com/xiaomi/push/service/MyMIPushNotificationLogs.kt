package com.xiaomi.push.service

import io.github.aakira.napier.Napier

internal object MyMIPushNotificationLogs {
    class Logger {
        fun i(msg: String) = Napier.i(msg, tag = "MyNotificationHelper")
        fun w(msg: String) = Napier.w(msg, tag = "MyNotificationHelper")
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "Error", t, tag = "MyNotificationHelper")
    }

    val logger = Logger()
}
