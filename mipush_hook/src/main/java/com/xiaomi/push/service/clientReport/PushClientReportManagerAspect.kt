package com.xiaomi.push.service.clientReport

import com.nihility.Hooked
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class PushClientReportManagerAspect {
    @Around("execution(* com.xiaomi.push.service.clientReport.PushClientReportManager.collectData(..))")
    fun doNothingToAvoidTracking(joinPoint: ProceedingJoinPoint) {
        Hooked.mark(PushClientReportManager::class.java.simpleName)
    }
}
