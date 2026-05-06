package com.xiaomi.channel.commonutils.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import com.xiaomi.channel.commonutils.android.TelephonyUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.string.MD5
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.Executors
import java.util.regex.Pattern

/*
 * Current override reference: miuipushsdkshared_3_7_9.jar:
 * com/xiaomi/channel/commonutils/network/Network.class
 */
object Network {
    const val CHINA_3G_CDMA2000 = "CDMA2000"
    const val CHINA_3G_TD_SCDMA = "TD-SCDMA"
    const val CHINA_3G_WCDMA = "WCDMA"
    const val CMWAP_GATEWAY = "10.0.0.172"
    const val CMWAP_HEADER_HOST_KEY = "X-Online-Host"
    const val CMWAP_PORT = 80
    const val CONNECTION_TIMEOUT = 10000
    private const val LogTag = "com.xiaomi.common.Network"
    const val NETWORK_TYPE_3GNET = "3gnet"
    const val NETWORK_TYPE_3GWAP = "3gwap"
    const val NETWORK_TYPE_CHINATELECOM = "#777"
    const val NETWORK_TYPE_WIFI = "wifi"
    const val READ_TIMEOUT = 15000
    const val USER_AGENT = "User-Agent"
    const val UserAgent_PC_Chrome = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.464.0 Safari/534.3"
    const val UserAgent_PC_Chrome_6_0_464_0 = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.464.0 Safari/534.3"

    @JvmField
    val ContentTypePattern_MimeType: Pattern = Pattern.compile("([^\\s;]+)(.*)")

    @JvmField
    val ContentTypePattern_Charset: Pattern = Pattern.compile("(.*?charset\\s*=[^a-zA-Z0-9]*)([-a-zA-Z0-9]+)(.*)", Pattern.CASE_INSENSITIVE)

    @JvmField
    val ContentTypePattern_XmlEncoding: Pattern = Pattern.compile("(\\<\\?xml\\s+.*?encoding\\s*=[^a-zA-Z0-9]*)([-a-zA-Z0-9]+)(.*)", Pattern.CASE_INSENSITIVE)

    private val DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool()

