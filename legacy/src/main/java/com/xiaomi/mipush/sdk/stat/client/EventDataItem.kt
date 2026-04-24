package com.xiaomi.mipush.sdk.stat.client
import io.github.magisk317.mipush.protocol.model.*

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

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
            val jSONObject = JSONObject()
            if (map.isNotEmpty()) {
                try {
                    for (str in map.keys) {
                        if (!TextUtils.isEmpty(str)) {
                            jSONObject.put(str, "${map[str]}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return jSONObject.toString()
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

    fun toJson(): JSONObject {
        val jSONObject = JSONObject()
        try {
            if (!TextUtils.isEmpty(mCategory)) {
                jSONObject.put("category", mCategory)
            }
            if (!TextUtils.isEmpty(mKey)) {
                jSONObject.put("key", mKey)
            }
            if (!TextUtils.isEmpty(mValue)) {
                jSONObject.put("value", mValue)
            }
            if (!TextUtils.isEmpty(mType)) {
                jSONObject.put("type", mType)
            }
            if (mParams.isNotEmpty()) {
                jSONObject.put("params", mapToJson(mParams))
            }
            jSONObject.put("timeStamp", mTimeStamp)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jSONObject
    }
}
