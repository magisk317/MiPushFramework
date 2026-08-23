package com.xiaomi.network

import com.xiaomi.channel.commonutils.string.XMStringUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.MalformedURLException
import java.net.URL
import java.util.*

open class Fallback(str: String) {
    companion object {
        protected const val DEFAULT_DOMAIN = "s.mi1.cc"
        const val DEFAULT_UPLOAD_RATIO = 0.1
    }

    @JvmField var city: String? = null
    @JvmField var country: String? = null
    @JvmField var host: String? = null
    @JvmField var ip: String? = null
    @JvmField var isp: String? = null
    private var mISP: String? = null
    @JvmField var networkLabel: String = ""
    @JvmField var province: String? = null
    private var timestamp: Long = System.currentTimeMillis()
    
    @JvmField protected var xforward: String? = null
    private val fallbackHosts = ArrayList<WeightedHost>()
    var percent: Double = 0.1
    var domainName: String = DEFAULT_DOMAIN
    var effectiveDuration: Long = 86400000

    init {
        if (str.isEmpty()) {
            throw IllegalArgumentException("the host is empty")
        }
        fallbackHosts.add(WeightedHost(str, -1))
        networkLabel = HostManager.getActiveNetworkLabel()
        host = str
    }

    private fun deleteWeightedHost(str: String?) {
        synchronized(this) {
            val it = fallbackHosts.iterator()
            while (it.hasNext()) {
                if (it.next().host == str) {
                    it.remove()
                }
            }
        }
    }

    fun accessHost(str: String, i: Int, j: Long, j2: Long, exc: Exception?) {
        accessHost(str, AccessHistory(i, j, j2, exc))
    }

    open fun accessHost(str: String, accessHistory: AccessHistory) {
        synchronized(this) {
            // MiPush SDK 3.7.9 Fallback.accessHost records the history on the matching weighted
            // host. The previous Kotlin port found the entry but never applied it, so successful
            // and failed stock socket attempts could not change fallback ordering.
            fallbackHosts
                .firstOrNull { str == it.host }
                ?.addAccessHistory(accessHistory)
        }
    }

    open fun addHost(weightedHost: WeightedHost) {
        synchronized(this) {
            deleteWeightedHost(weightedHost.host)
            fallbackHosts.add(weightedHost)
        }
    }

    fun addHost(str: String) {
        synchronized(this) {
            addHost(WeightedHost(str))
        }
    }

    fun addPreferredHost(strArr: Array<String>) {
        synchronized(this) {
            for (size in fallbackHosts.indices.reversed()) {
                for (str in strArr) {
                    if (fallbackHosts[size].host == str) {
                        fallbackHosts.removeAt(size)
                        break
                    }
                }
            }
            var i2 = 0
            for (weightedHost in fallbackHosts) {
                if (weightedHost.weight > i2) {
                    i2 = weightedHost.weight
                }
            }
            for (i4 in strArr.indices) {
                addHost(WeightedHost(strArr[i4], strArr.size + i2 - i4))
            }
        }
    }

    fun failedHost(str: String?, j: Long, j2: Long, exc: Exception?) {
        accessHost(str!!, -1, j, j2, exc)
    }

    fun failedUrl(str: String, j: Long, j2: Long, exc: Exception?) {
        try {
            failedHost(URL(str).host, j, j2, exc)
        } catch (e: MalformedURLException) {
        }
    }

