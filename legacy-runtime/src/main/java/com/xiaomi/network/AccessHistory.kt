package com.xiaomi.network

import org.json.JSONException
import org.json.JSONObject

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/network/AccessHistory.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class AccessHistory @JvmOverloads constructor(
    var weight: Int = 0,
    var cost: Long = 0L,
    var size: Long = 0L,
    exception: Exception? = null
) {
    var time: Long = System.currentTimeMillis()
    var exception: String? = exception?.javaClass?.simpleName
        private set

    @Throws(JSONException::class)
    fun fromJSON(jsonObject: JSONObject): AccessHistory {
        cost = jsonObject.getLong("cost")
        size = jsonObject.getLong("size")
        time = jsonObject.getLong("ts")
        weight = jsonObject.getInt("wt")
        exception = jsonObject.optString("expt")
        return this
    }

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("cost", cost)
        jsonObject.put("size", size)
        jsonObject.put("ts", time)
        jsonObject.put("wt", weight)
        jsonObject.put("expt", exception)
        return jsonObject
    }
}
