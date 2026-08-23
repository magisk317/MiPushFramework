package com.xiaomi.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.*

open class Fallbacks @JvmOverloads constructor(
    var host: String = ""
) {
    private val mFallbacks = ArrayList<Fallback>()

    init {
        if (host.isNotEmpty() && host.isEmpty()) {
            throw IllegalArgumentException("the host is empty")
        }
    }

    fun addFallback(fallback: Fallback) {
        synchronized(this) {
            var i = 0
            while (i < mFallbacks.size) {
                if (mFallbacks[i].match(fallback)) {
                    mFallbacks[i] = fallback
                    break
                }
                i++
            }
            if (i >= mFallbacks.size) {
                mFallbacks.add(fallback)
            }
        }
    }

    fun addFallbacks(arrayList: ArrayList<Fallback>) {
        synchronized(this) {
            for (fallback in arrayList) {
                addFallback(fallback)
            }
        }
    }

    fun fromJSON(jSONObject: JsonObject): Fallbacks {
        synchronized(this) {
            host = jSONObject["host"]?.jsonPrimitive?.content.orEmpty()
            val jSONArray = jSONObject["fbs"]?.jsonArray
            if (jSONArray != null) {
                for (element in jSONArray) {
                    val fallback = Fallback(host).fromJSON(element.jsonObject)
                    mFallbacks.add(fallback)
                }
            }
        }
        return this
    }

    val fallback: Fallback?
        get() = synchronized(this) {
            for (size in mFallbacks.indices.reversed()) {
                val fallback = mFallbacks[size]
                if (fallback.match()) {
                    HostManager.getInstance().setCurrentISP(fallback.getISP())
                    return fallback
                }
            }
            null
        }

    val fallbacks: ArrayList<Fallback>
        get() = mFallbacks

    fun purge(z: Boolean) {
        synchronized(this) {
            for (size in mFallbacks.indices.reversed()) {
                val fallback = mFallbacks[size]
                if (z) {
                    if (fallback.isExpired()) {
                        mFallbacks.removeAt(size)
                    }
                } else if (!fallback.isEffective()) {
                    mFallbacks.removeAt(size)
                }
            }
        }
    }

    fun toJSON(): JsonObject {
        synchronized(this) {
            return buildJsonObject {
                put("host", host)
                put("fbs", buildJsonArray {
                    for (fallback in mFallbacks) {
                        add(fallback.toJSON())
                    }
                })
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(host).append("\n")
        for (fallback in mFallbacks) {
            sb.append(fallback)
        }
        return sb.toString()
    }
}
