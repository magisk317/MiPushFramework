package com.xiaomi.channel.commonutils.android

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class MIUIUtilsAspect {
    @Around(
        "(execution(* com.xiaomi.channel.commonutils.android.MIUIUtils.getProperty(..))" +
            " || execution(* com.xiaomi.channel.commonutils.android.SystemProperties.get(..)))" +
            "&& args(key, ..)"
    )
    @Throws(Throwable::class)
    fun hookAllCountryCodeToCN(joinPoint: ProceedingJoinPoint, key: String?): Any? {
        if (key != null && (key.contains(".region") || key.contains(".country"))) {
            return "CN"
        }
        return joinPoint.proceed()
    }
}
