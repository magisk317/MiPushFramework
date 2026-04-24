package com.xiaomi.network
import io.github.magisk317.mipush.protocol.model.*

import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

open class Fallbacks @JvmOverloads constructor(
    var host: String = ""
) {
    private val mFallbacks = ArrayList<Fallback>()

    init {
        if (host.isNotEmpty() && TextUtils.isEmpty(host)) {
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

    @Throws(JSONException::class)
    fun fromJSON(jSONObject: JSONObject): Fallbacks {
        synchronized(this) {
            host = jSONObject.getString("host")
            val jSONArray = jSONObject.getJSONArray("fbs")
            for (i in 0 until jSONArray.length()) {
                val fallback = Fallback(host).fromJSON(jSONArray.getJSONObject(i))
                mFallbacks.add(fallback)
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

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        synchronized(this) {
            val jSONObject = JSONObject()
            jSONObject.put("host", host)
            val jSONArray = JSONArray()
            for (fallback in mFallbacks) {
                jSONArray.put(fallback.toJSON())
            }
            jSONObject.put("fbs", jSONArray)
            return jSONObject
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
