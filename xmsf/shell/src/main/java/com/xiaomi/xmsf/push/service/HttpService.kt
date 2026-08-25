package com.xiaomi.xmsf.push.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class HttpService : Service() {

    private val binder = object : IHttpService.Stub() {
        override fun doHttpPost(str: String?, map: Map<*, *>?): String? {
            return fakeUploadWithConcurrencyLimit(str)
        }

        override fun doHttpPostIntl(str: String?, map: Map<*, *>?): String? {
            return fakeUploadWithConcurrencyLimit(str)
        }

        private fun fakeUploadWithConcurrencyLimit(url: String?): String? {
            if (activeRequests.incrementAndGet() > MAX_CONCURRENT_REQUESTS) {
                activeRequests.decrementAndGet()
                return null
            }
            return try {
                fakeUploadResponse(url)
            } finally {
                activeRequests.decrementAndGet()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private const val MAX_CONCURRENT_REQUESTS = 5
        private val activeRequests = AtomicInteger(0)
        private val acceptedHost = setOf(
            "data.mistat.xiaomi.com",
            "data.mistat.intl.xiaomi.com",
            "data.mistat.india.xiaomi.com",
            "data.mistat.rus.xiaomi.com",
        )

        /**
         * Stock XMSF intercepts these MiStat endpoints and returns canned success/config data. It
         * does not proxy the caller's telemetry to the network. Keeping this helper local also
         * preserves the product-wide telemetry opt-out enforced by TelemetryDisabler.
         */
        internal fun fakeUploadResponse(rawUrl: String?): String? {
            if (rawUrl.isNullOrEmpty()) return null
            val url = try {
                URL(rawUrl)
            } catch (_: MalformedURLException) {
                return null
            }
            if (url.host !in acceptedHost || url.path.isNullOrEmpty()) return null
            return when (url.path) {
                "/realtime_network" ->
                    """{"status":"ok","description":"success.","data":{"ban_time":0,"sample_rate":0,"delay":300000},"code":0}"""
                "/mistats/v3", "/mistats/js", "/mistats/iosv3" ->
                    """{"msg":"write to xlogger success","code":"200"}"""
                "/mistats", "/mistats/v2", "/micrash" ->
                    """{"status":"ok","reason":"","description":"","code":0}"""
                "/getsdkconfig" ->
                    """{"errorCode":0,"reason":"get all sdk config","abtest-url":{""" +
                        """"CN":"abtest.mistat.xiaomi.com","IN":"abtest.mistat.india.xiaomi.com",""" +
                        """"INTL":"abtest.mistat.intl.xiaomi.com"},"region-url":{""" +
                        """"CN":"data.mistat.xiaomi.com","IN":"data.mistat.india.xiaomi.com",""" +
                        """"INTL":"data.mistat.intl.xiaomi.com"}}"""
                "/get_all_config" ->
                    """{"errorCode":0,"reason":"get all config","region-url":{""" +
                        """"CN":"data.mistat.xiaomi.com","IN":"data.mistat.india.xiaomi.com",""" +
                        """"INTL":"data.mistat.intl.xiaomi.com","RU":"data.mistat.rus.xiaomi.com"},""" +
                        """"configDelay":"0-2400","configNetwork":15,"uploadInterval":900000,""" +
                        """"uploadSwitch":992,"time":${System.currentTimeMillis()},"enableSample":"false"}"""
                "/key_get" ->
                    """{"code":1,"msg":"success","result":{""" +
                        """"key":"7516ed877b237d109bc51d02bb2fd04ec2ddd2bc995d0ec0f7fd1f5e333e9e05354f11ac7e9071445f4128e02f4aed3e",""" +
                        """"sid":"9d24c7a4c20fa9fc6f30ebb1226d594d60891f65b8e965be9cada7c5c86956fdbc0f86ce0970264401a6e6a3776ce177"},""" +
                        """"curTime":${System.currentTimeMillis()}}"""
                "/getconfig" ->
                    """{"errorCode":-2,"reason":"no changing","result":"null"}"""
                "/idservice/deviceid_get" ->
                    """{"success":true,"code":1,"msg":null,"timestamp":${System.currentTimeMillis()},"device_id":"O9C541345-E92B-4692-AE1D-5BC41BCBCA3A"}"""
                else -> null
            }
        }
    }
}
