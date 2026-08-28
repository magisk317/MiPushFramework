package io.github.magisk317.mipush.notification

import android.content.Context
import com.xiaomi.push.service.NotificationManagerHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.platform.support.XMPushUtils

/** Shell-local channel lookup and stock-compatible provisioning policy. */
internal object NotificationChannelResolutionSupport {
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        findExistingChannelId(context, metaInfo, packageName)?.let { return it }

        val stockChannelId = NotificationChannelManager.getChannelId(context, metaInfo, packageName)
        NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
        logD("getExistsChannelId() provisioned stock channel pkg=$packageName channel=$stockChannelId")
        return stockChannelId
    }

    /** Lookup-only channel resolution for read paths; never creates groups or channels. */
    fun findExistingChannelId(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
    ): String? {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val preferredBorrowed = custom.borrowChannelId(null)?.takeIf { it.isNotBlank() }
        if (preferredBorrowed != null) {
            val borrowedChannel = NotificationManagerEx.findPreferredTargetChannel(packageName, preferredBorrowed)
            if (borrowedChannel != null) {
                logD("getExistsChannelId() explicit borrow channel pkg=$packageName channel=${borrowedChannel.id}")
                return borrowedChannel.id
            }
            logD("getExistsChannelId() requested borrow channel unavailable pkg=$packageName channel=$preferredBorrowed")
        }
        val stockChannelId = NotificationChannelManager.getChannelId(context, metaInfo, packageName)
        // Before the stock 7.4.67-C channel alignment this path created ch_* IDs. Probe
        // both identities so existing channels retain user settings, while a missing
        // channel is always provisioned with the stock-owned ID used by clear semantics.
        val legacyChannelId = NotificationChannelManager.getLegacyChannelId(metaInfo, packageName)
        val sourceChannelId = custom.channelId(null)?.takeIf(String::isNotBlank)
        val stockManager = NotificationManagerHelper.from(context.applicationContext, packageName)
        val stockChannelExists = stockManager.getNotificationChannel(stockChannelId) != null
        val legacyChannelExists = NotificationManagerEx
            .getNotificationChannel(packageName, legacyChannelId) != null
        val selectedChannelId = selectManagedChannelId(
            stockChannelId = stockChannelId,
            stockChannelExists = stockChannelExists,
            legacyChannelId = legacyChannelId,
            legacyChannelExists = legacyChannelExists,
        )
        if (stockChannelExists || legacyChannelExists) {
            // Reuse a pre-migration ch_* channel only when it already exists. This preserves
            // user importance/sound choices while all newly provisioned channels move to the
            // stock namespace used by notification ownership and clear semantics.
            logD(
                "getExistsChannelId() reuse managed channel pkg=$packageName " +
                    "source=$sourceChannelId channel=$selectedChannelId legacy=${selectedChannelId == legacyChannelId}",
            )
            return selectedChannelId
        }
        return null
    }

    fun selectManagedChannelId(
        stockChannelId: String,
        stockChannelExists: Boolean,
        legacyChannelId: String,
        legacyChannelExists: Boolean,
    ): String = when {
        // Prefer stock when both exist so future posts converge without deleting the legacy
        // channel. Android channel settings are ID-scoped, so deleting or renaming it here
        // would discard user choices and can cause an unexpected alerting behavior change.
        stockChannelExists -> stockChannelId
        legacyChannelExists -> legacyChannelId
        else -> stockChannelId
    }
}
