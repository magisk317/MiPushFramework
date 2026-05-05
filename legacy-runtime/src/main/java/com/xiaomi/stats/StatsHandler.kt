package com.xiaomi.stats

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.stats.Stats
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.ServiceConfig
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.push.thrift.StatsEvent
import com.xiaomi.push.thrift.StatsEvents
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TException
import org.apache.thrift.protocol.TProtocol
import org.apache.thrift.protocol.XmPushTBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.LinkedList

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/oa/d.java
 * Stock class name is obfuscated as oa.d; this file keeps the deobfuscated com.xiaomi.stats.StatsHandler API.
 */
class StatsHandler private constructor() {
    private var context: StatsContext? = null
    private var duration = 0
    private var startTime: Long = 0
    private var uuid: String? = null
    private var allowStatsUpload = false
    private val statsContainer = Stats.instance()

    private fun from(item: Stats.Item): StatsEvent? {
        return if (item.key != 0) {
            createStatsEvent().apply {
                type = ChannelStatsType.CHANNEL_STATS_COUNTER.value
                subvalue = item.key
                annotation = item.annotation
            }
        } else if (item.obj is StatsEvent) {
            item.obj as StatsEvent
        } else {
            null
        }
    }

    private fun internalAdd(chid: Int, type: Int, value: Int, host: String, connpt: String) {
        val statsEvent = StatsEvent().apply {
            this.chid = chid.toByte()
            this.type = type
            this.value = value
            this.connpt = connpt
            this.host = host
            time = (System.currentTimeMillis() / 1000).toInt()
        }
        statsContainer.`stat`(statsEvent)
        MyLog.v(String.format("add stats: chid = %s, type =%d, value = %d, connpt = %s", chid, type, value, connpt))
    }

    private fun retriveStatsEvents(maxSize: Int): StatsEvents {
        val arrayList = ArrayList<StatsEvent>()
        val statsEvents = StatsEvents(uuid ?: "", arrayList)
        val pushService = context?.pushService
        if (pushService != null && !Network.isWIFIConnected(pushService)) {
            statsEvents.setOperator(com.xiaomi.channel.commonutils.android.DeviceInfo.getSimOperatorName(pushService))
        }
        val tMemoryBuffer = TMemoryBuffer(maxSize)
        val protocol: TProtocol = XmPushTBinaryProtocol.Factory().getProtocol(tMemoryBuffer)
        try {
            statsEvents.write(protocol)
        } catch (e: TException) {
            // Ignore
        }
        val stats: LinkedList<Stats.Item> = statsContainer.stats
        while (stats.isNotEmpty()) {
            try {
                val statsEvent = from(stats.last())
                if (statsEvent != null) {
                    statsEvent.write(protocol)
                }
                if (tMemoryBuffer.length() > maxSize) {
                    break
                }
                if (statsEvent != null) {
                    arrayList.add(statsEvent)
                }
                stats.removeLast()
            } catch (e: NoSuchElementException) {
                // Ignore
            } catch (e: TException) {
                // Ignore
            }
        }
        return statsEvents
    }

    private fun stopStatsIfNeed() {
        if (allowStatsUpload && System.currentTimeMillis() - startTime > duration) {
            allowStatsUpload = false
            startTime = 0
        }
    }

    fun add(chid: Int, type: Int, value: Int, host: String) {
        synchronized(this) {
            if (uuid == null) {
                MyLog.v("StatsHandler.add() Should initialized before add")
                return
            }
            val pushService = context?.pushService ?: return
            val activeConnPoint = Network.getActiveConnPoint(pushService)
            if (activeConnPoint.isNotEmpty()) {
                internalAdd(chid, type, value, host, activeConnPoint)
            }
        }
    }

    fun add(statsEvent: StatsEvent) {
        synchronized(this) {
            statsContainer.`stat`(statsEvent)
        }
    }

    fun createStatsEvent(): StatsEvent {
        return synchronized(this) {
            StatsEvent().apply {
                val pushService = context?.pushService
                if (pushService != null) {
                    connpt = Network.getActiveConnPoint(pushService)
                }
                chid = 0
                value = 1
                time = (System.currentTimeMillis() / 1000).toInt()
            }
        }
    }

    fun init(xMPushService: XMPushService) {
        synchronized(this) {
            context = StatsContext(xMPushService)
            uuid = ""
            ServiceConfig.getInstance().addListener(object : ServiceConfig.Listener() {
                override fun onConfigMsgReceive(pushServiceConfigMsg: ChannelMessage.PushServiceConfigMsg) {
                    if (pushServiceConfigMsg.hasDots()) {
                        getInstance().setDuration(pushServiceConfigMsg.dots)
                    }
                }
            })
        }
    }

    fun isAllowStats(): Boolean = allowStatsUpload

    fun retriveStatsEvents(): StatsEvents? {
        return synchronized(this) {
            if (shouldSendStatsNow()) {
                val maxSize = if (context?.pushService?.let { Network.isWIFIConnected(it) } == true) {
                    MIN_STATS_PING_SIZE
                } else {
                    MIN_STATS_PING_SIZE / 2
                }
                retriveStatsEvents(maxSize)
            } else {
                null
            }
        }
    }

    fun setDuration(i: Int) {
        if (i > 0) {
            val durationMs = i * 1000
            val clampedDuration = if (durationMs > MAX_DURATION) MAX_DURATION else durationMs
            if (this.duration == clampedDuration && allowStatsUpload) {
                return
            }
            allowStatsUpload = true
            startTime = System.currentTimeMillis()
            duration = clampedDuration
            MyLog.v("enable dot duration = $clampedDuration start = $startTime")
        }
    }

    fun shouldSendStatsNow(): Boolean {
        stopStatsIfNeed()
        return allowStatsUpload && statsContainer.getCount() > 0
    }

    companion object {
        private const val MAX_DURATION = 604800000
        private const val MIN_STATS_PING_SIZE = 750

        private object Holder {
            val sStatsHandler = StatsHandler()
        }

        @JvmStatic
        fun getContext(): StatsContext? = synchronized(Holder.sStatsHandler) {
            Holder.sStatsHandler.context
        }

        @JvmStatic
        fun getInstance(): StatsHandler = Holder.sStatsHandler
    }
}
