package com.xiaomi.channel.commonutils.logger

import android.content.Context
import co.touchlab.kermit.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Modern Kermit-backed compatibility implementation of Xiaomi's legacy MyLog API.
 * Decouples from android.util.Log and supports pure JVM testing seamlessly.
 */
object MyLog {
    const val DEBUG = 1
    const val ERROR = 4
    const val FATAL = 5
    const val INFO = 0
    const val WARN = 2

    private const val DEFAULT_TAG = "XMPush"

    @Volatile
    private var LOG_LEVEL = 2

    @Volatile
    private var debugLoggingEnabled = false

    private val mStartTimes = ConcurrentHashMap<Int, Long>()
    private val mActionNames = ConcurrentHashMap<Int, String>()
    private const val NEGATIVE_CODE = -1
    private val mCodeGenerator = AtomicInteger(1)

    private val logger = Logger.withTag(DEFAULT_TAG)

    @JvmStatic
    fun e(str: String) {
        if (shouldLog(ERROR)) logger.e { str }
    }

    @JvmStatic
    fun e(str: String, th: Throwable) {
        if (shouldLog(ERROR)) logger.e(th) { str }
    }

    @JvmStatic
    fun e(th: Throwable) {
        if (shouldLog(ERROR)) logger.e(th) { th.message ?: "" }
    }

    @JvmStatic
    fun getLogLevel(): Int = LOG_LEVEL

    @JvmStatic
    fun heapInfo(context: Context, str: String) {
        i("+++Heap Info+++$str")
    }

    @JvmStatic
    fun i(str: String) {
        if (shouldLog(INFO)) logger.i { str }
    }

    @JvmStatic
    fun init(context: Context?) = Unit

    @JvmStatic
    fun log(level: Int, str: String) {
        if (!shouldLog(level)) return
        when (level) {
            DEBUG -> logger.d { str }
            WARN -> logger.w { str }
            ERROR, FATAL -> logger.e { str }
            else -> logger.i { str }
        }
    }

    @JvmStatic
    fun log(level: Int, str: String, th: Throwable) {
        if (!shouldLog(level)) return
        when (level) {
            DEBUG -> logger.d(th) { str }
            WARN -> logger.w(th) { str }
            ERROR, FATAL -> logger.e(th) { str }
            else -> logger.i(th) { str }
        }
    }

    @JvmStatic
    fun log(level: Int, th: Throwable) {
        log(level, th.message ?: "", th)
    }

    @JvmStatic
    fun pe(code: Int?) {
        if (debugLoggingEnabled && LOG_LEVEL <= 1 && code != null && mStartTimes.containsKey(code)) {
            val jLongValue = mStartTimes.remove(code) ?: return
            val strRemove = mActionNames.remove(code)
            val jCurrentTimeMillis = System.currentTimeMillis()
            logger.d { "$strRemove ends in ${jCurrentTimeMillis - jLongValue} ms" }
        }
    }

    @JvmStatic
    fun persist(str: String) {
        logger.i { str }
    }

    @JvmStatic
    fun printCallStack(str: String) {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        printWriter.println(str)
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
        logger.d { "$str starts" }
        return numValueOf
    }

    @JvmStatic
    fun setLogLevel(level: Int) {
        LOG_LEVEL = level
    }

    @JvmStatic
    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled = enabled
    }

    @JvmStatic
    fun setLogger(loggerInterface: LoggerInterface) = Unit

    @JvmStatic
    fun v(str: String) {
        if (shouldLog(DEBUG)) logger.d { str }
    }

    @JvmStatic
    fun v(str: String, str2: String) {
        if (shouldLog(DEBUG)) logger.d { "[$str] $str2" }
    }

    @JvmStatic
    fun v(objArr: Array<*>) {
        if (shouldLog(DEBUG)) logger.d { objArr.joinToString(",") }
    }

    @JvmStatic
    fun w(str: String) {
        if (shouldLog(WARN)) logger.w { str }
    }

    @JvmStatic
    fun w(str: String, str2: String) {
        if (shouldLog(WARN)) logger.w { "[$str] $str2" }
    }

    private fun shouldLog(level: Int): Boolean {
        if (level == DEBUG && !debugLoggingEnabled) return false
        return level >= LOG_LEVEL
    }
}