    fun fromJSON(jSONObject: JsonObject): Fallback {
        synchronized(this) {
            networkLabel = jSONObject["net"]?.jsonPrimitive?.content.orEmpty()
            effectiveDuration = jSONObject["ttl"]?.jsonPrimitive?.longOrNull ?: 86400000L
            percent = jSONObject["pct"]?.jsonPrimitive?.doubleOrNull ?: 0.1
            timestamp = jSONObject["ts"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
            city = jSONObject["city"]?.jsonPrimitive?.content
            province = jSONObject["prv"]?.jsonPrimitive?.content
            country = jSONObject["cty"]?.jsonPrimitive?.content
            isp = jSONObject["isp"]?.jsonPrimitive?.content
            ip = jSONObject["ip"]?.jsonPrimitive?.content
            host = jSONObject["host"]?.jsonPrimitive?.content
            xforward = jSONObject["xf"]?.jsonPrimitive?.content
            val jSONArray = jSONObject["fbs"]?.jsonArray
            if (jSONArray != null) {
                for (element in jSONArray) {
                    addHost(WeightedHost().fromJSON(element.jsonObject))
                }
            }
        }
        return this
    }

    fun getHosts(): ArrayList<String> {
        return synchronized(this) { getHosts(false) }
    }

    open fun getHosts(z: Boolean): ArrayList<String> {
        synchronized(this) {
            val weightedHostArr = fallbackHosts.toTypedArray()
            Arrays.sort(weightedHostArr)
            val arrayList = ArrayList<String>()
            for (weightedHost in weightedHostArr) {
                if (z) {
                    arrayList.add(weightedHost.host!!)
                } else {
                    val iIndexOf = weightedHost.host!!.indexOf(":")
                    if (iIndexOf != -1) {
                        arrayList.add(weightedHost.host!!.substring(0, iIndexOf))
                    } else {
                        arrayList.add(weightedHost.host!!)
                    }
                }
            }
            return arrayList
        }
    }

    fun getISP(): String {
        return synchronized(this) {
            val cached = mISP
            if (cached != null) {
                cached
            } else {
                val currentIsp = isp ?: "hardcode_isp"
                val strJoin = XMStringUtils.join(arrayOf(currentIsp, province ?: "", city ?: "", country ?: "", ip ?: ""), "_") ?: "unknown_isp"
                mISP = strJoin
                strJoin
            }
        }
    }

    @Throws(MalformedURLException::class)
    fun getUrls(str: String): ArrayList<String> {
        if (str.isEmpty()) {
            throw IllegalArgumentException("the url is empty.")
        }
        val url = URL(str)
        if (url.host != host) {
            throw IllegalArgumentException("the url is not supported by the fallback")
        }
        val arrayList = ArrayList<String>()
        val it = getHosts(true).iterator()
        while (it.hasNext()) {
            val hostStr = it.next()
            val hostObj = Host.parse(hostStr, url.port)
            arrayList.add(URL(url.protocol, hostObj.host, hostObj.port, url.file).toString())
        }
        return arrayList
    }

    internal fun getWeightedHost(): ArrayList<WeightedHost> {
        return fallbackHosts
    }

    open fun isEffective(): Boolean {
        return System.currentTimeMillis() - timestamp < effectiveDuration
    }

    internal fun isExpired(): Boolean {
        var j = 864000000L
        if (864000000L < effectiveDuration) {
            j = effectiveDuration
        }
        val jCurrentTimeMillis = System.currentTimeMillis()
        val elapsed = jCurrentTimeMillis - timestamp
        return elapsed > j || (elapsed > effectiveDuration && networkLabel.startsWith("WIFI-"))
    }

    fun match(): Boolean {
        return networkLabel == HostManager.getActiveNetworkLabel()
    }

    fun match(fallback: Fallback): Boolean {
        return networkLabel == fallback.networkLabel
    }

    fun succeedHost(str: String?, j: Long, j2: Long) {
        accessHost(str!!, 0, j, j2, null)
    }

    fun succeedUrl(str: String, j: Long, j2: Long) {
        try {
            succeedHost(URL(str).host, j, j2)
        } catch (MalformedURLException: MalformedURLException) {
        }
    }

    fun toJSON(): JsonObject {
        synchronized(this) {
            return buildJsonObject {
                put("net", networkLabel)
                put("ttl", effectiveDuration)
                put("pct", percent)
                put("ts", timestamp)
                city?.let { put("city", it) }
                province?.let { put("prv", it) }
                country?.let { put("cty", it) }
                isp?.let { put("isp", it) }
                ip?.let { put("ip", it) }
                host?.let { put("host", it) }
                xforward?.let { put("xf", it) }
                put("fbs", buildJsonArray {
                    for (weightedHost in fallbackHosts) {
                        add(weightedHost.toJSON())
                    }
                })
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(networkLabel).append("\n")
        sb.append(getISP())
        for (weightedHost in fallbackHosts) {
            sb.append("\n").append(weightedHost.toString())
        }
        sb.append("\n")
        return sb.toString()
    }
}
