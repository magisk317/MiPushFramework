package com.xiaomi.mipush.sdk.stat.client

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import org.json.JSONObject

object PushStatClient {
    private var sContext: Context? = null

    fun init(
        context: Context,
        str: String,
        str2: String,
    ) {
        sContext = context.applicationContext
        var str3 = if (TextUtils.isEmpty(str)) "appId can not be null. " else ""
        var str4 = str3
        if (TextUtils.isEmpty(str2)) {
            str4 = "$str3 channel can not be null. "
        }
        if (str4.isNotEmpty()) {
            throw IllegalArgumentException(str4)
        }
        PushStatClientManager.getInstance(sContext!!).init(str, str2)
        if (AppInfoUtils.isAppMainProc(sContext!!)) {
            PushStatClientManager.getInstance(sContext!!).schedule()
        }
    }

    fun record(clientUploadDataItem: ClientUploadDataItem) {
        if (clientUploadDataItem != null) {
            PushStatClientManager.getInstance(sContext!!).record(clientUploadDataItem)
        }
    }

    fun record(str: String) {
        if (TextUtils.isEmpty(str)) return
        PushStatClientManager.getInstance(sContext!!).record(str)
    }

    private fun record(jSONObject: JSONObject) {
        if (jSONObject != null) {
            record(jSONObject.toString())
        }
    }

    fun recordCalculateEvent(str: String, str2: String, j: Long) {
        record(EventDataItem.getCalculateEvent(str, str2, j).toJson())
    }

    fun recordCalculateEvent(
        str: String,
        str2: String,
        j: Long,
        map: Map<String, String>,
    ) {
        record(EventDataItem.getCalculateEvent(str, str2, j, map).toJson())
    }

    fun recordCountEvent(str: String, str2: String) {
        record(EventDataItem.getCountEvent(str, str2).toJson())
    }

    fun recordCountEvent(
        str: String,
        str2: String,
        map: Map<String, String>,
    ) {
        record(EventDataItem.getCountEvent(str, str2, map).toJson())
    }

    fun recordNumericPropertyEvent(str: String, str2: String, j: Long) {
        record(EventDataItem.getNumericPropertyEvent(str, str2, j).toJson())
    }

    fun recordStringPropertyEvent(str: String, str2: String, str3: String) {
        record(EventDataItem.getPropertyEvent(str, str2, str3).toJson())
    }
}
