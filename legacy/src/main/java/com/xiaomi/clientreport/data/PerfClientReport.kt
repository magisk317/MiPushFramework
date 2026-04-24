package com.xiaomi.clientreport.data
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import org.json.JSONException
import org.json.JSONObject

class PerfClientReport : BaseClientReport() {
    @JvmField var code: Int = 0
    @JvmField var perfCounts: Long = DEFAULT_VALUE
    @JvmField var perfLatencies: Long = DEFAULT_VALUE

    companion object {
        private const val DEFAULT_VALUE: Long = -1

        @JvmStatic
        fun getBlankInstance(): PerfClientReport = PerfClientReport()
    }

    override fun toJson(): JSONObject? {
        return try {
            val json = super.toJson() ?: return null
            json.put("code", code)
            json.put("perfCounts", perfCounts)
            json.put("perfLatencies", perfLatencies)
            json
        } catch (e: JSONException) {
            MyLog.e(e)
            null
        }
    }

    override fun toJsonString(): String = super.toJsonString()
}
