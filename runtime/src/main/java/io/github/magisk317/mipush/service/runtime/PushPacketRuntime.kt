package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*
import com.xiaomi.push.service.*
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet

import com.xiaomi.channel.commonutils.logger.MyLog

object PushPacketRuntime {
    @JvmStatic
    fun preparePacket(
        packet: Packet,
        packageName: String,
        session: String?,
        pushClientsManager: PushClientsManager,
        connected: Boolean
    ): PushPacketPreparationResult {
        val packageChannels = pushClientsManager.queryChannelIdByPackage(packageName)
        if (packageChannels.isEmpty()) {
            safeWarn("open channel should be called first before sending a packet, pkg=$packageName")
            return PushPacketPreparationResult(
                action = PushPacketRouteAction.DropNoChannel,
                packet = null,
                client = null,
                reason = "no_channel"
            )
        }

        packet.setPackageName(packageName)
        val resolvedChannelId = packet.channelId?.takeIf { it.isNotBlank() } ?: packageChannels.first().also {
            packet.channelId = it
        }
        val client = pushClientsManager.getClientLoginInfoByChidAndUserId(resolvedChannelId, packet.from)
        if (!connected) {
            safeWarn("drop a packet as the channel is not connected, chid=$resolvedChannelId")
            return PushPacketPreparationResult(
                action = PushPacketRouteAction.DropDisconnected,
                packet = null,
                client = client,
                reason = "channel_not_connected"
            )
        }
        if (client == null || client.status != PushClientsManager.ClientStatus.binded) {
            safeWarn("drop a packet as the channel is not opened, chid=$resolvedChannelId")
            return PushPacketPreparationResult(
                action = PushPacketRouteAction.DropUnbound,
                packet = null,
                client = client,
                reason = "channel_not_opened"
            )
        }
        if (session != client.session) {
            safeWarn("invalid session. $session")
            return PushPacketPreparationResult(
                action = PushPacketRouteAction.DropInvalidSession,
                packet = null,
                client = client,
                reason = "invalid_session"
            )
        }
        return PushPacketPreparationResult(
            action = PushPacketRouteAction.Ready,
            packet = packet,
            client = client,
            reason = "ready"
        )
    }

    @JvmStatic
    fun buildBlobForPacket(
        packet: Packet,
        packageName: String,
        session: String?,
        pushClientsManager: PushClientsManager,
        connected: Boolean
    ): Blob? {
        val prepared = preparePacket(packet, packageName, session, pushClientsManager, connected)
        if (prepared.action != PushPacketRouteAction.Ready) {
            return null
        }
        val resolvedPacket = prepared.packet ?: return null
        val client = prepared.client ?: return null
        @Suppress("DEPRECATION")
        return Blob.from(resolvedPacket, client.security)
    }

    private fun safeWarn(message: String) {
        runCatching { MyLog.w(message) }
    }
}
