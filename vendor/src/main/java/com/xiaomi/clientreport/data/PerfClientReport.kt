package com.xiaomi.clientreport.data

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PerfClientReport : BaseClientReport() {
    @JvmField var code: Int = 0
    @JvmField var perfCounts: Long = DEFAULT_VALUE
    @JvmField var perfLatencies: Long = DEFAULT_VALUE

    companion object {
        private const val DEFAULT_VALUE: Long = -1

        @JvmStatic
        fun getBlankInstance(): PerfClientReport = PerfClientReport()
    }

    override fun toJsonObject(): JsonObject? {
        return try {
            val base = super.toJsonObject() ?: return null
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                put("code", code)
                put("perfCounts", perfCounts)
                put("perfLatencies", perfLatencies)
            }
        } catch (e: Exception) {
            Logger.e(e) { "toJsonObject error" }
            null
        }
    }

    override fun toJsonString(): String = super.toJsonString()
}
