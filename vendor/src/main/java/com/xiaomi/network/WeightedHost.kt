package com.xiaomi.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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

    fun fromJSON(jSONObject: JsonObject): WeightedHost {
        synchronized(this) {
            touchedTime = jSONObject["tt"]?.jsonPrimitive?.longOrNull ?: 0L
            weight = jSONObject["wt"]?.jsonPrimitive?.intOrNull ?: 0
            host = jSONObject["host"]?.jsonPrimitive?.content
            val jSONArray = jSONObject["ah"]?.jsonArray
            if (jSONArray != null) {
                for (element in jSONArray) {
                    accessHistories.add(AccessHistory().fromJSON(element.jsonObject))
                }
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

    fun toJSON(): JsonObject {
        synchronized(this) {
            return buildJsonObject {
                put("tt", touchedTime)
                put("wt", weight)
                host?.let { put("host", it) }
                put("ah", buildJsonArray {
                    for (history in accessHistories) {
                        add(history.toJSON())
                    }
                })
            }
        }
    }

    override fun toString(): String {
        return "$host:$weight"
    }
}
