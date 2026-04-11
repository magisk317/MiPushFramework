package com.xiaomi.push.service

import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.network.Fallback
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.push.protobuf.ChannelConfig
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.stats.StatsHandler
import com.xiaomi.stats.StatsHelper
import com.xiaomi.xmsf.runtime.PushConnectionState
import com.xiaomi.xmsf.runtime.PushRuntime
import java.io.IOException
import java.net.URL
import java.util.ArrayList

class PushHostManagerFactory(
    private val pushService: XMPushService,
) : ServiceConfig.Listener(), HostManager.HostManagerFactory {
    companion object {
        private const val MIN_BUCKET_FETCH_DURATION = 3600000L
        private const val VERSION = "2.2"

        @JvmStatic
        fun init(pushService: XMPushService) {
            val factory = PushHostManagerFactory(pushService)
            ServiceConfig.getInstance().addListener(factory)
            synchronized(HostManager::class.java) {
                HostManager.setHostManagerFactory(factory)
                HostManager.init(pushService, null, GslbHttpGet(), Blob.CLIENT_PING_ID, "push", VERSION)
            }
        }
    }

    private var lastFetchTime = 0L

    class GslbHttpGet : HostManager.HttpGet {
        @Throws(IOException::class)
        override fun doGet(url: String): String {
            val request = PushHostRuntime.buildGslbRequest(
                url,
                41,
                Build.VERSION.SDK_INT,
                Build.MODEL,
                Build.VERSION.INCREMENTAL,
                SystemUtils.getMIUIType(),
            )
            MyLog.v("fetch bucket from : ${request.requestUrl}")
            val requestUrl = URL(request.requestUrl)
            PushRuntime.observeChannelEvent(null, "gslb_fetch_request", "PushHostManagerFactory.GslbHttpGet")
            return try {
                val startedAt = System.currentTimeMillis()
                val response = Network.downloadXml(SystemUtils.getContext(), requestUrl)
                StatsHelper.statsGslb(request.statsHostPort, (System.currentTimeMillis() - startedAt).toInt(), null)
                response
            } catch (e: IOException) {
                PushRuntime.observeChannelEvent(null, "gslb_fetch_failed", "PushHostManagerFactory.GslbHttpGet")
                StatsHelper.statsGslb(request.statsHostPort, -1, e)
                throw e
            }
        }
    }

    class PushHostManager(
        context: Context,
        hostFilter: HostFilter?,
        httpGet: HostManager.HttpGet,
        userId: String,
    ) : HostManager(context, hostFilter, httpGet, userId) {
        @Throws(IOException::class)
        override fun getRemoteFallbackJSON(
            hosts: ArrayList<String>,
            requestHost: String,
            uuid: String?,
            forceRefresh: Boolean,
        ): String {
            return try {
                val effectiveUuid = if (StatsHandler.getInstance().isAllowStats) {
                    ServiceConfig.getDeviceUUID()
                } else {
                    uuid
                }
                super.getRemoteFallbackJSON(hosts, requestHost, effectiveUuid, forceRefresh)
            } catch (e: IOException) {
                StatsHelper.stats(0, ChannelStatsType.GSLB_ERR.value, 1, null, if (Network.hasNetwork(sAppContext)) 1 else 0)
                throw e
            }
        }
    }

    override fun createHostManager(
        context: Context,
        hostFilter: HostFilter?,
        httpGet: HostManager.HttpGet,
        userId: String,
    ): HostManager {
        return PushHostManager(context, hostFilter, httpGet, userId)
    }

    override fun onConfigChange(pushServiceConfig: ChannelConfig.PushServiceConfig) = Unit

    override fun onConfigMsgReceive(configMsg: ChannelMessage.PushServiceConfigMsg) {
        val now = System.currentTimeMillis()
        val fetchPlan = PushHostRuntime.decideBucketFetch(
            configMsg.hasFetchBucket() && configMsg.fetchBucket,
            lastFetchTime,
            now,
            MIN_BUCKET_FETCH_DURATION,
        )
        PushRuntime.observeChannelEvent(null, fetchPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
        if (!fetchPlan.shouldRefresh) {
            return
        }
        MyLog.w("fetch bucket :${configMsg.fetchBucket}")
        lastFetchTime = now
        val hostManager = HostManager.getInstance()
        hostManager.clear()
        try {
            hostManager.refreshFallbacks()
        } catch (t: Throwable) {
            MyLog.e(t)
        }
        val currentConnection = pushService.currentConnection
        if (currentConnection == null) {
            val reconnectPlan = PushHostRuntime.decideBucketReconnect(false, null, arrayListOf())
            PushRuntime.observeChannelEvent(null, reconnectPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
            return
        }
        val fallbacksByHost: Fallback? = hostManager.getFallbacksByHost(currentConnection.configuration.host)
        val hosts = fallbacksByHost?.hosts ?: arrayListOf()
        val reconnectPlan = PushHostRuntime.decideBucketReconnect(true, currentConnection.host, hosts)
        PushRuntime.observeChannelEvent(null, reconnectPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
        if (!reconnectPlan.shouldReconnect) {
            return
        }
        MyLog.w("bucket changed, force reconnect")
        PushRuntime.observeConnectionState(
            PushConnectionState.Disconnected,
            "PushHostManagerFactory.onConfigMsgReceive",
            currentConnection.host,
            reconnectPlan.connectionStateReason,
        )
        pushService.disconnect(0, null)
        pushService.scheduleConnect(false)
    }
}
