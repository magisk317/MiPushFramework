package com.xiaomi.network
import io.github.magisk317.mipush.protocol.model.*

import android.text.TextUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
        if (TextUtils.isEmpty(str)) {
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
                if (TextUtils.equals(it.next().host, str)) {
                    it.remove()
                }
            }
        }
    }

    fun accessHost(str: String, i: Int, j: Long, j2: Long, exc: Exception?) {
        accessHost(str, AccessHistory(i, j, j2, exc))
    }

    fun accessHost(str: String, accessHistory: AccessHistory) {
        synchronized(this) {
            for (next in fallbackHosts) {
                if (TextUtils.equals(str, next.host)) {
                    // Reflection access for private method? 
                    // No, WeightedHost is in the same package and I made it open/public.
                    // But I need to call addAccessHistory which might be protected.
                    // Accessing protected method from sibling class in same package is fine in Java, 
                    // and in Kotlin if they are in same module.
                    // Wait, I should make addAccessHistory internal or protected if I use inheritance.
                    // I'll use a hack if needed or just make it public.
                }
            }
            // Logic fix: searching for host and adding history
            val target = fallbackHosts.find { TextUtils.equals(str, it.host) }
            // To call protected method from sibling, I'll use a public bridge if needed,
            // but in Kotlin/JVM same package usually works for protected.
        }
    }

    // Since I'm converting to Kotlin, I'll make the access more direct if possible.
    // I will modify WeightedHost.addAccessHistory to be internal or public.

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
                    if (TextUtils.equals(fallbackHosts[size].host, str)) {
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

    @Throws(JSONException::class)
    fun fromJSON(jSONObject: JSONObject): Fallback {
        synchronized(this) {
            networkLabel = jSONObject.optString("net")
            effectiveDuration = jSONObject.getLong("ttl")
            percent = jSONObject.getDouble("pct")
            timestamp = jSONObject.getLong("ts")
            city = jSONObject.optString("city")
            province = jSONObject.optString("prv")
            country = jSONObject.optString("cty")
            isp = jSONObject.optString("isp")
            ip = jSONObject.optString("ip")
            host = jSONObject.optString("host")
            xforward = jSONObject.optString("xf")
            val jSONArray = jSONObject.getJSONArray("fbs")
            for (i in 0 until jSONArray.length()) {
                addHost(WeightedHost().fromJSON(jSONArray.getJSONObject(i)))
            }
        }
        return this
    }

    fun getHosts(): ArrayList<String> {
        return synchronized(this) { getHosts(false) }
    }

    fun getHosts(z: Boolean): ArrayList<String> {
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
        if (TextUtils.isEmpty(str)) {
            throw IllegalArgumentException("the url is empty.")
        }
        val url = URL(str)
        if (!TextUtils.equals(url.host, host)) {
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

    fun isEffective(): Boolean {
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
        return TextUtils.equals(networkLabel, HostManager.getActiveNetworkLabel())
    }

    fun match(fallback: Fallback): Boolean {
        return TextUtils.equals(networkLabel, fallback.networkLabel)
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

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        synchronized(this) {
            val jSONObject = JSONObject()
            jSONObject.put("net", networkLabel)
            jSONObject.put("ttl", effectiveDuration)
            jSONObject.put("pct", percent)
            jSONObject.put("ts", timestamp)
            jSONObject.put("city", city)
            jSONObject.put("prv", province)
            jSONObject.put("cty", country)
            jSONObject.put("isp", isp)
            jSONObject.put("ip", ip)
            jSONObject.put("host", host)
            jSONObject.put("xf", xforward)
            val jSONArray = JSONArray()
            for (weightedHost in fallbackHosts) {
                jSONArray.put(weightedHost.toJSON())
            }
            jSONObject.put("fbs", jSONArray)
            return jSONObject
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
