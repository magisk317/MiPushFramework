package com.xiaomi.clientreport.data

import io.github.magisk317.xposed.logging.XLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EventClientReport : BaseClientReport() {
    @JvmField var eventContent: String? = null
    @JvmField var eventId: String? = null
    @JvmField var eventTime: Long = 0
    @JvmField var eventType: Int = 0

    companion object {
        @JvmStatic
        fun getBlankInstance(): EventClientReport = EventClientReport()
    }

    override fun toJsonObject(): JsonObject? {
        return try {
            val base = super.toJsonObject() ?: return null
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                eventId?.let { put("eventId", it) }
                put("eventType", eventType)
                put("eventTime", eventTime)
                put("eventContent", eventContent ?: "")
            }
        } catch (e: Exception) {
            XLog.e("toJsonObject error", e)
            null
        }
    }

    override fun toJsonString(): String = super.toJsonString()
}
