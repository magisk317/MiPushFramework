package io.github.magisk317.mipush.framework.lifecycle.runtime

import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import com.xiaomi.push.service.PushClientsManager

typealias PushPacketRouteAction = com.xiaomi.push.service.PushPacketRouteAction
typealias PushPacketPreparationResult = com.xiaomi.push.service.PushPacketPreparationResult

object PushPacketRuntime {
    @JvmStatic
    fun preparePacket(
        packet: Packet,
        packageName: String,
        session: String?,
        pushClientsManager: PushClientsManager,
        connected: Boolean,
    ): PushPacketPreparationResult {
        return io.github.magisk317.mipush.service.runtime.PushPacketRuntime.preparePacket(
            packet,
            packageName,
            session,
            pushClientsManager,
            connected,
        )
    }

    @JvmStatic
    fun buildBlobForPacket(
        packet: Packet,
        packageName: String,
        session: String?,
        pushClientsManager: PushClientsManager,
        connected: Boolean,
    ): Blob? {
        return io.github.magisk317.mipush.service.runtime.PushPacketRuntime.buildBlobForPacket(
            packet,
            packageName,
            session,
            pushClientsManager,
            connected,
        )
    }
}
