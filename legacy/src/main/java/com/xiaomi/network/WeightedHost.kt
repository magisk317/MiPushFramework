package com.xiaomi.network

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

open class WeightedHost @JvmOverloads constructor(
    @JvmField var host: String? = null,
    @JvmField var weight: Int = 0
) : Comparable<WeightedHost> {
    
    private val accessHistories = LinkedList<AccessHistory>()
    private var touchedTime: Long = 0

    companion object {
        private const val HISTORY_SIZE = 30
    }

    internal fun addAccessHistory(accessHistory: AccessHistory?) {
        synchronized(this) {
            if (accessHistory != null) {
                accessHistories.add(accessHistory)
                val weightVal = accessHistory.weight
                if (weightVal > 0) {
                    weight += weightVal
                } else {
                    var i = 0
                    var index = accessHistories.size - 1
                    while (index >= 0 && accessHistories[index].weight < 0) {
                        i++
                        index--
                    }
                    weight += weightVal * i
                }
                if (accessHistories.size > HISTORY_SIZE) {
                    weight -= accessHistories.remove().weight
                }
            }
        }
    }

    override fun compareTo(other: WeightedHost): Int {
        return other.weight - this.weight
    }

    @Throws(JSONException::class)
    fun fromJSON(jSONObject: JSONObject): WeightedHost {
        synchronized(this) {
            touchedTime = jSONObject.getLong("tt")
            weight = jSONObject.getInt("wt")
            host = jSONObject.getString("host")
            val jSONArray = jSONObject.getJSONArray("ah")
            for (i in 0 until jSONArray.length()) {
                accessHistories.add(AccessHistory().fromJSON(jSONArray.getJSONObject(i)))
            }
        }
        return this
    }

    fun getAccessHistory(): ArrayList<AccessHistory> {
        synchronized(this) {
            return ArrayList(accessHistories)
        }
    }

    fun getUnTouchedAccessHistory(): ArrayList<AccessHistory> {
        synchronized(this) {
            val arrayList = ArrayList<AccessHistory>()
            for (accessHistory in accessHistories) {
                if (accessHistory.time > touchedTime) {
                    arrayList.add(accessHistory)
                }
            }
            touchedTime = System.currentTimeMillis()
            return arrayList
        }
    }

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        synchronized(this) {
            val jSONObject = JSONObject()
            jSONObject.put("tt", touchedTime)
            jSONObject.put("wt", weight)
            jSONObject.put("host", host)
            val jSONArray = JSONArray()
            for (history in accessHistories) {
                jSONArray.put(history.toJSON())
            }
            jSONObject.put("ah", jSONArray)
            return jSONObject
        }
    }

    override fun toString(): String {
        return "$host:$weight"
    }
}
