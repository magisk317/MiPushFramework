package com.xiaomi.clientreport.manager

import com.nihility.Hooked
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class ClientReportClientAspect {
    @Around("execution(* com.xiaomi.clientreport.manager.ClientReportClient.init(..))")
    fun doNothingToAvoidTracking(joinPoint: ProceedingJoinPoint) {
        Hooked.mark(ClientReportClient::class.java.simpleName)
    }
}
