package com.xiaomi.push.service

import android.net.Uri
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class PushHostManagerFactoryAspect {
    @Around("execution(* com.xiaomi.push.service.PushHostManagerFactory.GslbHttpGet.doGet(..)) && args(url)")
    @Throws(Throwable::class)
    fun addCountryCodeToConfigurationUrl(joinPoint: ProceedingJoinPoint, url: String): Any? {
        var newUrl = url
        newUrl = newUrl.replaceFirst("&countrycode=[^&]+".toRegex(), "")
        newUrl = newUrl.replaceFirst("\\?countrycode=[^&]+".toRegex(), "?")
        val uri = Uri.parse(newUrl).buildUpon()
        uri.appendQueryParameter("countrycode", "CN")
        return joinPoint.proceed(arrayOf(uri.toString()))
    }
}