    class DoneHandlerInputStream(inputStream: InputStream) : FilterInputStream(inputStream) {
        private var done = false

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
            if (!done) {
                val read = super.read(buffer, byteOffset, byteCount)
                if (read != -1) {
                    return read
                }
            }
            done = true
            return -1
        }
    }

    class HttpHeaderInfo {
        @JvmField
        var AllHeaders: MutableMap<String, String>? = null

        @JvmField
        var ContentType: String? = null

        @JvmField
        var ResponseCode: Int = 0

        @JvmField
        var UserAgent: String? = null

        @JvmField
        var realUrl: String? = null

        override fun toString(): String {
            return String.format("resCode = %1\$d, headers = %2\$s", ResponseCode, AllHeaders.toString())
        }
    }

    fun interface PostDownloadHandler {
        fun OnPostDownload(success: Boolean)
    }

    @JvmStatic
    fun beginDownloadFile(
        url: String,
        outputStream: OutputStream,
        context: Context?,
        stopWhenNotWifi: Boolean,
        postDownloadHandler: PostDownloadHandler,
    ) {
        DOWNLOAD_EXECUTOR.execute {
            postDownloadHandler.OnPostDownload(downloadFile(url, outputStream, stopWhenNotWifi, context))
        }
    }

    @JvmStatic
    fun beginDownloadFile(url: String, outputStream: OutputStream, postDownloadHandler: PostDownloadHandler) {
        DOWNLOAD_EXECUTOR.execute {
            postDownloadHandler.OnPostDownload(downloadFile(url, outputStream))
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun doHttpPost(context: Context?, url: String?, params: Map<String, String>?): HttpResponse {
        return httpRequest(context, url, "POST", null, fromParamsMapToString(params))
    }

    @JvmStatic
    fun downloadFile(url: String, outputStream: OutputStream): Boolean {
        return downloadFile(url, outputStream, false, null)
    }

    @JvmStatic
    fun downloadFile(url: String, outputStream: OutputStream, context: Context): Boolean {
        try {
            val httpURLConnection = URL(url).openConnection() as HttpURLConnection
            HttpURLConnection.setFollowRedirects(true)
            httpURLConnection.connectTimeout = CONNECTION_TIMEOUT
            httpURLConnection.readTimeout = READ_TIMEOUT
            httpURLConnection.connect()
            val inputStream = httpURLConnection.inputStream
            val buffer = ByteArray(1024)
            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) {
                    inputStream.close()
                    outputStream.close()
                    return true
                }
                outputStream.write(buffer, 0, read)
            }
        } catch (e: IOException) {
            Log.e(LogTag, "error while download file:" + e.javaClass.simpleName)
            return false
        } catch (throwable: Throwable) {
            Log.e(LogTag, "error while download file$throwable")
            return false
        }
    }

    @JvmStatic
    fun downloadFile(url: String, outputStream: OutputStream, stopWhenNotWifi: Boolean, context: Context?): Boolean {
        var inputStream: InputStream? = null
        try {
            val httpURLConnection = URL(url).openConnection() as HttpURLConnection
            httpURLConnection.connectTimeout = CONNECTION_TIMEOUT
            httpURLConnection.readTimeout = READ_TIMEOUT
            HttpURLConnection.setFollowRedirects(true)
            httpURLConnection.connect()
            inputStream = httpURLConnection.inputStream
            val buffer = ByteArray(1024)
            var interruptedByNetwork = false
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) {
                    break
                }
                outputStream.write(buffer, 0, read)
                if (stopWhenNotWifi && context != null && !isWIFIConnected(context)) {
                    interruptedByNetwork = true
                    break
                }
            }
            IOUtils.closeQuietly(inputStream)
            IOUtils.closeQuietly(outputStream)
            return !interruptedByNetwork
        } catch (e: IOException) {
            Log.e(LogTag, "error while download file:" + e.javaClass.simpleName)
            IOUtils.closeQuietly(inputStream)
            IOUtils.closeQuietly(outputStream)
            return false
        } catch (throwable: Throwable) {
            Log.e(LogTag, "error while download file$throwable")
            IOUtils.closeQuietly(inputStream)
            IOUtils.closeQuietly(outputStream)
            return false
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXml(context: Context?, url: URL): String {
        return downloadXml(context, url, false, null, "UTF-8", null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXml(
        context: Context?,
        url: URL,
        userAgent: String?,
        cookie: String?,
        headers: Map<String, String>?,
        httpHeaderInfo: HttpHeaderInfo?,
    ): String {
        var inputStream: InputStream? = null
        try {
            inputStream = downloadXmlAsStream(context, url, true, userAgent, cookie, headers, httpHeaderInfo)
            val builder = StringBuilder(1024)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val buffer = CharArray(4096)
            while (true) {
                val read = bufferedReader.read(buffer)
                if (read == -1) {
                    IOUtils.closeQuietly(inputStream)
                    return builder.toString()
                }
                builder.append(buffer, 0, read)
            }
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXml(
        context: Context?,
        url: URL,
        encrypt: Boolean,
        userAgent: String?,
        encoding: String,
        cookie: String?,
    ): String {
        var inputStream: InputStream? = null
        try {
            inputStream = downloadXmlAsStream(context, url, encrypt, userAgent, cookie)
            val builder = StringBuilder(1024)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, encoding))
            val buffer = CharArray(4096)
            while (true) {
                val read = bufferedReader.read(buffer)
                if (read == -1) {
                    IOUtils.closeQuietly(inputStream)
                    return builder.toString()
                }
                builder.append(buffer, 0, read)
            }
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXmlAsStream(context: Context?, url: URL): InputStream {
        return downloadXmlAsStream(context, url, true, null, null, null, null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXmlAsStream(context: Context?, url: URL, encrypt: Boolean, userAgent: String?, cookie: String?): InputStream {
        return downloadXmlAsStream(context, url, encrypt, userAgent, cookie, null, null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXmlAsStream(
        context: Context?,
        url: URL?,
        encrypt: Boolean,
        userAgent: String?,
        cookie: String?,
        headers: Map<String, String>?,
        httpHeaderInfo: HttpHeaderInfo?,
    ): InputStream {
        if (context == null) {
            throw IllegalArgumentException("context")
        }
        if (url == null) {
            throw IllegalArgumentException("url")
        }
        val requestUrl = if (encrypt) {
            url
        } else {
            URL(encryptURL(url.toString()))
        }
        try {
            HttpURLConnection.setFollowRedirects(true)
            val httpUrlConnection = getHttpUrlConnection(context, requestUrl)
            httpUrlConnection.connectTimeout = CONNECTION_TIMEOUT
            httpUrlConnection.readTimeout = READ_TIMEOUT
            if (!TextUtils.isEmpty(userAgent)) {
                httpUrlConnection.setRequestProperty(USER_AGENT, userAgent)
            }
            if (cookie != null) {
                httpUrlConnection.setRequestProperty("Cookie", cookie)
            }
            headers?.forEach { (key, value) ->
                httpUrlConnection.setRequestProperty(key, value)
            }
            if (httpHeaderInfo != null && (url.protocol == "http" || url.protocol == "https")) {
                httpHeaderInfo.ResponseCode = httpUrlConnection.responseCode
                if (httpHeaderInfo.AllHeaders == null) {
                    httpHeaderInfo.AllHeaders = HashMap()
                }
                var index = 0
                while (true) {
                    val headerFieldKey = httpUrlConnection.getHeaderFieldKey(index)
                    val headerField = httpUrlConnection.getHeaderField(index)
                    if (headerFieldKey == null && headerField == null) {
                        break
                    }
                    if (!TextUtils.isEmpty(headerFieldKey) && !TextUtils.isEmpty(headerField)) {
                        httpHeaderInfo.AllHeaders?.put(headerFieldKey, headerField)
                    }
                    index++
                }
            }
            return DoneHandlerInputStream(httpUrlConnection.inputStream)
        } catch (e: IOException) {
            throw IOException("IOException:" + e.javaClass.simpleName)
        } catch (throwable: Throwable) {
            throw IOException(throwable.message)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadXmlAsStreamWithoutRedirect(url: URL, userAgent: String?, cookie: String?): InputStream {
        try {
            HttpURLConnection.setFollowRedirects(false)
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.connectTimeout = CONNECTION_TIMEOUT
            httpURLConnection.readTimeout = READ_TIMEOUT
            if (!TextUtils.isEmpty(userAgent)) {
                httpURLConnection.setRequestProperty(USER_AGENT, userAgent)
            }
            if (cookie != null) {
                httpURLConnection.setRequestProperty("Cookie", cookie)
            }
            val responseCode = httpURLConnection.responseCode
            val inputStream = if (responseCode >= 400) httpURLConnection.errorStream else httpURLConnection.inputStream
            return DoneHandlerInputStream(inputStream)
        } catch (e: IOException) {
            throw IOException("IOException:" + e.javaClass.simpleName)
        } catch (throwable: Throwable) {
            throw IOException(throwable.message)
        }
    }

    @JvmStatic
    fun encryptURL(url: String?): String? {
        if (TextUtils.isEmpty(url)) {
            return null
        }
        return String.format("%s&key=%s", url, MD5.MD5_32(String.format("%sbe988a6134bc8254465424e5a70ef037", url)))
    }

    @JvmStatic
    fun fromParamsMapToString(params: Map<String, String>?): String? {
        if (params.isNullOrEmpty()) {
            return null
        }
        val stringBuffer = StringBuffer()
        for ((key, value) in params) {
            try {
                stringBuffer.append(URLEncoder.encode(key, "UTF-8"))
                stringBuffer.append("=")
                stringBuffer.append(URLEncoder.encode(value, "UTF-8"))
                stringBuffer.append("&")
            } catch (e: UnsupportedEncodingException) {
                Log.d(LogTag, "Failed to convert from params map to string: $e")
                Log.d(LogTag, "map: $params")
                return null
            }
        }
        if (stringBuffer.isNotEmpty()) {
            stringBuffer.deleteCharAt(stringBuffer.length - 1)
        }
        return stringBuffer.toString()
    }

    @JvmStatic
    fun getActiveConnPoint(context: Context?): String {
        if (context == null) {
            return ""
        }
        if (isWIFIConnected(context)) {
            return NETWORK_TYPE_WIFI
        }
        return try {
            val activeNetworkCapabilities = getActiveNetworkCapabilities(context) ?: return ""
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                joinNetworkPoint("mobile", getActiveCellularSubtypeName(context), getLocalNetworkType(context))
            } else {
                getActiveNetworkName(context).lowercase(Locale.ROOT)
            }
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun getActiveNetworkName(context: Context?): String {
        if (context == null) {
            return "null"
        }
        return try {
            val activeNetworkCapabilities = getActiveNetworkCapabilities(context) ?: return "null"
            when {
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_TYPE_WIFI
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val activeCellularSubtypeName = getActiveCellularSubtypeName(context)
                    if (TextUtils.isEmpty(activeCellularSubtypeName)) {
                        "mobile"
                    } else {
                        String.format(Locale.ROOT, "mobile-%s", activeCellularSubtypeName)
                    }
                }
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "unknown"
            }
        } catch (_: Exception) {
            "null"
        }
    }

    @JvmStatic
    fun getActiveNetworkType(context: Context?): Int {
        if (context == null) {
            return -1
        }
        return try {
            val activeNetworkCapabilities = getActiveNetworkCapabilities(context) ?: return -1
            when {
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 0
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 9
                else -> -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    @JvmStatic
    fun getCMWapUrl(url: URL): String {
        val builder = StringBuilder()
        builder.append(url.protocol)
        builder.append("://")
        builder.append(CMWAP_GATEWAY)
        builder.append(url.path)
        if (!TextUtils.isEmpty(url.query)) {
            builder.append("?")
            builder.append(url.query)
        }
        return builder.toString()
    }

    @Throws(MalformedURLException::class)
    private fun getDefaultStreamHandlerURL(url: String): URL {
        return URL(url)
    }

    @JvmStatic
    fun getHttpHeaderInfo(urlString: String, userAgent: String?, cookie: String?): HttpHeaderInfo? {
        try {
            val url = URL(urlString)
            if (url.protocol != "http" && url.protocol != "https") {
                return null
            }
            HttpURLConnection.setFollowRedirects(false)
            val httpURLConnection = url.openConnection() as HttpURLConnection
            if (urlString.indexOf("wap") == -1) {
                httpURLConnection.connectTimeout = 5000
                httpURLConnection.readTimeout = 5000
            } else {
                httpURLConnection.connectTimeout = READ_TIMEOUT
                httpURLConnection.readTimeout = READ_TIMEOUT
            }
            if (!TextUtils.isEmpty(userAgent)) {
                httpURLConnection.setRequestProperty(USER_AGENT, userAgent)
            }
            if (cookie != null) {
                httpURLConnection.setRequestProperty("Cookie", cookie)
            }
            val httpHeaderInfo = HttpHeaderInfo()
            httpHeaderInfo.ResponseCode = httpURLConnection.responseCode
            httpHeaderInfo.UserAgent = userAgent
            var index = 0
            while (true) {
                val headerFieldKey = httpURLConnection.getHeaderFieldKey(index)
                val headerField = httpURLConnection.getHeaderField(index)
                if (headerFieldKey == null && headerField == null) {
                    return httpHeaderInfo
                }
                if (headerFieldKey != null && headerFieldKey == "content-type") {
                    httpHeaderInfo.ContentType = headerField
                }
                if (headerFieldKey != null && headerFieldKey == "location") {
                    val uri = URI(headerField)
                    val resolvedUri = if (uri.isAbsolute) uri else URI(urlString).resolve(uri)
                    httpHeaderInfo.realUrl = resolvedUri.toString()
                }
                index++
            }
        } catch (e: MalformedURLException) {
            Log.e(LogTag, "Failed to transform URL", e)
            return null
        } catch (e: IOException) {
            Log.e(LogTag, "Failed to get mime type", e)
            return null
        } catch (e: URISyntaxException) {
            Log.e(LogTag, "Failed to parse URI", e)
            return null
        } catch (throwable: Throwable) {
            Log.e(LogTag, "Failed to get HttpHeaderInfo", throwable)
            return null
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getHttpPostAsStream(
        url: URL?,
        body: String,
        headers: MutableMap<String, String>,
        userAgent: String?,
        cookie: String?,
    ): InputStream {
        if (url == null) {
            throw IllegalArgumentException("url")
        }
        try {
            HttpURLConnection.setFollowRedirects(true)
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.connectTimeout = 5000
            httpURLConnection.readTimeout = READ_TIMEOUT
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.doOutput = true
            if (!TextUtils.isEmpty(userAgent)) {
                httpURLConnection.setRequestProperty(USER_AGENT, userAgent)
            }
            if (!TextUtils.isEmpty(cookie)) {
                httpURLConnection.setRequestProperty("Cookie", cookie)
            }
            val outputStream = httpURLConnection.outputStream
            outputStream.write(body.toByteArray())
            outputStream.flush()
            outputStream.close()
            headers["ResponseCode"] = httpURLConnection.responseCode.toString()
            var index = 0
            while (true) {
                val headerFieldKey = httpURLConnection.getHeaderFieldKey(index)
                val headerField = httpURLConnection.getHeaderField(index)
                if (headerFieldKey == null && headerField == null) {
                    return httpURLConnection.inputStream
                }
                putNullableHeader(headers, headerFieldKey, headerField)
                index++
            }
        } catch (e: IOException) {
            throw IOException("IOException:" + e.javaClass.simpleName)
        } catch (throwable: Throwable) {
            throw IOException(throwable.message)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getHttpUrlConnection(context: Context?, url: URL): HttpURLConnection {
        return if ("http" == url.protocol && isCtwap(context)) {
            url.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.0.200", 80))) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
    }

    @JvmStatic
    fun getLocalNetworkType(context: Context?): String {
        if (context == null) {
            return "unknown"
        }
        if (isWIFIConnected(context)) {
            return NETWORK_TYPE_WIFI
        }
        if (TelephonyUtils.isChinaTelecom(context)) {
            return NETWORK_TYPE_CHINATELECOM
        }
        val activeCellularSubtypeName = getActiveCellularSubtypeName(context)
        return if (TextUtils.isEmpty(activeCellularSubtypeName)) "unknown" else activeCellularSubtypeName.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun hasNetwork(context: Context?): Boolean {
        return getActiveNetworkCapabilities(context) != null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun httpRequest(
        context: Context?,
        url: String?,
        method: String?,
        headers: Map<String, String>?,
        body: String?,
    ): HttpResponse {
        val httpResponse = HttpResponse()
        var outputStream: OutputStream? = null
        var bufferedReader: BufferedReader? = null
        try {
            val httpUrlConnection = getHttpUrlConnection(context, getDefaultStreamHandlerURL(url ?: throw MalformedURLException("url")))
            httpUrlConnection.connectTimeout = CONNECTION_TIMEOUT
            httpUrlConnection.readTimeout = READ_TIMEOUT
            httpUrlConnection.requestMethod = method ?: "GET"
            headers?.forEach { (key, value) ->
                httpUrlConnection.setRequestProperty(key, value)
            }
            if (!TextUtils.isEmpty(body)) {
                val bytes = body!!.toByteArray()
                httpUrlConnection.doOutput = true
                outputStream = httpUrlConnection.outputStream
                outputStream.write(bytes, 0, bytes.size)
                outputStream.flush()
            }
            httpResponse.responseCode = httpUrlConnection.responseCode
            Log.d(LogTag, "Http POST Response Code: " + httpResponse.responseCode)
            var index = 0
            while (true) {
                val headerFieldKey = httpUrlConnection.getHeaderFieldKey(index)
                val headerField = httpUrlConnection.getHeaderField(index)
                if (headerFieldKey == null && headerField == null) {
                    break
                }
                putNullableHeader(httpResponse.headers, headerFieldKey, headerField)
                index++
            }
            val inputStream = try {
                httpUrlConnection.inputStream
            } catch (_: IOException) {
                httpUrlConnection.errorStream
            }
            bufferedReader = BufferedReader(InputStreamReader(DoneHandlerInputStream(inputStream)))
            val stringBuffer = StringBuffer()
            val lineSeparator = System.getProperty("line.separator")
            var line = bufferedReader.readLine()
            while (line != null) {
                stringBuffer.append(line).append(lineSeparator)
                line = bufferedReader.readLine()
            }
            httpResponse.responseString = stringBuffer.toString()
            return httpResponse
        } catch (e: IOException) {
            throw e
        } catch (throwable: Throwable) {
            throw IOException(throwable.message)
        } finally {
            IOUtils.closeQuietly(outputStream)
            IOUtils.closeQuietly(bufferedReader)
        }
    }

    @JvmStatic
    fun is2GConnected(context: Context?): Boolean {
        return when (getActiveCellularSubtype(context)) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            4,
            7,
            11,
            -> true
            else -> false
        }
    }

    @JvmStatic
    fun is3GConnected(context: Context?): Boolean {
        val activeCellularSubtype = getActiveCellularSubtype(context)
        if (activeCellularSubtype < 0) {
            return false
        }
        val subtypeName = getActiveCellularSubtypeName(context)
        if (
            CHINA_3G_TD_SCDMA.equals(subtypeName, ignoreCase = true) ||
            CHINA_3G_CDMA2000.equals(subtypeName, ignoreCase = true) ||
            CHINA_3G_WCDMA.equals(subtypeName, ignoreCase = true)
        ) {
            return true
        }
        return when (activeCellularSubtype) {
            TelephonyManager.NETWORK_TYPE_UMTS,
            5,
            6,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            12,
            14,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            -> true
            else -> false
        }
    }

    @JvmStatic
    fun is4GConnected(context: Context?): Boolean {
        return getActiveCellularSubtype(context) == TelephonyManager.NETWORK_TYPE_LTE
    }

    @JvmStatic
    fun is5GConnected(context: Context?): Boolean {
        return getActiveCellularSubtype(context) == TelephonyManager.NETWORK_TYPE_NR
    }

    @JvmStatic
    fun isConnected(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        return try {
            val activeNetworkCapabilities = getActiveNetworkCapabilities(context)
            activeNetworkCapabilities != null && activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isCtwap(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        if (!"CN".equals((context.getSystemService("phone") as TelephonyManager).simCountryIso, ignoreCase = true)) {
            return false
        }
        return false
    }

    @JvmStatic
    fun isUsingMobileDataConnection(context: Context?): Boolean {
        return is5GConnected(context) || is4GConnected(context) || is3GConnected(context) || is2GConnected(context)
    }

    @JvmStatic
    fun isWIFIConnected(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        return try {
            val activeNetworkCapabilities = getActiveNetworkCapabilities(context)
            activeNetworkCapabilities != null && activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private fun getConnectivityManager(context: Context?): ConnectivityManager? {
        return context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    }

    private fun getActiveNetworkCapabilities(context: Context?): NetworkCapabilities? {
        val connectivityManager = getConnectivityManager(context) ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(activeNetwork)
    }

    private fun getActiveCellularSubtype(context: Context?): Int {
        if (context == null) {
            return -1
        }
        val activeNetworkCapabilities = getActiveNetworkCapabilities(context)
        if (activeNetworkCapabilities == null || !activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return -1
        }
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        return telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
    }

    private fun getActiveCellularSubtypeName(context: Context?): String {
        val activeCellularSubtype = getActiveCellularSubtype(context)
        if (activeCellularSubtype < 0) {
            return ""
        }
        return getNetworkTypeName(activeCellularSubtype) ?: ""
    }

    private fun getNetworkTypeName(networkType: Int): String? {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            4 -> "CDMA"
            5 -> "EVDO_0"
            6 -> "EVDO_A"
            7 -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            11 -> "IDEN"
            12 -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            14 -> "EHRPD"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> CHINA_3G_TD_SCDMA
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            else -> "UNKNOWN"
        }
    }

    private fun joinNetworkPoint(vararg parts: String?): String {
        val stringBuilder = StringBuilder()
        for (part in parts) {
            if (TextUtils.isEmpty(part)) {
                continue
            }
            if (stringBuilder.isNotEmpty()) {
                stringBuilder.append("-")
            }
            stringBuilder.append(part)
        }
        return stringBuilder.toString().lowercase(Locale.ROOT)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun tryDetectCharsetEncoding(url: URL, defaultEncoding: String?): String {
        var encoding = if (TextUtils.isEmpty(defaultEncoding)) "UTF-8" else defaultEncoding!!
        var inputStream: InputStream? = null
        try {
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.connectTimeout = CONNECTION_TIMEOUT
            httpURLConnection.readTimeout = READ_TIMEOUT
            val contentType = httpURLConnection.contentType
                val matcher = ContentTypePattern_Charset.matcher(contentType)
                if (matcher.matches()) {
                    val group = matcher.group(2)
                    if (!TextUtils.isEmpty(group)) {
                        encoding = group!!
                    }
                }
            inputStream = httpURLConnection.inputStream
            val buffer = ByteArray(1024)
            val read = inputStream.read(buffer)
            if (read > 0) {
                val responsePrefix = try {
                    String(buffer, 0, read, Charset.forName(encoding))
                } catch (_: Exception) {
                    String(buffer, 0, read)
                }
                val matcher = ContentTypePattern_XmlEncoding.matcher(responsePrefix)
                if (matcher.find()) {
                    val group = matcher.group(2)
                    if (!TextUtils.isEmpty(group)) {
                        encoding = group!!
                    }
                }
            }
            Log.v(LogTag, "XML charset detected is: $encoding")
            return encoding
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uploadFile(url: String, file: File, postKey: String): String? {
        return uploadFile(url, null, file, postKey)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uploadFile(url: String, headers: Map<String, String>?, file: File, postKey: String): String? {
        if (!file.exists()) {
            return null
        }
        var dataOutputStream: DataOutputStream? = null
        var fileInputStream: FileInputStream? = null
        var bufferedReader: BufferedReader? = null
        try {
            val httpURLConnection = URL(url).openConnection() as HttpURLConnection
            httpURLConnection.readTimeout = READ_TIMEOUT
            httpURLConnection.connectTimeout = CONNECTION_TIMEOUT
            httpURLConnection.doInput = true
            httpURLConnection.doOutput = true
            httpURLConnection.useCaches = false
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive")
            httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=*****")
            headers?.forEach { (key, value) ->
                httpURLConnection.setRequestProperty(key, value)
            }
            httpURLConnection.setFixedLengthStreamingMode(file.name.length + 77 + file.length().toInt() + postKey.length)
            dataOutputStream = DataOutputStream(httpURLConnection.outputStream)
            dataOutputStream.writeBytes("--*****\r\n")
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"$postKey\";filename=\"${file.name}\"\r\n")
            dataOutputStream.writeBytes("\r\n")
            fileInputStream = FileInputStream(file)
            val buffer = ByteArray(1024)
            while (true) {
                val read = fileInputStream.read(buffer)
                if (read == -1) {
                    break
                }
                dataOutputStream.write(buffer, 0, read)
                dataOutputStream.flush()
            }
            dataOutputStream.writeBytes("\r\n")
            dataOutputStream.writeBytes("--")
            dataOutputStream.writeBytes("*****")
            dataOutputStream.writeBytes("--")
            dataOutputStream.writeBytes("\r\n")
            dataOutputStream.flush()
            val stringBuffer = StringBuffer()
            bufferedReader = BufferedReader(InputStreamReader(DoneHandlerInputStream(httpURLConnection.inputStream)))
            var line = bufferedReader.readLine()
            while (line != null) {
                stringBuffer.append(line)
                line = bufferedReader.readLine()
            }
            return stringBuffer.toString()
        } catch (e: IOException) {
            throw IOException("IOException:" + e.javaClass.simpleName)
        } catch (throwable: Throwable) {
            throw IOException(throwable.message)
        } finally {
            IOUtils.closeQuietly(fileInputStream)
            IOUtils.closeQuietly(bufferedReader)
            IOUtils.closeQuietly(dataOutputStream)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun putNullableHeader(headers: MutableMap<String, String>, key: String?, value: String?) {
        (headers as MutableMap<String?, String?>)[key] = value
    }
}
