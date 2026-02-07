package com.xiaomi.push.service

import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import org.aspectj.lang.JoinPoint
import top.trumeet.common.BuildConfig

class LogClientEventDispatcherAspect {
    fun notifyPacketArrival(joinPoint: JoinPoint, pushService: XMPushService, chid: String, data: Any) {
        logger.d(joinPoint.signature)
        if (data is Blob) {
            if (BuildConfig.DEBUG) {
                logger.d("blob arrival: $chid; $data")
            }
        } else {
            val packet = data as Packet
            if (BuildConfig.DEBUG) {
                logger.d("packet arrival: $chid; ${packet.toXML()}")
            }
        }
    }

    companion object {
        private val logger: Logger = XLog.tag(LogClientEventDispatcherAspect::class.java.simpleName).build()
    }
}
