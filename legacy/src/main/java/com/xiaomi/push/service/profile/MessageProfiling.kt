package com.xiaomi.push.service.profile
import io.github.magisk317.mipush.protocol.model.*

import android.util.Pair
import com.xiaomi.push.mpcd.Constants
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap

object MessageProfiling {
    private val sSentPerfDatas = Vector<Pair<String, Long>>()
    private val sSendingMessages = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun getPrefString(): String {
        val sb = StringBuilder()
        synchronized(sSentPerfDatas) {
            for (i in 0 until sSentPerfDatas.size) {
                val pair = sSentPerfDatas.elementAt(i)
                sb.append(pair.first)
                sb.append(":")
                sb.append(pair.second)
                if (i < sSentPerfDatas.size - 1) {
                    sb.append(Constants.ITEM_SEPARATOR)
                }
            }
            sSentPerfDatas.clear()
        }
        return sb.toString()
    }

    @JvmStatic
    fun onReceiveSentAck(messageId: String, str2: String) {
        sSendingMessages[messageId]?.let { timestamp ->
            sSentPerfDatas.add(Pair(str2, System.currentTimeMillis() - timestamp))
            sSendingMessages.remove(messageId)
        }
    }

    @JvmStatic
    fun onSendingMessage(messageId: String) {
        sSendingMessages[messageId] = System.currentTimeMillis()
    }
}
