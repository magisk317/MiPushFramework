package com.xiaomi.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

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

    fun fromJSON(jsonObject: JsonObject): AccessHistory {
        cost = jsonObject["cost"]?.jsonPrimitive?.longOrNull ?: 0L
        size = jsonObject["size"]?.jsonPrimitive?.longOrNull ?: 0L
        time = jsonObject["ts"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        weight = jsonObject["wt"]?.jsonPrimitive?.intOrNull ?: 0
        exception = jsonObject["expt"]?.jsonPrimitive?.content
        return this
    }

    fun toJSON(): JsonObject {
        return buildJsonObject {
            put("cost", cost)
            put("size", size)
            put("ts", time)
            put("wt", weight)
            exception?.let { put("expt", it) }
        }
    }
}
