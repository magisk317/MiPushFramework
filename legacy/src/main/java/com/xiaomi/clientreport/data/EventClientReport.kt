package com.xiaomi.clientreport.data
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import org.json.JSONException
import org.json.JSONObject

class EventClientReport : BaseClientReport() {
    @JvmField var eventContent: String? = null
    @JvmField var eventId: String? = null
    @JvmField var eventTime: Long = 0
    @JvmField var eventType: Int = 0

    companion object {
        @JvmStatic
        fun getBlankInstance(): EventClientReport = EventClientReport()
    }

    override fun toJson(): JSONObject? {
        return try {
            val json = super.toJson() ?: return null
            json.put("eventId", eventId)
            json.put("eventType", eventType)
            json.put("eventTime", eventTime)
            json.put("eventContent", eventContent ?: "")
            json
        } catch (e: JSONException) {
            MyLog.e(e)
            null
        }
    }

    override fun toJsonString(): String = super.toJsonString()
}
