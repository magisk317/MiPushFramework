package com.xiaomi.channel.commonutils.logger

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import com.xiaomi.channel.commonutils.string.XMStringUtils
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

object MyLog {
    private const val XMSF_PACKAGE_NAME = "com.xiaomi.xmsf"

    const val DEBUG = 1
    const val ERROR = 4
    const val FATAL = 5
    const val INFO = 0
    const val WARN = 2

    private var sContext: Context? = null
    @Volatile
    private var LOG_LEVEL = 2
    @Volatile
    private var debugLoggingEnabled = false
    private var isXMSF = false
    private var DEFAULT_TAG = "XMPush-${Process.myPid()}"
    private var logger: LoggerInterface = DefaultAndroidLogger()
    private val mStartTimes = HashMap<Int, Long>()
    private val mActionNames = HashMap<Int, String>()
    private val NEGATIVE_CODE = -1
    private val mCodeGenerator = AtomicInteger(1)

    private class DefaultAndroidLogger : LoggerInterface {
        private var mTag: String = DEFAULT_TAG

        override fun log(str: String) {
            Log.v(mTag, str)
        }

        override fun log(str: String, th: Throwable) {
            Log.v(mTag, str, th)
        }

        override fun setTag(str: String) {
            mTag = str
        }
    }

    @JvmStatic
    fun e(str: String) {
        log(ERROR, wrapMessage(str))
    }

    @JvmStatic
    fun e(str: String, th: Throwable) {
        log(ERROR, wrapMessage(str), th)
    }

    @JvmStatic
    fun e(th: Throwable) {
        log(ERROR, "", th)
    }

    @JvmStatic
    fun getLogLevel(): Int {
        return LOG_LEVEL
    }

    @JvmStatic
    fun heapInfo(context: Context, str: String) {
        val processMemoryInfo = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getProcessMemoryInfo(intArrayOf(Process.myPid()))
        i("+++Heap Info+++$str total:${processMemoryInfo[0].totalPss}, managed:${processMemoryInfo[0].dalvikPss}, native:${processMemoryInfo[0].nativePss}")
    }

    @JvmStatic
    fun i(str: String) {
        log(INFO, wrapMessage(str))
    }

    @JvmStatic
    fun init(context: Context?) {
        sContext = context
        if (context != null && XMSF_PACKAGE_NAME == context.packageName) {
            isXMSF = true
        }
    }

    private fun jointThreadId(): String {
        return "[Tid:${Process.myTid()}] "
    }

    @JvmStatic
    fun log(level: Int, str: String) {
        if (shouldLog(level)) {
            logger.log(str)
        }
    }

    @JvmStatic
    fun log(level: Int, str: String, th: Throwable) {
        if (shouldLog(level)) {
            logger.log(str, th)
        }
    }

    @JvmStatic
    fun log(level: Int, th: Throwable) {
        if (shouldLog(level)) {
            logger.log("", th)
        }
    }

    @JvmStatic
    fun pe(code: Int?) {
        if (debugLoggingEnabled && LOG_LEVEL <= 1 && code != null && mStartTimes.containsKey(code)) {
            val jLongValue = mStartTimes.remove(code)!!
            val strRemove = mActionNames.remove(code)
            val jCurrentTimeMillis = System.currentTimeMillis()
            logger.log("$strRemove ends in ${jCurrentTimeMillis - jLongValue} ms")
        }
    }

    @JvmStatic
    fun persist(str: String) {
        if (isXMSF) {
            w(str)
        } else {
            Log.i(DEFAULT_TAG, wrapMessage(str))
        }
    }

    @JvmStatic
    fun printCallStack(str: String) {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        printWriter.println(str)
        printWriter.println(
            String.format(
                "Current thread id (%s); thread name (%s)",
                Process.myTid(),
                Thread.currentThread().name
            )
        )
        Throwable("Call stack").printStackTrace(printWriter)
        v(stringWriter.toString())
    }

    @JvmStatic
    fun ps(str: String): Int {
        if (!debugLoggingEnabled || LOG_LEVEL > 1) {
            return NEGATIVE_CODE
        }
        val numValueOf = mCodeGenerator.incrementAndGet()
        mStartTimes[numValueOf] = System.currentTimeMillis()
        mActionNames[numValueOf] = str
        logger.log("$str starts")
        return numValueOf
    }

    @JvmStatic
    fun setLogLevel(level: Int) {
        if (level < 0 || level > 5) {
            log(WARN, "set log level as $level")
        }
        LOG_LEVEL = level
    }

    @JvmStatic
    fun setLogger(loggerInterface: LoggerInterface) {
        logger = loggerInterface
    }

    @JvmStatic
    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled = enabled
    }

    private fun shouldLog(level: Int): Boolean {
        if (level == DEBUG && !debugLoggingEnabled) return false
        return level >= LOG_LEVEL
    }

    @JvmStatic
    fun v(str: String) {
        log(DEBUG, wrapMessage(str))
    }

    @JvmStatic
    fun v(str: String, str2: String) {
        log(DEBUG, wrapMessage(str, str2))
    }

    @JvmStatic
    fun v(objArr: Array<*>) {
        @Suppress("UNCHECKED_CAST")
        log(DEBUG, XMStringUtils.join(objArr as Array<Any?>, ",") ?: "")
    }

    @JvmStatic
    fun w(str: String) {
        log(WARN, wrapMessage(str))
    }

    @JvmStatic
    fun w(str: String, str2: String) {
        log(WARN, wrapMessage(str, str2))
    }

    private fun wrapMessage(str: String): String {
        return jointThreadId() + str
    }

    private fun wrapMessage(str: String, str2: String): String {
        return "${jointThreadId()}[$str] $str2"
    }
}
