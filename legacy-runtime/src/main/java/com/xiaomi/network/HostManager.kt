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
import android.text.TextUtils
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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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

        @JvmStatic
        fun getActiveNetworkLabel(): String {
            val context = sAppContext ?: return "unknown"
            return try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connectivityManager?.activeNetwork ?: return "unknown"
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "unknown"
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val transportInfo = networkCapabilities.transportInfo
                        if (transportInfo is WifiInfo) {
                            return "WIFI-" + transportInfo.ssid
                        }
                    }
                    "WIFI"
                } else {
                    Network.getActiveNetworkName(context)
                }
            } catch (th: Throwable) {
                "unknown"
            }
        }

        @JvmStatic
        @Synchronized
        fun getInstance(): HostManager {
            return sInstance ?: throw IllegalStateException("the host manager is not initialized yet.")
        }

        @JvmStatic
        fun init(context: Context, hostFilter: HostFilter?, httpGet: HttpGet?, userId: String) {
            init(context, hostFilter, httpGet, userId, null, null)
        }

        @JvmStatic
        @Synchronized
        fun init(context: Context, hostFilter: HostFilter?, httpGet: HttpGet?, userId: String, appName: String?, appVersion: String?) {
            val applicationContext = context.applicationContext ?: context
            sAppContext = applicationContext
            if (sInstance == null) {
                val observer = XMPushService.observer
                if (observer != null) {
                    try {
                        val obsInstance = observer.createHostManager(context, hostFilter, httpGet, userId)
                        if (obsInstance is HostManager) {
                            sInstance = obsInstance
                            return
                        }
                    } catch (t: Throwable) {
                        MyLog.e("HostManager.init create via observer fail", t)
                    }
                }

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
        fun createHostManager(context: Context, hostFilter: HostFilter?, httpGet: HttpGet?, userId: String): HostManager
    }

    fun interface HttpGet {
        @Throws(IOException::class)
        fun doGet(url: String): String
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
        return if (TextUtils.isEmpty(str)) "unknown" else if (str!!.startsWith("WIFI")) "WIFI" else str
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
            if (!TextUtils.isEmpty(remoteFallbackJSON)) {
                val jSONObject = JSONObject(remoteFallbackJSON!!)
                MyLog.i(remoteFallbackJSON)
                if ("OK".equals(jSONObject.getString("S"), ignoreCase = true)) {
                    val jSONObject2 = jSONObject.getJSONObject("R")
                    val province = jSONObject2.getString("province")
                    val city = jSONObject2.getString("city")
                    val isp = jSONObject2.getString("isp")
                    val ip = jSONObject2.getString("ip")
                    val country = jSONObject2.getString("country")
                    val jSONObject3 = jSONObject2.getJSONObject(str2)
                    MyLog.v("get bucket: net=$isp, hosts=$jSONObject3")
                    for (i2 in arrayList.indices) {
                        val str3 = arrayList[i2]
                        val jSONArrayOptJSONArray = jSONObject3.optJSONArray(str3)
                        if (jSONArrayOptJSONArray == null) {
                            MyLog.w("no bucket found for $str3")
                        } else {
                            val fallback2 = Fallback(str3)
                            for (i3 in 0 until jSONArrayOptJSONArray.length()) {
                                val string6 = jSONArrayOptJSONArray.getString(i3)
                                if (!TextUtils.isEmpty(string6)) {
                                    fallback2.addHost(WeightedHost(string6, jSONArrayOptJSONArray.length() - i3))
                                }
                            }
                            arrayList2[i2] = fallback2
                            fallback2.country = country
                            fallback2.province = province
                            fallback2.isp = isp
                            fallback2.ip = ip
                            fallback2.city = city
                            if (jSONObject2.has("stat-percent")) {
                                fallback2.percent = jSONObject2.getDouble("stat-percent")
                            }
                            if (jSONObject2.has("stat-domain")) {
                                fallback2.domainName = jSONObject2.getString("stat-domain")
                            }
                            if (jSONObject2.has("ttl")) {
                                fallback2.effectiveDuration = jSONObject2.getInt("ttl") * 1000L
                            }
                            setCurrentISP(fallback2.getISP())
                        }
                    }
                    val jSONObjectOptJSONObject = jSONObject2.optJSONObject("reserved")
                    if (jSONObjectOptJSONObject != null) {
                        var j = 604800000L
                        if (jSONObject2.has("reserved-ttl")) {
                            j = jSONObject2.getInt("reserved-ttl") * 1000L
                        }
                        val it = jSONObjectOptJSONObject.keys()
                        while (it.hasNext()) {
                            val next = it.next()
                            val jSONArrayOptJSONArray2 = jSONObjectOptJSONObject.optJSONArray(next)
                            if (jSONArrayOptJSONArray2 == null) {
                                MyLog.w("no bucket found for $next")
                            } else {
                                val fallback3 = Fallback(next)
                                fallback3.effectiveDuration = j
                                for (i4 in 0 until jSONArrayOptJSONArray2.length()) {
                                    val string7 = jSONArrayOptJSONArray2.getString(i4)
                                    if (!TextUtils.isEmpty(string7)) {
                                        fallback3.addHost(WeightedHost(string7, jSONArrayOptJSONArray2.length() - i4))
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
                if (!TextUtils.isEmpty(strLoadHosts)) {
                    fromJSON(strLoadHosts!!)
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

    @Throws(JSONException::class)
    protected open fun fromJSON(str: String) {
        synchronized(mHostsMapping) {
            mHostsMapping.clear()
            val jSONObject = JSONObject(str)
            if (jSONObject.optInt("ver") != 2) {
                throw JSONException("Bad version")
            }
            val jSONArrayOptJSONArray = jSONObject.optJSONArray("data")
            if (jSONArrayOptJSONArray != null) {
                for (i in 0 until jSONArrayOptJSONArray.length()) {
                    val fallbacksFromJSON = Fallbacks().fromJSON(jSONArrayOptJSONArray.getJSONObject(i))
                    mHostsMapping[fallbacksFromJSON.host] = fallbacksFromJSON
                }
            }
            val jSONArrayOptJSONArray2 = jSONObject.optJSONArray("reserved")
            if (jSONArrayOptJSONArray2 != null) {
                for (i2 in 0 until jSONArrayOptJSONArray2.length()) {
                    val jSONObject2 = jSONArrayOptJSONArray2.getJSONObject(i2)
                    val fallbackFromJSON = Fallback(jSONObject2.optString("host")).fromJSON(jSONObject2)
                    sReservedHosts[fallbackFromJSON.host!!] = fallbackFromJSON
                }
            }
        }
    }

    @Throws(JSONException::class)
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
                                    if (!TextUtils.isEmpty(exception)) {
                                        map2[exception!!] = (map2[exception] ?: 0) + 1
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
                if (value.host_infoSize > 0) {
                    arrayList.add(value)
                }
            }
        }
        return arrayList
    }

    fun getCurrentISP(): String = currentISP

    @JvmOverloads
    fun getFallbacksByHost(str: String, z: Boolean = true): Fallback? {
        if (TextUtils.isEmpty(str)) {
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
        return Fallback(str).apply {
            localFallback?.let { ip = it.ip }
        }
    }

    @Throws(MalformedURLException::class)
    fun getFallbacksByURL(str: String): Fallback? {
        if (TextUtils.isEmpty(str)) {
            throw IllegalArgumentException("the url is empty")
        }
        return getFallbacksByHost(URL(str).host, true)
    }

    protected open fun getHost(): String {
        val region = AppRegionStorage.getInstance(sAppContext!!).getRegion()
        val zIsEmpty = TextUtils.isEmpty(region)
        if (zIsEmpty) {
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
        
        val observer = XMPushService.observer
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
                if (!TextUtils.isEmpty(string)) {
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
        val observer = XMPushService.observer
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
        val observer = XMPushService.observer ?: return null
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

    @Throws(JSONException::class)
    protected open fun toJSON(): JSONObject {
        val jSONObject: JSONObject
        synchronized(mHostsMapping) {
            jSONObject = JSONObject().apply {
                put("ver", 2)
                val jSONArray = JSONArray()
                for (fallbacks in mHostsMapping.values) {
                    jSONArray.put(fallbacks.toJSON())
                }
                put("data", jSONArray)
                val jSONArray2 = JSONArray()
                for (fallback in sReservedHosts.values) {
                    jSONArray2.put(fallback.toJSON())
                }
                put("reserved", jSONArray2)
            }
        }
        return jSONObject
    }

    fun updateFallbacks(str: String, fallback: Fallback) {
        if (TextUtils.isEmpty(str)) {
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
