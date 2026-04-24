package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.push.service.*
import com.xiaomi.smack.packet.*
import com.xiaomi.smack.*
import com.xiaomi.slim.*
import com.xiaomi.push.service.timers.*
import com.xiaomi.push.service.*

import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker


object PushChannelInfoRuntime {
    @JvmStatic
    fun resolveUpdateTarget(
        packageChannelIds: List<String>,
        requestedChannelId: String?,
        requestedUserId: String?,
        pushClientsManager: PushClientsManager
    ): PushChannelInfoUpdateTarget {
        if (packageChannelIds.isEmpty()) {
            return PushChannelInfoUpdateTarget(
                channelId = null,
                client = null,
                reason = "missing_package_channel"
            )
        }
        val resolvedChannelId = requestedChannelId?.takeIf { it.isNotBlank() } ?: packageChannelIds.first()
        val client = if (requestedUserId.isNullOrBlank()) {
            pushClientsManager.getAllClientLoginInfoByChid(resolvedChannelId).firstOrNull()
        } else {
            pushClientsManager.getClientLoginInfoByChidAndUserId(resolvedChannelId, requestedUserId)
        }
        return PushChannelInfoUpdateTarget(
            channelId = resolvedChannelId,
            client = client,
            reason = if (client == null) "missing_client" else "ready"
        )
    }

    @JvmStatic
    fun applyUpdate(
        target: PushChannelInfoUpdateTarget,
        hasClientAttr: Boolean,
        clientAttr: String?,
        hasCloudAttr: Boolean,
        cloudAttr: String?
    ): PushChannelInfoUpdateResult {
        val client = target.client as? PushClientsManager.ClientLoginInfo
        if (client == null) {
            return PushChannelInfoUpdateResult(
                target = target,
                updatedClientExtra = false,
                updatedCloudExtra = false
            )
        }
        var updatedClientExtra = false
        var updatedCloudExtra = false
        if (hasClientAttr && client.clientExtra != clientAttr) {
            client.clientExtra = clientAttr.orEmpty()
            updatedClientExtra = true
        }
        if (hasCloudAttr && client.cloudExtra != cloudAttr) {
            client.cloudExtra = cloudAttr.orEmpty()
            updatedCloudExtra = true
        }
        return PushChannelInfoUpdateResult(
            target = target,
            updatedClientExtra = updatedClientExtra,
            updatedCloudExtra = updatedCloudExtra
        )
    }

    @JvmStatic
    fun observeUpdateResult(result: PushChannelInfoUpdateResult, source: String) {
        val client = result.target.client as? PushClientsManager.ClientLoginInfo
        val action = when {
            client == null -> "channel_info_update_${result.target.reason}"
            result.updatedClientExtra || result.updatedCloudExtra -> "channel_info_updated"
            else -> "channel_info_noop"
        }
        PushRuntime.observeChannelEvent(client?.pkgName, action, source)
        if (client != null && (result.updatedClientExtra || result.updatedCloudExtra)) {
            PushRuntimeChannelTracker.syncNow("$source:sync")
        }
    }
}
