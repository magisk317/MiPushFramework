package com.xiaomi.push.service.clientReport

import android.content.Context
import android.content.Intent
import com.magisk317.push.hook.HookTraceCompat

class PushClientReportManager private constructor(private val context: Context) {
    private fun collectData() {
        HookTraceCompat.onPushClientReportCollectData()
    }

    private fun reportPerf(packageName: String, code: Int, count: Long, latency: Long, details: String?) {
        collectData()
    }

    protected fun reportEvent(
        packageName: String,
        interfaceId: Int,
        messageId: String?,
        eventCode: Int,
        eventType: String?,
        cmdCode: Int,
        timestamp: Long,
        details: String?
    ) {
        collectData()
    }

    fun reportEvent(packageName: String, intent: Intent, eventCode: Int) {
        collectData()
    }

    fun reportEvent(packageName: String, intent: Intent, eventCode: Int, details: String?) {
        collectData()
    }

    fun reportEvent(packageName: String, interfaceId: String, messageId: String, eventCode: Int) {
        collectData()
    }

    fun reportEvent(
        packageName: String,
        interfaceId: String,
        messageId: String,
        eventCode: Int,
        timestamp: Long,
        details: String?
    ) {
        collectData()
    }

    fun reportEvent(
        packageName: String,
        interfaceId: String,
        messageId: String,
        eventCode: Int,
        details: String?
    ) {
        collectData()
    }

    fun reportEvent4DUPMD(packageName: String, interfaceId: String, messageId: String, reason: String) {
        collectData()
    }

    fun reportEvent4ERROR(packageName: String, intent: Intent, reason: String) {
        collectData()
    }

    fun reportEvent4ERROR(packageName: String, intent: Intent, throwable: Throwable) {
        collectData()
    }

    fun reportEvent4ERROR(packageName: String, interfaceId: String, messageId: String, reason: String) {
        collectData()
    }

    fun reportEvent4ERROR(packageName: String, interfaceId: String, messageId: String, throwable: Throwable) {
        collectData()
    }

    fun reportEvent4NeedDrop(packageName: String, interfaceId: String, messageId: String, reason: String) {
        collectData()
    }

    fun reportPerf(packageName: String, code: Int, count: Long, latency: Long) {
        collectData()
    }

    fun reportPerf(
        packageName: String,
        code: Int,
        details: String,
        cost: Int,
        count: Int,
        latency: Long,
        netFlow: Long
    ) {
        collectData()
    }

    companion object {
        @Volatile
        private var instance: PushClientReportManager? = null

        @JvmStatic
        fun getInstance(context: Context): PushClientReportManager {
            return instance ?: synchronized(this) {
                instance ?: PushClientReportManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
