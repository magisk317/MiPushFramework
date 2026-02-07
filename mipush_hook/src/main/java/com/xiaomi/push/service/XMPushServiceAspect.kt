package com.xiaomi.push.service

import android.content.Intent
import com.nihility.Dependencies
import com.nihility.service.XMPushServiceListener
import com.nihility.service.XMPushServiceListener.ConnectionStatus
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before

@Aspect
class XMPushServiceAspect {
    private var listener: XMPushServiceListener? = null

    @Around("execution(* com.xiaomi.push.service.XMPushService.onCreate(..)) && this(pushService)")
    @Throws(Throwable::class)
    fun onCreate(joinPoint: ProceedingJoinPoint, pushService: XMPushService) {
        listener = Dependencies.instance()?.serviceListener(pushService)
        joinPoint.proceed()
        listener?.created()
    }

    @Before("execution(* com.xiaomi.push.service.XMPushService.onStart(..)) && args(intent, startId)")
    fun onStart(joinPoint: JoinPoint, intent: Intent, startId: Int) {
        listener?.start(intent)
    }

    @Before("execution(* com.xiaomi.push.service.XMPushService.onDestroy(..))")
    fun onDestroy(joinPoint: JoinPoint) {
        listener?.destroy()
    }

    @Before("execution(* com.xiaomi.smack.Connection.setConnectionStatus(..)) && args(newStatus, reason, e)")
    fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception) {
        listener?.connectionStatusChanged(ConnectionStatus.of(newStatus))
    }
}
