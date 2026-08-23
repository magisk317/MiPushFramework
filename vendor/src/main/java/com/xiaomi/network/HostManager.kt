package com.xiaomi.network

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TransportInfo
import android.net.Uri
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Process
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.BasicNameValuePair
import com.xiaomi.channel.commonutils.network.NameValuePair
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.common.logger.thrift.mfs.HostInfo
import com.xiaomi.common.logger.thrift.mfs.HttpApi
import com.xiaomi.common.logger.thrift.mfs.LandNodeInfo
import com.xiaomi.common.logger.thrift.mfs.Location
import com.xiaomi.push.service.*
import com.xiaomi.push.service.module.PushChannelRegion
import com.xiaomi.slim.Blob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*

open class HostManager @JvmOverloads constructor(
    context: Context,
    hostFilter: HostFilter? = null,
    httpGet: HttpGet? = null,
    userId: String = Blob.CLIENT_PING_ID,
    appName: String? = null,
    appVersion: String? = null
) {
    companion object {
        private const val BUCKET_URL = "https://%1\$s/gslb/?ver=4.0"
        private const val HOST = "resolver.msg.xiaomi.net"
        private const val HOST_GLOBAL = "resolver.msg.global.xiaomi.net"

        @JvmStatic
        var factory: HostManagerFactory? = null
            @Synchronized set(value) {
                field = value
                sInstance = null
            }

        @JvmStatic
        protected var sAppContext: Context? = null
        private var sAppName: String? = null
        private var sAppVersion: String? = null
        private var sInstance: HostManager? = null
            
        @JvmField
        protected val sReservedHosts = HashMap<String, Fallback>()
        
        @JvmField
        protected var hostLoaded = false

        @JvmStatic
        fun addReservedHost(host: String, target: String) {
            synchronized(sReservedHosts) {
                val fallback = sReservedHosts.getOrPut(host) {
                    Fallback(host).apply {
                        effectiveDuration = 604800000L
                    }
                }
                fallback.addHost(target)
            }
        }
        private val sHostFilter = HostFilter { true }

        @JvmStatic
        fun getActiveNetworkLabel(): String {
            val cm = sAppContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) return "unknown"
            
            return try {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null) {
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            var bssid = "WIFI"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val transportInfo = caps.transportInfo
                                if (transportInfo is WifiInfo) {
                                    bssid = "WIFI-${transportInfo.bssid}"
                                }
                            }
                            return bssid
                        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            return "CELLULAR"
                        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            return "ETHERNET"
                        }
                    }
                }
                "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }

        @JvmStatic
        fun getInstance(): HostManager {
            synchronized(HostManager::class.java) {
                checkNotNull(sInstance) { "the host manager is not initialized yet." }
                return sInstance!!
            }
        }

        @JvmStatic
        fun init(
            context: Context,
            hostFilter: HostFilter?,
            httpGet: HttpGet?,
            userId: String,
            appName: String? = null,
            appVersion: String? = null
        ) {
            synchronized(HostManager::class.java) {
                if (sInstance != null) return
                sAppContext = context.applicationContext
                sAppName = appName
                sAppVersion = appVersion
                val currentFactory = factory
                if (currentFactory == null) {
                    sInstance = HostManager(context, hostFilter, httpGet, userId, appName, appVersion)
                } else {
                    sInstance = currentFactory.createHostManager(context, hostFilter, httpGet, userId)
                }
            }
        }

        @JvmStatic
        fun obfuscate(str: String): String {
            return try {
                val length = str.length
                val bytes = str.toByteArray(charset("UTF-8"))
                for (i in bytes.indices) {
                    if (bytes[i].toInt() and 240 != 240) {
                        val b = bytes[i].toInt()
                        bytes[i] = (b and 240 or (b and 15 xor (b shr 4 + length and 15))).toByte()
                    }
                }
                String(bytes)
            } catch (e: UnsupportedEncodingException) {
                str
            }
        }
    }

    private val MAX_REQUEST_FAILURE_CNT: Long = 15L
    private var currentISP: String = "isp_prov_city_country_ip"
    private var lastRemoteRequestTimestamp: Long = 0L
    private var remoteRequestFailureCount: Long = 0L
    
    @JvmField
    protected var mHostsMapping = HashMap<String, Fallbacks>()
    
    @JvmField
    protected var sHostFilter: HostFilter
    
    @JvmField
    protected var sHttpGetter: HttpGet? = null
    private var sUserId: String = Blob.CLIENT_PING_ID

    init {
        sHttpGetter = httpGet
        sHostFilter = hostFilter ?: HostFilter { true }
        sUserId = userId
        sAppName = appName ?: context.packageName
        sAppVersion = appVersion ?: getVersionName()
    }

    interface HostManagerFactory {
        fun createHostManager(
            context: Context,
            hostFilter: HostFilter?,
            httpGet: HttpGet?,
            userId: String
        ): HostManager
    }

    fun interface HttpGet {
        fun doGet(url: String): String?
    }

    private fun getVersionName(): String {
        return try {
            val packageInfo = sAppContext?.packageManager?.getPackageInfo(sAppContext!!.packageName, 16384)
            packageInfo?.versionName ?: Blob.CLIENT_PING_ID
        } catch (e: Exception) {
            Blob.CLIENT_PING_ID
        }
    }

    private fun processNetwork(str: String?): String {
        return if (str.isNullOrEmpty()) "unknown" else if (str.startsWith("WIFI")) "WIFI" else str
    }

    @Throws(Throwable::class)
    private fun requestRemoteFallbacks(arrayList: ArrayList<String>): ArrayList<Fallback?> {
        purge()
        synchronized(mHostsMapping) {
            checkHostMapping()
            for (str in mHostsMapping.keys) {
                if (!arrayList.contains(str)) {
                    arrayList.add(str)
                }
            }
        }
        var z = sReservedHosts.isEmpty()
        synchronized(sReservedHosts) {
            val iterator = sReservedHosts.values.iterator()
            while (iterator.hasNext()) {
                val fallback = iterator.next()
                if (!fallback.isEffective()) {
                    z = true
                    iterator.remove()
                }
            }
        }
        val host = getHost()
        if (!arrayList.contains(host)) {
            arrayList.add(host)
        }
        val localFallback = getLocalFallback(host)
        if (localFallback != null && localFallback.isEffective()) {
            return arrayListOf(localFallback)
        }
        val arrayList2 = ArrayList<Fallback?>(arrayList.size)
        for (i in arrayList.indices) {
            arrayList2.add(null)
        }
        try {
            val str2 = if (Network.isWIFIConnected(sAppContext)) "wifi" else "wap"
            val remoteFallbackJSON = getRemoteFallbackJSON(arrayList, str2, sUserId, z)
            if (!remoteFallbackJSON.isNullOrEmpty()) {
                val jSONObject = Json.parseToJsonElement(remoteFallbackJSON).jsonObject
                MyLog.i(remoteFallbackJSON)
                if ("OK".equals(jSONObject["S"]?.jsonPrimitive?.content, ignoreCase = true)) {
                    val jSONObject2 = jSONObject["R"]?.jsonObject ?: return arrayList2
                    val province = jSONObject2["province"]?.jsonPrimitive?.content.orEmpty()
                    val city = jSONObject2["city"]?.jsonPrimitive?.content.orEmpty()
                    val isp = jSONObject2["isp"]?.jsonPrimitive?.content.orEmpty()
                    val ip = jSONObject2["ip"]?.jsonPrimitive?.content.orEmpty()
                    val country = jSONObject2["country"]?.jsonPrimitive?.content.orEmpty()
                    val jSONObject3 = jSONObject2[str2]?.jsonObject
                    MyLog.v("get bucket: net=$isp, hosts=$jSONObject3")
                    if (jSONObject3 != null) {
                        for (i2 in arrayList.indices) {
                            val str3 = arrayList[i2]
                            val jSONArrayOptJSONArray = jSONObject3[str3]?.jsonArray
                            if (jSONArrayOptJSONArray == null) {
                                MyLog.w("no bucket found for $str3")
                            } else {
                                val fallback2 = Fallback(str3)
                                for (i3 in 0 until jSONArrayOptJSONArray.size) {
                                    val string6 = jSONArrayOptJSONArray[i3].jsonPrimitive.content
                                    if (string6.isNotEmpty()) {
                                        fallback2.addHost(WeightedHost(string6, jSONArrayOptJSONArray.size - i3))
                                    }
                                }
                                arrayList2[i2] = fallback2
                                fallback2.country = country
                                fallback2.province = province
                                fallback2.isp = isp
                                fallback2.ip = ip
                                fallback2.city = city
                                jSONObject2["stat-percent"]?.jsonPrimitive?.doubleOrNull?.let {
                                    fallback2.percent = it
                                }
                                jSONObject2["stat-domain"]?.jsonPrimitive?.content?.let {
                                    fallback2.domainName = it
                                }
                                jSONObject2["ttl"]?.jsonPrimitive?.intOrNull?.let {
                                    fallback2.effectiveDuration = it * 1000L
                                }
                                setCurrentISP(fallback2.getISP())
                            }
                        }
                    }
                    val jSONObjectOptJSONObject = jSONObject2["reserved"]?.jsonObject
                    if (jSONObjectOptJSONObject != null) {
                        var j = 604800000L
                        jSONObject2["reserved-ttl"]?.jsonPrimitive?.intOrNull?.let {
                            j = it * 1000L
                        }
                        for ((next, value) in jSONObjectOptJSONObject) {
                            val jSONArrayOptJSONArray2 = value.jsonArray
                            val fallback3 = Fallback(next)
                            fallback3.effectiveDuration = j
                            for (i4 in 0 until jSONArrayOptJSONArray2.size) {
                                val string7 = jSONArrayOptJSONArray2[i4].jsonPrimitive.content
                                if (string7.isNotEmpty()) {
                                    fallback3.addHost(WeightedHost(string7, jSONArrayOptJSONArray2.size - i4))
                                }
                            }
                            synchronized(sReservedHosts) {
                                if (sHostFilter.accept(next)) {
                                    sReservedHosts[next] = fallback3
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MyLog.w("failed to get bucket" + e.message)
        }
        for (i5 in arrayList.indices) {
            val fallback4 = arrayList2[i5]
            if (fallback4 != null) {
                updateFallbacks(arrayList[i5], fallback4)
            }
        }
        persist()
        return arrayList2
    }

    protected open fun checkHostMapping(): Boolean {
        synchronized(mHostsMapping) {
            if (hostLoaded) {
                return true
            }
            hostLoaded = true
            mHostsMapping.clear()
            return try {
                val strLoadHosts = loadHosts()
                if (!strLoadHosts.isNullOrEmpty()) {
                    fromJSON(strLoadHosts)
                    MyLog.i("loading the new hosts succeed")
                    true
                } else {
                    false
                }
            } catch (th: Throwable) {
                MyLog.w("load bucket failure: " + th.message)
                false
            }
        }
    }

    fun clear() {
        synchronized(mHostsMapping) {
            mHostsMapping.clear()
        }
    }

    fun dump(): String {
        val sb = StringBuilder()
        synchronized(mHostsMapping) {
            for ((key, value) in mHostsMapping) {
                sb.append(key)
                sb.append(":\n")
                sb.append(value.toString())
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    fun fromJSON(str: String) {
        synchronized(mHostsMapping) {
            mHostsMapping.clear()
            val jSONObject = Json.parseToJsonElement(str).jsonObject
            if ((jSONObject["ver"]?.jsonPrimitive?.intOrNull ?: 0) != 2) {
                throw IllegalArgumentException("Bad version")
            }
            val jSONArrayOptJSONArray = jSONObject["data"]?.jsonArray
            if (jSONArrayOptJSONArray != null) {
                for (element in jSONArrayOptJSONArray) {
                    val fallbacksFromJSON = Fallbacks().fromJSON(element.jsonObject)
                    mHostsMapping[fallbacksFromJSON.host] = fallbacksFromJSON
                }
            }
            val jSONArrayOptJSONArray2 = jSONObject["reserved"]?.jsonArray
            if (jSONArrayOptJSONArray2 != null) {
                for (element in jSONArrayOptJSONArray2) {
                    val jSONObject2 = element.jsonObject
                    val hostStr = jSONObject2["host"]?.jsonPrimitive?.content.orEmpty()
                    val fallbackFromJSON = Fallback(hostStr).fromJSON(jSONObject2)
                    fallbackFromJSON.host?.let {
                        sReservedHosts[it] = fallbackFromJSON
                    }
                }
            }
        }
    }

    fun generateHostStats(): ArrayList<HttpApi> {
        val arrayList: ArrayList<HttpApi>
        synchronized(mHostsMapping) {
            val map = HashMap<String, HttpApi>()
            for (host in mHostsMapping.keys) {
                val fallbacks = mHostsMapping[host] ?: continue
                for (next in fallbacks.fallbacks) {
                    var httpApi2 = map[next.isp]
                    if (httpApi2 == null) {
                        httpApi2 = HttpApi().apply {
                            setCategory("httpapi")
                            setClient_ip(next.ip)
                            setNetwork(processNetwork(next.networkLabel))
                            setUuid(sUserId)
                            setVersion(sAppVersion)
                            setVersion_type(sAppName)
                            setApp_name(sAppContext?.packageName)
                            setApp_version(getVersionName())
                            setLocation(Location().apply {
                                setCity(next.city)
                                setContry(next.country)
                                setProvince(next.province)
                                setIsp(next.isp)
                            })
                        }
                        map[next.isp!!] = httpApi2
                    }
                    val hostInfo = HostInfo().apply {
                        setHost(next.host)
                    }
                    val arrayList2 = ArrayList<LandNodeInfo>()
                    val weightedHosts = next.getWeightedHost()
                    for (next2 in weightedHosts) {
                        val unTouchedAccessHistory = next2.getUnTouchedAccessHistory()
                        if (unTouchedAccessHistory.isNotEmpty()) {
                            val landNodeInfo = LandNodeInfo().apply {
                                setIp(next2.host)
                            }
                            var i = 0
                            val map2 = HashMap<String, Int>()
                            var i2 = 0
                            var j = 0L
                            var size = 0
                            for (accessHistory in unTouchedAccessHistory) {
                                if (accessHistory.weight >= 0) {
                                    i++
                                    val cost = accessHistory.cost
                                    size = (size.toLong() + accessHistory.size).toInt()
                                    j += cost
                                } else {
                                    val exception = accessHistory.exception
                                    if (!exception.isNullOrEmpty()) {
                                        map2[exception] = (map2[exception] ?: 0) + 1
                                    }
                                    i2++
                                }
                            }
                            landNodeInfo.apply {
                                setExp_info(map2)
                                setSuccess_count(i)
                                setFailed_count(i2)
                                setDuration(j)
                                setSize(size)
                            }
                            arrayList2.add(landNodeInfo)
                        }
                    }
                    if (arrayList2.isNotEmpty()) {
                        hostInfo.setLand_node_info(arrayList2)
                        httpApi2.addToHost_info(hostInfo)
                    }
                }
            }
            arrayList = ArrayList()
            val httpApis = map.values
            for (value in httpApis) {
                if (value.getHost_infoSize() > 0) {
                    arrayList.add(value)
                }
            }
        }
        return arrayList
    }

    fun getCurrentISP(): String = currentISP

    @JvmOverloads
    fun getFallbacksByHost(str: String, z: Boolean = true): Fallback? {
        if (str.isEmpty()) {
            throw IllegalArgumentException("the host is empty")
        }
        if (!sHostFilter.accept(str)) {
            return null
        }
        val localFallback = getLocalFallback(str)
        if (localFallback != null && localFallback.isEffective()) {
            return localFallback
        }
        if (z && Network.hasNetwork(sAppContext)) {
            val fallbackRequestRemoteFallback = requestRemoteFallback(str)
            if (fallbackRequestRemoteFallback != null) {
                return fallbackRequestRemoteFallback
            }
        }
        // MiPush SDK 3.7.9 HostManager.2 and stock 7.4.67-C y7.h return an ineffective
        // proxy when no current bucket exists. The prior Kotlin port returned a fresh effective
        // Fallback, which prevented SocketConnection from ever scheduling a remote bucket refresh
        // and also dropped reserved hosts and access-history forwarding.
        return object : Fallback(str) {
            init {
                localFallback?.let { ip = it.ip }
            }

            override fun accessHost(str: String, accessHistory: AccessHistory) {
                synchronized(this) {
                    localFallback?.accessHost(str, accessHistory)
                }
            }

            override fun getHosts(z: Boolean): ArrayList<String> {
                synchronized(this) {
                    val hosts = ArrayList<String>()
                    localFallback?.getHosts(true)?.let(hosts::addAll)
                    synchronized(sReservedHosts) {
                        sReservedHosts[str]?.getHosts(true)?.forEach { reservedHost ->
                            if (!hosts.contains(reservedHost)) hosts.add(reservedHost)
                        }
                    }
                    hosts.remove(str)
                    hosts.add(str)
                    return hosts
                }
            }

            override fun isEffective(): Boolean = false
        }
    }

    @Throws(MalformedURLException::class)
    fun getFallbacksByURL(str: String): Fallback? {
        if (str.isEmpty()) {
            throw IllegalArgumentException("the url is empty")
        }
        return getFallbacksByHost(URL(str).host, true)
    }

    protected open fun getHost(): String {
        val region = AppRegionStorage.getInstance(sAppContext!!).getRegion()
        if (region.isNullOrEmpty()) {
            return HOST
        }
        return if (PushChannelRegion.China.name != region) HOST_GLOBAL else HOST
    }

    protected open fun getLocalFallback(str: String): Fallback? {
        synchronized(mHostsMapping) {
            checkHostMapping()
            return mHostsMapping[str]?.fallback
        }
    }

    protected open fun getProcessName(): String {
        val activityManager = sAppContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val runningAppProcesses = activityManager?.runningAppProcesses
        if (runningAppProcesses == null) {
            return sAppContext?.packageName ?: ""
        }
        for (runningAppProcessInfo in runningAppProcesses) {
            if (runningAppProcessInfo.pid == Process.myPid()) {
                return runningAppProcessInfo.processName
            }
        }
        return sAppContext?.packageName ?: ""
    }

    @Throws(IOException::class)
    protected open fun getRemoteFallbackJSON(arrayList: ArrayList<String>, str: String, str2: String, z: Boolean): String? {
        val arrayList3 = ArrayList<NameValuePair>()
        arrayList3.add(BasicNameValuePair("type", str))
        if (str == "wap") {
            arrayList3.add(BasicNameValuePair("conpt", obfuscate(Network.getActiveConnPoint(sAppContext))))
        }
        if (z) {
            arrayList3.add(BasicNameValuePair("reserved", "1"))
        }
        arrayList3.add(BasicNameValuePair("uuid", str2))
        arrayList3.add(BasicNameValuePair("list", XMStringUtils.join(arrayList.toTypedArray(), ",")!!))
        arrayList3.add(BasicNameValuePair("countrycode", AppRegionStorage.getInstance(sAppContext!!).getCountryCode()!!))
        
        val localFallback = getLocalFallback(getHost())
        val str3 = String.format(Locale.US, BUCKET_URL, getHost())
        val arrayList2 = ArrayList<String>()
        synchronized(sReservedHosts) {
            sReservedHosts[HOST]?.getHosts(true)?.let { arrayList2.addAll(it) }
        }
        
        val observer = XMPushServiceCore.observer
        if (observer == null) return null
        val planRequestUrls = observer.planRequestUrls(str3, localFallback?.getUrls(str3), arrayList2)
        val urls = ArrayList(planRequestUrls.urls)
        var e: IOException? = null
        for (url in urls) {
            val builderBuildUpon = Uri.parse(url).buildUpon()
            for (nameValuePair in arrayList3) {
                builderBuildUpon.appendQueryParameter(nameValuePair.name, nameValuePair.value)
            }
            try {
                return sHttpGetter?.doGet(builderBuildUpon.toString()) ?: Network.downloadXml(sAppContext, URL(builderBuildUpon.toString()))
            } catch (e2: IOException) {
                e = e2
            }
        }
        if (e == null) {
            return null
        }
        MyLog.w("network exception: " + e.message)
        throw e
    }

    protected open fun loadHosts(): String? {
        var bufferedReader: BufferedReader? = null
        return try {
            val file = File(sAppContext?.filesDir, getProcessName())
            if (file.isFile) {
                val bufferedReader3 = BufferedReader(InputStreamReader(FileInputStream(file)))
                bufferedReader = bufferedReader3
                val sb = StringBuilder()
                while (true) {
                    val line = bufferedReader3.readLine() ?: break
                    sb.append(line)
                }
                sb.toString()
            } else {
                null
            }
        } catch (th: Throwable) {
            MyLog.w("load host exception " + th.message)
            null
        } finally {
            IOUtils.closeQuietly(bufferedReader)
        }
    }

    fun persist() {
        synchronized(mHostsMapping) {
            try {
                val bufferedWriter = BufferedWriter(OutputStreamWriter(sAppContext!!.openFileOutput(getProcessName(), 0)))
                val string = toJSON().toString()
                if (string.isNotEmpty()) {
                    bufferedWriter.write(string)
                }
                bufferedWriter.close()
            } catch (e: Exception) {
                MyLog.w("persist bucket failure: " + e.message)
            }
        }
    }

    fun purge() {
        synchronized(mHostsMapping) {
            for (fallbacks in mHostsMapping.values) {
                fallbacks.purge(true)
            }
            var finished = false
            while (!finished) {
                finished = true
                val iterator = mHostsMapping.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value.fallbacks.isEmpty()) {
                        iterator.remove()
                        finished = false
                    }
                }
            }
        }
    }

    @Throws(Throwable::class)
    fun refreshFallbacks() {
        val arrayList: ArrayList<String>
        val arrayList2 = ArrayList<String>()
        synchronized(mHostsMapping) {
            checkHostMapping()
            arrayList = ArrayList(mHostsMapping.keys)
            for (str in arrayList) {
                if (mHostsMapping[str]?.fallback != null) {
                    arrayList2.add(str)
                }
            }
        }
        val observer = XMPushServiceCore.observer
        if (observer != null) {
            val planRefreshTargets = observer.planRefreshTargets(arrayList, HashSet(arrayList2))
            val arrayList3 = ArrayList(planRefreshTargets.targetHosts)
            val arrayListRequestRemoteFallbacks = requestRemoteFallbacks(arrayList3)
            for (i in arrayList3.indices) {
                arrayListRequestRemoteFallbacks[i]?.let {
                    updateFallbacks(arrayList3[i], it)
                }
            }
        }
    }

    protected open fun requestRemoteFallback(str: String): Fallback? {
        val observer = XMPushServiceCore.observer ?: return null
        val planRemoteFallbackRequest = observer.planRemoteFallbackRequest(System.currentTimeMillis(), lastRemoteRequestTimestamp, remoteRequestFailureCount)
        if (!planRemoteFallbackRequest.shouldRequest) {
            return null
        }
        lastRemoteRequestTimestamp = planRemoteFallbackRequest.nextTimestampMs
        val arrayList = ArrayList<String>()
        arrayList.add(str)
        var fallback: Fallback? = null
        try {
            fallback = requestRemoteFallbacks(arrayList)[0]
        } catch (th: Throwable) {
            MyLog.e(th)
        }
        if (fallback != null) {
            remoteRequestFailureCount = 0L
            return fallback
        }
        if (remoteRequestFailureCount < 15) {
            remoteRequestFailureCount++
        }
        return null
    }

    fun setCurrentISP(str: String) {
        currentISP = str
    }

    protected open fun toJSON(): JsonObject {
        synchronized(mHostsMapping) {
            return buildJsonObject {
                put("ver", 2)
                put("data", buildJsonArray {
                    for (fallbacks in mHostsMapping.values) {
                        add(fallbacks.toJSON())
                    }
                })
                put("reserved", buildJsonArray {
                    for (fallback in sReservedHosts.values) {
                        add(fallback.toJSON())
                    }
                })
            }
        }
    }

    fun updateFallbacks(str: String, fallback: Fallback) {
        if (str.isEmpty()) {
            throw IllegalArgumentException("the argument is invalid $str, $fallback")
        }
        if (sHostFilter.accept(str)) {
            synchronized(mHostsMapping) {
                checkHostMapping()
                val fallbacks = mHostsMapping.getOrPut(str) { Fallbacks(str) }
                fallbacks.addFallback(fallback)
            }
        }
    }

    fun updateHostsMapping(map: Map<String, Fallbacks>) {
        synchronized(mHostsMapping) {
            mHostsMapping.clear()
            mHostsMapping.putAll(map)
        }
    }
}
