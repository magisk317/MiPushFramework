package com.xiaomi.network

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.channel.commonutils.network.NameValuePair
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.slim.Blob
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/network/HttpUtils.java
 * Stock 7.4.67-C keeps the resolver fetch flow in y7/j.java; no stock same-path source was found.
 */
abstract class HttpUtils {
    class DefaultHttpGetProcessor : HttpProcessor(HttpProcessor.HTTP_GET) {
        @Throws(IOException::class)
        override fun visit(context: Context, url: String, params: List<NameValuePair>?): String {
            if (params == null) {
                return Network.downloadXml(context, URL(url))
            }
            val builder = Uri.parse(url).buildUpon()
            for (param in params) {
                builder.appendQueryParameter(param.name, param.value)
            }
            return Network.downloadXml(context, URL(builder.toString()))
        }
    }

    companion object {
        @JvmStatic
        fun get(context: Context, url: String, params: List<NameValuePair>?): String? {
            return httpRequest(context, url, params, DefaultHttpGetProcessor(), true)
        }

        @JvmStatic
        fun getHttpGetTxtTraffic(urlLength: Int, contentLength: Int): Int {
            return ((contentLength + 243) / 1448) * 132 + 1080 + urlLength + contentLength
        }

        @JvmStatic
        fun getHttpPostTxtTraffic(urlLength: Int, contentLength: Int, responseLength: Int): Int {
            return ((contentLength + Blob.ERROR_INVALID_CHID) / 1448) * 132 + 1011 + contentLength + urlLength + responseLength
        }

        @JvmStatic
        fun getPostDataLength(params: List<NameValuePair>?): Int {
            var length = 0
            if (params != null) {
                for (param in params) {
                    var currentLength = length
                    if (!TextUtils.isEmpty(param.name)) {
                        currentLength = length + param.name.length
                    }
                    length = currentLength
                    if (!TextUtils.isEmpty(param.value)) {
                        length = currentLength + param.value.length
                    }
                }
            }
            return length * 2
        }

        @JvmStatic
        fun getStringUTF8Length(str: String?): Int {
            if (str.isNullOrEmpty()) {
                return 0
            }
            return try {
                str.toByteArray(charset("UTF-8")).size
            } catch (e: Exception) {
                0
            }
        }

        private fun getTraffic(
            processor: HttpProcessor,
            url: String,
            params: List<NameValuePair>?,
            response: String?
        ): Int {
            return when (processor.requestType) {
                HttpProcessor.HTTP_GET -> getHttpGetTxtTraffic(url.length, getStringUTF8Length(response))
                HttpProcessor.HTTP_POST -> getHttpPostTxtTraffic(url.length, getPostDataLength(params), getStringUTF8Length(response))
                else -> -1
            }
        }

        @JvmStatic
        fun httpRequest(
            context: Context,
            url: String,
            params: List<NameValuePair>?,
            processor: HttpProcessor
        ): String? {
            return httpRequest(context, url, params, processor, true)
        }

        @JvmStatic
        fun httpRequest(
            context: Context,
            url: String,
            params: List<NameValuePair>?,
            processor: HttpProcessor,
            useFallback: Boolean
        ): String? {
            if (!Network.hasNetwork(context)) {
                return null
            }
            try {
                val urlList = ArrayList<String>()
                var fallbackByURL: Fallback? = null
                if (useFallback) {
                    fallbackByURL = HostManager.getInstance().getFallbacksByURL(url)
                    if (fallbackByURL != null) {
                        urlList.addAll(fallbackByURL.getUrls(url))
                    }
                }
                if (!urlList.contains(url)) {
                    urlList.add(url)
                }
                var response: String? = null
                for (targetUrl in urlList) {
                    val currentParams: MutableList<NameValuePair>? =
                        if (params != null) ArrayList(params) else null
                    val startTime = System.currentTimeMillis()
                    try {
                        if (!processor.prepare(context, targetUrl, currentParams)) {
                            break
                        }
                        response = processor.visit(context, targetUrl, currentParams)
                        val traffic = getTraffic(processor, targetUrl, currentParams, response).toLong()
                        if (!response.isNullOrEmpty()) {
                            fallbackByURL?.succeedUrl(targetUrl, System.currentTimeMillis() - startTime, traffic)
                            break
                        }
                        fallbackByURL?.failedUrl(targetUrl, System.currentTimeMillis() - startTime, traffic, null)
                    } catch (e: IOException) {
                        val traffic = getTraffic(processor, targetUrl, currentParams, response).toLong()
                        fallbackByURL?.failedUrl(targetUrl, System.currentTimeMillis() - startTime, traffic, e)
                        e.printStackTrace()
                    }
                }
                return response
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                return null
            }
        }
    }
}
