package com.xiaomi.mipush.sdk

import android.content.Intent
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.xmsf.utils.ConvertUtils
import org.aspectj.lang.JoinPoint

class LogPushMessageProcessorAspect {
    fun processIntent(joinPoint: JoinPoint, intent: Intent) {
        logger.d(joinPoint.signature)
        logger.d("Intent " + ConvertUtils.toJson(intent))
    }

    companion object {
        private val TAG: String = LogPushMessageProcessorAspect::class.java.simpleName
        private val logger: Logger = XLog.tag(TAG).build()
    }
}
