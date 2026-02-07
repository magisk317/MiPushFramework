package com.xiaomi.push.service

import android.content.Intent
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.Global
import com.xiaomi.xmsf.utils.ConvertUtils
import org.aspectj.lang.JoinPoint

class LogXMPushServiceAspect {
    fun onCreate(joinPoint: JoinPoint, pushService: XMPushService) {
        logger.d(joinPoint.signature)
        logger.d("Service started")
    }

    fun onStartCommand(joinPoint: JoinPoint) {
        logger.d(joinPoint.signature)
    }

    fun onStart(joinPoint: JoinPoint, intent: Intent?, startId: Int) {
        logger.d(joinPoint.signature)
        logIntent(intent)
        if (intent != null) {
            Global.MiPushEventListener().receiveFromApplication(intent)
        }
    }

    fun onBind(joinPoint: JoinPoint, intent: Intent?) {
        logger.d(joinPoint.signature)
        logIntent(intent)
    }

    fun onDestroy(joinPoint: JoinPoint) {
        logger.d(joinPoint.signature)
        logger.d("Service stopped")
    }

    fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception?) {
        logger.d(joinPoint.signature)
    }

    fun sendMessage(joinPoint: JoinPoint, intent: Intent?) {
        logger.d(joinPoint.signature)
        if (intent != null) {
            Global.MiPushEventListener().transferToServer(intent)
        }
    }

    private fun logIntent(intent: Intent?) {
        logger.d("Intent ${ConvertUtils.toJson(intent)}")
    }

    companion object {
        private val logger: Logger = XLog.tag(LogXMPushServiceAspect::class.java.simpleName).build()
    }
}
