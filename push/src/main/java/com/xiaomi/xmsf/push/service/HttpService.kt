@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.push.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import com.xiaomi.channel.commonutils.network.Network
import java.net.MalformedURLException
import java.net.URL

class HttpService : Service() {

    private val binder = object : IHttpService.Stub() {
        override fun doHttpPost(str: String?, map: Map<*, *>?): String? {
            if (isUnmeteredNetworkConnected() && isAcceptedHost(str) && map != null) {
                val postMap = HashMap<String, String>()
                for (key in map.keys) {
                    val value = map[key]
                    if (key != null && value != null) {
                        postMap[key.toString()] = value.toString()
                    }
                }
                return try {
                    Network.doHttpPost(applicationContext, str, postMap).responseString
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }

        private fun isAcceptedHost(str: String?): Boolean {
            if (!TextUtils.isEmpty(str)) {
                try {
                    return acceptedHost.contains(URL(str).host)
                } catch (_: MalformedURLException) {
                }
            }
            return false
        }

        @SuppressLint("NewApi")
        private fun isUnmeteredNetworkConnected(): Boolean {
            val connectivityManager = applicationContext.getSystemService("connectivity") as ConnectivityManager
            val activeNetworkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null) {
                if (activeNetworkInfo.type == 1) {
                    return true
                }
                if (Build.VERSION.SDK_INT >= 16) {
                    return !connectivityManager.isActiveNetworkMetered
                }
            }
            return false
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private val acceptedHost = arrayListOf("data.mistat.xiaomi.com")
    }
}
