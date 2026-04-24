package com.xiaomi.clientreport.data
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.clientreport.util.ClientReportUtil
import org.json.JSONException
import org.json.JSONObject

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

    open fun toJson(): JSONObject? {
        return try {
            JSONObject().apply {
                put("production", production)
                put("reportType", reportType)
                put("clientInterfaceId", clientInterfaceId)
                put("os", os)
                put("miuiVersion", miuiVersion)
                put("pkgName", pkgName)
                put("sdkVersion", sdkVersion)
            }
        } catch (e: JSONException) {
            MyLog.e(e)
            null
        }
    }

    open fun toJsonString(): String {
        return toJson()?.toString() ?: ""
    }
}
