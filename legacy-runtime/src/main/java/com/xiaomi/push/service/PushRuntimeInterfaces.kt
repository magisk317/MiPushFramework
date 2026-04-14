package com.xiaomi.push.service

import com.xiaomi.smack.packet.Packet

/**
 * Interface for components that can send packets asynchronously.
 */
interface IPendingPacketSender {
    fun sendPacket(packet: Packet)
}

/**
 * Interface for components that handle error notifications for packets.
 */
interface IPendingPacketErrorNotifier {
    fun notifyError(errorCode: Int, errorMessage: String)
}

/**
 * Interface for notification handling logic.
 */
interface IPushNotificationHandler {
    fun handleNotification(packageName: String, payload: ByteArray): Boolean
    fun clearNotification(packageName: String, notifyId: Int)
}
