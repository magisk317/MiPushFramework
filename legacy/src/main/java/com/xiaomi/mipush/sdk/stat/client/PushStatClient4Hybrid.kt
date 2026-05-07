package com.xiaomi.mipush.sdk.stat.client

import android.content.Context
import android.text.TextUtils
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter
import org.json.JSONObject

/*
 * Local legacy hybrid stat client facade retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
object PushStatClient4Hybrid {
    private var sContext: Context? = null

    fun init(
        context: Context,
        str: String,
        str2: String,
        str3: String,
        iDbPathGetter: IDbPathGetter,
    ) {
        sContext = context.applicationContext
        var str4 = if (TextUtils.isEmpty(str2)) "appId can not be null. " else ""
        var str5 = str4
        if (TextUtils.isEmpty(str3)) {
            str5 = "$str4 channel can not be null. "
        }
        var str6 = str5
        if (TextUtils.isEmpty(str)) {
            str6 = "$str5 packageName can not be null."
        }
        if (str6.isNotEmpty()) {
            throw IllegalArgumentException(str6)
        }
        PushStatClientManager.getInstance(sContext!!).init(str, str2, str3, iDbPathGetter)
    }

    private fun record(str: String) {
        if (TextUtils.isEmpty(str)) return
        PushStatClientManager.getInstance(sContext!!).record(str)
    }

    private fun record(jSONObject: JSONObject) {
        PushStatClientManager.getInstance(sContext!!).record(jSONObject.toString())
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

    fun schedule() {
        PushStatClientManager.getInstance(sContext!!).schedule()
    }
}
