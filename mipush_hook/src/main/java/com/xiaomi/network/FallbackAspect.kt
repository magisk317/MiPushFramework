package com.xiaomi.network

import android.text.TextUtils
import com.nihility.Dependencies
import com.xiaomi.smack.ConnectionConfiguration
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import java.util.ArrayList

@Aspect
class FallbackAspect {
    @Around("execution(* com.xiaomi.network.Fallback.getHosts(..)) && target(fallback) && args(usePort)")
    @Throws(Throwable::class)
    fun useUserDefinedXmppServerHostFirst(
        joinPoint: ProceedingJoinPoint,
        fallback: Fallback,
        usePort: Boolean
    ): Any {
        @Suppress("UNCHECKED_CAST")
        val hosts = joinPoint.proceed() as ArrayList<String>
        if (TextUtils.equals(fallback.host, ConnectionConfiguration.getXmppServerHost())) {
            val configurations = Dependencies.instance()?.configuration()
            val userHost = configurations?.getXMPPServer()
            if (!TextUtils.isEmpty(userHost) && userHost != null) {
                hosts.add(0, userHost)
            }
        }
        return hosts
    }
}
