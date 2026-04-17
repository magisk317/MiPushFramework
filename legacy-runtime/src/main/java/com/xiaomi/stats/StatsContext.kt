package com.xiaomi.stats

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.push.thrift.StatsEvent
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionListener
import com.xiaomi.smack.SmackConfiguration

class StatsContext(
    val pushService: XMPushService,
    var connection: Connection? = null
) : ConnectionListener {
    companion object {
        private const val NETWORKDURATION_MAX_THRESHOLD = 5400000
    }

    private var mRxBytes: Long = 0
    private var mTxBytes: Long = 0
    private var reason = 0
    private var exception: Exception? = null
    private var networkConnectedTime: Long = 0
    private var accumulatedNetworkDuration: Long = 0
    private var channelConnectedTime: Long = 0
    private var accumulatedChannelDuration: Long = 0
    var connectionPoint: String = ""

    init {
        resetChannelStats()
        val myUid = Process.myUid()
        try {
            mRxBytes = TrafficStats.getUidRxBytes(myUid)
            mTxBytes = TrafficStats.getUidTxBytes(myUid)
        } catch (e: Exception) {
            MyLog.w("Failed to obtain traffic data during initialization: $e")
            mRxBytes = -1
            mTxBytes = -1
        }
    }

    private fun resetChannelStats() {
        accumulatedNetworkDuration = 0
        accumulatedChannelDuration = 0
        networkConnectedTime = 0
        channelConnectedTime = 0
        val elapsedRealtime = SystemClock.elapsedRealtime()
        if (Network.hasNetwork(pushService)) {
            networkConnectedTime = elapsedRealtime
        }
        if (pushService.isConnected) {
            channelConnectedTime = elapsedRealtime
        }
    }

    private fun statsChannelDuration() {
        synchronized(this) {
            MyLog.v("stat connpt = $connectionPoint netDuration = $accumulatedNetworkDuration ChannelDuration = $accumulatedChannelDuration channelConnectedTime = $channelConnectedTime")
            val statsEvent = StatsEvent().apply {
                chid = 0
                setType(ChannelStatsType.CHANNEL_ONLINE_RATE.value)
                connpt = this@StatsContext.connectionPoint
                time = (System.currentTimeMillis() / 1000).toInt()
                value = (accumulatedNetworkDuration / 1000).toInt()
                subvalue = (accumulatedChannelDuration / 1000).toInt()
            }
            StatsHandler.getInstance().add(statsEvent)
            resetChannelStats()
        }
    }

    override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        var uidRxBytes: Long
        var uidTxBytes: Long
        if (this.reason == 0 && exception == null) {
            this.reason = reason
            exception = error
            StatsHelper.connectionDown(connection.getHost() ?: "", error)
        }
        if (reason == 22 && channelConnectedTime != 0L) {
            var lastPingRecv = connection.getLastPingRecv() - channelConnectedTime
            if (lastPingRecv < 0L) {
                lastPingRecv = 0L
            }
            accumulatedChannelDuration += lastPingRecv + (SmackConfiguration.pingInterval / 2)
            channelConnectedTime = 0
        }
        statsChannelIfNeed()
        val myUid = Process.myUid()
        try {
            uidRxBytes = TrafficStats.getUidRxBytes(myUid)
            uidTxBytes = TrafficStats.getUidTxBytes(myUid)
        } catch (e: Exception) {
            MyLog.w("Failed to obtain traffic data: $e")
            uidRxBytes = -1
            uidTxBytes = -1
        }
        MyLog.v("Stats rx=${uidRxBytes - mRxBytes}, tx=${uidTxBytes - mTxBytes}")
        mRxBytes = uidRxBytes
        mTxBytes = uidTxBytes
    }

    override fun connectionStarted(connection: Connection) {
        reason = 0
        exception = null
        this.connection = connection
        connectionPoint = Network.getActiveConnPoint(pushService)
        StatsHelper.trackStart(0, ChannelStatsType.CONN_SUCCESS.value)
    }

    fun getCaughtException(): Exception? = exception

    override fun reconnectionFailed(connection: Connection, error: Exception) {
        StatsHelper.stats(
            0,
            ChannelStatsType.CHANNEL_CON_FAIL.value,
            1,
            connection.getHost() ?: "",
            if (Network.hasNetwork(pushService)) 1 else 0
        )
        statsChannelIfNeed()
    }

    override fun reconnectionSuccessful(connection: Connection) {
        statsChannelIfNeed()
        channelConnectedTime = SystemClock.elapsedRealtime()
        StatsHelper.trackEnd(0, ChannelStatsType.CONN_SUCCESS.value, connection.getHost() ?: "", connection.getConnTryTimes())
    }

    @Synchronized
    fun statsChannelIfNeed() {
        val pushService = this.pushService

        val activeConnPoint = Network.getActiveConnPoint(pushService)
        val hasNetwork = Network.hasNetwork(pushService)
        val elapsedRealtime = SystemClock.elapsedRealtime()
        val networkTime = networkConnectedTime
        if (networkTime > 0) {
            accumulatedNetworkDuration += elapsedRealtime - networkTime
            networkConnectedTime = 0
        }
        val channelTime = channelConnectedTime
        if (channelTime != 0L) {
            accumulatedChannelDuration += elapsedRealtime - channelTime
            channelConnectedTime = 0
        }
        if (hasNetwork) {
            if ((connectionPoint != activeConnPoint && accumulatedNetworkDuration > MessageInfoContract.TIMEOUT) ||
                accumulatedNetworkDuration > NETWORKDURATION_MAX_THRESHOLD
            ) {
                statsChannelDuration()
            }
            connectionPoint = activeConnPoint
            if (networkConnectedTime == 0L) {
                networkConnectedTime = elapsedRealtime
            }
            if (pushService.isConnected) {
                channelConnectedTime = elapsedRealtime
            }
        }
    }
}
