package com.xiaomi.stats
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.push.thrift.StatsEvent
import com.xiaomi.push.thrift.StatsEvents
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.util.Hashtable

object StatsHelper {
    private const val MAX_KEY_VALUE = 16777215
    private val PING_RTT_VALUE = ChannelStatsType.PING_RTT.value

    private object Holder {
        val sTimeTracker = Hashtable<Int, Long>()
    }

    @JvmStatic
    fun connectFail(host: String, exc: Exception?) {
        try {
            val typeWrapper = StatsAnalyser.fromConnectionException(exc)
            StatsHandler.getInstance().add(StatsEvent().apply {
                type = typeWrapper.type?.value ?: 0
                annotation = typeWrapper.annotation
                this.host = host
            })
        } catch (e: NullPointerException) {
            // Ignore
        }
    }

    @JvmStatic
    fun connectionDown(host: String, exc: Exception?) {
        try {
            val typeWrapper = StatsAnalyser.fromDisconnectEx(exc)
            StatsHandler.getInstance().add(StatsEvent().apply {
                type = typeWrapper.type?.value ?: 0
                annotation = typeWrapper.annotation
                this.host = host
            })
        } catch (e: NullPointerException) {
            // Ignore
        }
    }

    @JvmStatic
    fun count(value: Int) {
        StatsHandler.getInstance().add(StatsEvent().apply {
            type = ChannelStatsType.CHANNEL_STATS_COUNTER.value
            subvalue = value
        })
    }

    @JvmStatic
    fun pingEnded() {
        trackEnd(0, PING_RTT_VALUE, null, -1)
    }

    @JvmStatic
    fun pingStarted() {
        trackStart(0, PING_RTT_VALUE)
    }

    @JvmStatic
    fun retriveStatsAsByte(): ByteArray? {
        val statsEvents = StatsHandler.getInstance().retriveStatsEvents() ?: return null
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(statsEvents)
    }

    @JvmStatic
    fun retriveStatsAsString(): String? {
        val statsEvents = StatsHandler.getInstance().retriveStatsEvents() ?: return null
        val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(statsEvents) ?: return null
        return String(Base64Coder.encode(bytes))
    }

    @JvmStatic
    fun stats(chid: Int, type: Int, value: Int, host: String, subvalue: Int) {
        StatsHandler.getInstance().add(StatsEvent().apply {
            this.chid = chid.toByte()
            this.type = type
            this.value = value
            this.host = host
            this.subvalue = subvalue
        })
    }

    @JvmStatic
    fun statsBind(pushAction: IPushServiceAction, clientLoginInfo: PushClientsManager.ClientLoginInfo) {
        BindTracker(pushAction, clientLoginInfo).track()
    }

    @JvmStatic
    fun statsGslb(host: String, value: Int, exc: Exception?) {
        val statsEvent = StatsHandler.getInstance().createStatsEvent()
        if (value > 0) {
            statsEvent.apply {
                type = ChannelStatsType.GSLB_REQUEST_SUCCESS.value
                this.host = host
                this.value = value
            }
            StatsHandler.getInstance().add(statsEvent)
        } else {
            try {
                val typeWrapper = StatsAnalyser.fromGslbException(exc)
                statsEvent.apply {
                    type = typeWrapper.type?.value ?: 0
                    annotation = typeWrapper.annotation
                    this.host = host
                }
                StatsHandler.getInstance().add(statsEvent)
            } catch (e: NullPointerException) {
                // Ignore
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun trackEnd(chid: Int, type: Int, host: String?, subvalue: Int) {
        try {
            val currentTimeMillis = System.currentTimeMillis()
            val key = (chid shl 24) or type
            val time = Holder.sTimeTracker[key]
            if (time != null) {
                StatsHandler.getInstance().add(StatsEvent().apply {
                    this.type = type
                    value = (currentTimeMillis - time).toInt()
                    if (host != null) this.host = host
                    if (subvalue > -1) this.subvalue = subvalue
                })
                Holder.sTimeTracker.remove(type)
            } else {
                MyLog.e("stats key not found")
            }
        } finally {
            // No cleanup needed
        }
    }

    @JvmStatic
    @Synchronized
    fun trackStart(chid: Int, type: Int) {
        try {
            if (type < MAX_KEY_VALUE) {
                Holder.sTimeTracker[(chid shl 24) or type] = System.currentTimeMillis()
            } else {
                MyLog.e("stats key should less than 16777215")
            }
        } finally {
            // No cleanup needed
        }
    }
}
