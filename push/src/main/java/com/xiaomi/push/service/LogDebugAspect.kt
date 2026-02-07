package com.xiaomi.push.service

import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.Global
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.utils.ConvertUtils
import org.aspectj.lang.ProceedingJoinPoint

class LogDebugAspect {
    fun logger(joinPoint: ProceedingJoinPoint): Any? {
        if (!Global.ConfigCenter().isDebugMode) {
            return joinPoint.proceed()
        }

        val prefix = getPrefix(joinPoint, indentLevel.get() ?: 0)
        logger.d(prefix)

        increaseIndent()
        val ret: Any?
        try {
            ret = joinPoint.proceed()
        } catch (e: Throwable) {
            logThrowable(e, prefix)
            throw e
        } finally {
            decreaseIndent()
        }

        logResult(ret, prefix)
        return ret
    }

    private fun logThrowable(e: Throwable, prefix: String) {
        logger.d("$prefix -> [$e]")
    }

    private fun logResult(ret: Any?, prefix: String) {
        if (ret is XmPushActionContainer) {
            logger.d("$prefix -> [${ConvertUtils.toJson(ret)}]")
        } else {
            logger.d("$prefix -> [$ret]")
        }
    }

    private fun decreaseIndent() {
        indentLevel.set((indentLevel.get() ?: 0) - 1)
    }

    private fun increaseIndent() {
        indentLevel.set((indentLevel.get() ?: 0) + 1)
    }

    private fun getPrefix(joinPoint: ProceedingJoinPoint, curLevel: Int): String {
        var prefix = "|\t".repeat(curLevel)
        prefix += if (joinPoint.`this` != null) {
            "${joinPoint.`this`.javaClass.simpleName} ${joinPoint.toLongString()}"
        } else {
            joinPoint.toLongString()
        }
        return prefix
    }

    companion object {
        private val logger: Logger = XLog.tag(LogDebugAspect::class.java.simpleName).build()
        private val indentLevel = object : ThreadLocal<Int>() {
            override fun initialValue(): Int = 0
        }
    }
}
