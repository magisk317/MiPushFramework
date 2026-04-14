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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HttpUtils.class */
abstract class HttpUtils {

    class DefaultHttpGetProcessor : HttpProcessor(1) {
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
                1 -> getHttpGetTxtTraffic(url.length, getStringUTF8Length(response))
                2 -> getHttpPostTxtTraffic(url.length, getPostDataLength(params), getStringUTF8Length(response))
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
                val urlList: MutableList<String> = ArrayList()
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
                var currentContext = context
                for (targetUrl in urlList) {
                    val currentParams: MutableList<NameValuePair>? =
                        if (params != null) ArrayList(params) else null
                    val startTime = System.currentTimeMillis()
                    try {
                        if (!processor.prepare(currentContext, targetUrl, currentParams)) {
                            break
                        }
                        response = processor.visit(currentContext, targetUrl, currentParams)
                        if (!response.isNullOrEmpty()) {
                            fallbackByURL?.succeedUrl(
                                targetUrl,
                                System.currentTimeMillis() - startTime,
                                getTraffic(processor, targetUrl, currentParams, response).toLong()
                            )
                            break
                        }
                        fallbackByURL?.failedUrl(
                            targetUrl,
                            System.currentTimeMillis() - startTime,
                            getTraffic(processor, targetUrl, currentParams, response).toLong(),
                            null
                        )
                    } catch (e: IOException) {
                        fallbackByURL?.failedUrl(
                            targetUrl,
                            System.currentTimeMillis() - startTime,
                            getTraffic(processor, targetUrl, currentParams, response).toLong(),
                            e
                        )
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
