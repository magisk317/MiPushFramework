package com.xiaomi.clientreport.data

import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.clientreport.util.ClientReportUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class BaseClientReport {
    @JvmField var clientInterfaceId: String? = null
    private var pkgName: String? = null
    @JvmField var production: Int = 0
    @JvmField var reportType: Int = 0
    private var sdkVersion: String? = null
    private var os: String = ClientReportUtil.os
    private var miuiVersion: String = MIUIUtils.getMIUIType()

    val packageName: String?
        get() = pkgName

    fun setAppPackageName(str: String) {
        pkgName = str
    }

    fun setSdkVersion(str: String) {
        sdkVersion = str
    }

    open fun toJsonObject(): JsonObject? {
        return try {
            buildJsonObject {
                put("production", production)
                put("reportType", reportType)
                clientInterfaceId?.let { put("clientInterfaceId", it) }
                put("os", os)
                put("miuiVersion", miuiVersion)
                pkgName?.let { put("pkgName", it) }
                sdkVersion?.let { put("sdkVersion", it) }
            }
        } catch (e: Exception) {
            Logger.e(e) { "toJsonObject error" }
            null
        }
    }

    open fun toJsonString(): String {
        return toJsonObject()?.toString() ?: ""
    }
}
