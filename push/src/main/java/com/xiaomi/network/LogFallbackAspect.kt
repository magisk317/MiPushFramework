package com.xiaomi.network

import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.google.gson.GsonBuilder
import org.aspectj.lang.JoinPoint

class LogFallbackAspect {
    fun logFallback(joinPoint: JoinPoint, fallback: Fallback, usePort: Boolean) {
        logger.d(joinPoint.signature)
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        logger.d("Fallback " + gson.toJsonTree(fallback))
    }

    companion object {
        private val TAG: String = LogFallbackAspect::class.java.simpleName
        private val logger: Logger = XLog.tag(TAG).build()
    }
}
