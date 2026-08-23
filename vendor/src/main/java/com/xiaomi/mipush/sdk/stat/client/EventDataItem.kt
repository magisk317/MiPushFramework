package com.xiaomi.mipush.sdk.stat.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Local legacy stat client model retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class EventDataItem {
    private var mCategory: String? = null
    private var mKey: String? = null
    private val mParams: MutableMap<String, String> = HashMap()
    private var mTimeStamp: Long = 0
    private var mType: String? = null
    private var mValue: String? = null

    constructor()

    constructor(
        str: String,
        str2: String,
        str3: String,
        str4: String,
    ) {
        mCategory = str
        mKey = str2
        mValue = str3
        mType = str4
        timeStamp = System.currentTimeMillis()
    }

    constructor(
        str: String,
        str2: String,
        str3: String,
        str4: String,
        map: Map<String, String>,
    ) {
        category = str
        key = str2
        value = str3
        type = str4
        setParams(map)
        timeStamp = System.currentTimeMillis()
    }

    companion object {
        private const val COUNT = "count"
        private const val EVENT = "event"
        private const val NUMERIC = "numeric"
        private const val PROPERTY = "property"

        fun getCalculateEvent(str: String, str2: String, j: Long): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = j.toString()
                type = COUNT
                timeStamp = System.currentTimeMillis()
            }
        }

        fun getCalculateEvent(
            str: String,
            str2: String,
            j: Long,
            map: Map<String, String>,
        ): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = j.toString()
                type = COUNT
                setParams(map)
                timeStamp = System.currentTimeMillis()
            }
        }

        fun getCountEvent(str: String, str2: String): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = "1"
                type = EVENT
                timeStamp = System.currentTimeMillis()
            }
        }

        fun getCountEvent(
            str: String,
            str2: String,
            map: Map<String, String>,
        ): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = "1"
                type = EVENT
                setParams(map)
                timeStamp = System.currentTimeMillis()
            }
        }

        fun getNumericPropertyEvent(str: String, str2: String, j: Long): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = j.toString()
                type = NUMERIC
                timeStamp = System.currentTimeMillis()
            }
        }

        fun getPropertyEvent(str: String, str2: String, str3: String): EventDataItem {
            return EventDataItem().apply {
                category = str
                key = str2
                value = str3
                type = PROPERTY
                timeStamp = System.currentTimeMillis()
            }
        }

        private fun mapToJson(map: Map<String, String>): String {
            if (map.isEmpty()) return "{}"
            val obj = buildJsonObject {
                for ((k, v) in map) {
                    if (k.isNotEmpty()) {
                        put(k, v)
                    }
                }
            }
            return obj.toString()
        }
    }

    var category: String?
        get() = mCategory
        set(value) {
            mCategory = value
        }

    var key: String?
        get() = mKey
        set(value) {
            mKey = value
        }

    fun setParams(map: Map<String, String>) {
        if (map.isEmpty()) return
        mParams.putAll(map)
    }

    var timeStamp: Long
        get() = mTimeStamp
        set(value) {
            mTimeStamp = value
        }

    var type: String?
        get() = mType
        set(value) {
            mType = value
        }

    var value: String?
        get() = mValue
        set(value) {
            mValue = value
        }

    fun toJson(): JsonObject {
        return buildJsonObject {
            mCategory?.takeIf { it.isNotEmpty() }?.let { put("category", it) }
            mKey?.takeIf { it.isNotEmpty() }?.let { put("key", it) }
            mValue?.takeIf { it.isNotEmpty() }?.let { put("value", it) }
            mType?.takeIf { it.isNotEmpty() }?.let { put("type", it) }
            if (mParams.isNotEmpty()) {
                put("params", mapToJson(mParams))
            }
            put("timeStamp", mTimeStamp)
        }
    }
}
