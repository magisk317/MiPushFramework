package com.xiaomi.mipush.sdk

import android.content.pm.PackageInfo
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import org.aspectj.lang.JoinPoint

class ManifestCheckerAspectLog {
    fun logCheckServices(joinPoint: JoinPoint, pkgInfo: PackageInfo) {
        logger.d(joinPoint.signature)
        logger.d(pkgInfo)
    }

    companion object {
        private val TAG: String = ManifestCheckerAspect::class.java.simpleName
        private val logger: Logger = XLog.tag(TAG).build()
    }
}
