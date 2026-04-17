package com.xiaomi.push.service

import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.network.Fallback
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.network.WeightedHost
import com.xiaomi.push.protobuf.ChannelConfig
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.slim.Blob
import com.xiaomi.stats.StatsHandler
import com.xiaomi.stats.StatsHelper
import java.io.IOException
import java.net.URL
import java.util.ArrayList

class PushHostManagerFactory(
    private val pushService: XMPushService,
) : ServiceConfig.Listener(), HostManager.HostManagerFactory {

    inner class GslbHttpGet : HostManager.HttpGet {
        @Throws(IOException::class)
        override fun doGet(url: String): String {
            val request = pushService.runtimeObserver.buildGslbRequest(
                url,
                41,
                Build.VERSION.SDK_INT,
                Build.MODEL,
                Build.VERSION.INCREMENTAL,
                SystemUtils.getMIUIType(),
            )
            MyLog.v("fetch bucket from : ${request.requestUrl}")
            val requestUrl = URL(request.requestUrl)
            return try {
                val startedAt = System.currentTimeMillis()
                val response = Network.downloadXml(SystemUtils.context!!, requestUrl)
                StatsHelper.statsGslb(request.statsHostPort, (System.currentTimeMillis() - startedAt).toInt(), null)
                response
            } catch (e: IOException) {
                StatsHelper.statsGslb(request.statsHostPort, -1, e)
                throw e
            }
        }
    }

    companion object {
        private const val MIN_BUCKET_FETCH_DURATION = 3600000L
        private const val VERSION = "2.2"

        @JvmStatic
        fun init(pushService: XMPushService) {
            val factory = PushHostManagerFactory(pushService)
            ServiceConfig.getInstance().addListener(factory)
            synchronized(HostManager::class.java) {
                HostManager.factory = factory
                HostManager.init(pushService, null, factory.GslbHttpGet(), Blob.CLIENT_PING_ID, "push", VERSION)
            }
        }
    }

    private var lastFetchTime = 0L

    class PushHostManager(
        private val mContext: Context,
        hostFilter: HostFilter?,
        httpGet: HostManager.HttpGet?,
        userId: String,
    ) : HostManager(mContext, hostFilter, httpGet, userId) {
        @Throws(IOException::class)
        override fun getRemoteFallbackJSON(
            arrayList: ArrayList<String>,
            str: String,
            str2: String,
            z: Boolean,
        ): String? {
            return try {
                val effectiveUuid = if (StatsHandler.getInstance().isAllowStats()) {
                    ServiceConfig.getDeviceUUID() ?: str2
                } else {
                    str2
                }
                super.getRemoteFallbackJSON(arrayList, str, effectiveUuid, z)
            } catch (e: IOException) {
                StatsHelper.stats(0, ChannelStatsType.GSLB_ERR.value, 1, "", if (Network.hasNetwork(mContext)) 1 else 0)
                throw e
            }
        }
    }

    override fun createHostManager(
        context: Context,
        hostFilter: HostFilter?,
        httpGet: HostManager.HttpGet?,
        userId: String,
    ): HostManager {
        return PushHostManager(context, hostFilter, httpGet, userId)
    }

    override fun onConfigChange(pushServiceConfig: ChannelConfig.PushServiceConfig) = Unit

    override fun onConfigMsgReceive(pushServiceConfigMsg: ChannelMessage.PushServiceConfigMsg) {
        val now = System.currentTimeMillis()
        val fetchPlan = pushService.runtimeObserver.decideBucketFetch(
            pushServiceConfigMsg.hasFetchBucket() && pushServiceConfigMsg.fetchBucket,
            lastFetchTime,
            now,
            MIN_BUCKET_FETCH_DURATION,
        )
        pushService.runtimeObserver.onChannelEvent(null, fetchPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
        if (!fetchPlan.shouldRefresh) {
            return
        }
        MyLog.w("fetch bucket :${pushServiceConfigMsg.fetchBucket}")
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
            val reconnectPlan = pushService.runtimeObserver.decideBucketReconnect(false, null, arrayListOf())
            pushService.runtimeObserver.onChannelEvent(null, reconnectPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
            return
        }
        val fallbacksByHost: Fallback? = hostManager.getFallbacksByHost(currentConnection.config.host!!, false)
        val hosts = fallbacksByHost?.getHosts() ?: arrayListOf<String>()
        val reconnectPlan = pushService.runtimeObserver.decideBucketReconnect(true, currentConnection.getHost()!!, hosts)
        pushService.runtimeObserver.onChannelEvent(null, reconnectPlan.eventAction, "PushHostManagerFactory.onConfigMsgReceive")
        if (!reconnectPlan.shouldReconnect) {
            return
        }
        MyLog.w("bucket changed, force reconnect")
        pushService.runtimeObserver.onConnectionStateChanged(
            stateName = "Disconnected",
            reason = "PushHostManagerFactory.onConfigMsgReceive",
            host = currentConnection.getHost() ?: "",
            message = reconnectPlan.connectionStateReason ?: "",
        )
        pushService.disconnect(0, null)
        pushService.scheduleConnect(false)
    }
}
