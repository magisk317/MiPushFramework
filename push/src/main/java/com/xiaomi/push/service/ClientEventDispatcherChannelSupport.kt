package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Message
import android.os.RemoteException
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.xmsf.runtime.PushChannelState
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.runtime.PushRuntimeChannelTracker

internal object ClientEventDispatcherChannelSupport {
    fun notifyChannelClosed(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        reason: Int
    ) {
        PushRuntime.observeChannelEvent(
            packageName = clientLoginInfo.pkgName,
            action = "com.xiaomi.push.channel_closed",
            source = "ClientEventDispatcher.notifyChannelClosed"
        )
        PushRuntime.observeChannelState(
            packageName = clientLoginInfo.pkgName,
            channelId = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = PushChannelState.Closed,
            source = "ClientEventDispatcher.notifyChannelClosed",
            reasonCode = reason
        )
        PushRuntimeChannelTracker.syncNow("ClientEventDispatcher.notifyChannelClosed")
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) return
        val intent = Intent().apply {
            action = "com.xiaomi.push.channel_closed"
            `package` = clientLoginInfo.pkgName
            putExtra(PushConstants.EXTRA_CHANNEL_ID, clientLoginInfo.chid)
            putExtra("ext_reason", reason)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        sendToPeerOrBroadcast(context, intent, clientLoginInfo, true)
    }

    fun notifyChannelOpenResult(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        succeeded: Boolean,
        reason: Int,
        reasonMessage: String?,
        pushEventProcessor: MIPushEventProcessor
    ) {
        PushRuntime.observeChannelEvent(
            packageName = clientLoginInfo.pkgName,
            action = if (succeeded) "com.xiaomi.push.channel_opened" else "com.xiaomi.push.channel_open_failed",
            source = "ClientEventDispatcher.notifyChannelOpenResult"
        )
        PushRuntime.observeChannelState(
            packageName = clientLoginInfo.pkgName,
            channelId = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = if (succeeded) PushChannelState.Bound else PushChannelState.OpenFailed,
            source = "ClientEventDispatcher.notifyChannelOpenResult",
            reasonCode = reason.takeIf { !succeeded },
            reasonMessage = reasonMessage
        )
        PushRuntimeChannelTracker.syncNow("ClientEventDispatcher.notifyChannelOpenResult")
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            pushEventProcessor.processChannelOpenResult(context, clientLoginInfo, succeeded, reason, reasonMessage)
            return
        }
        val intent = Intent().apply {
            action = "com.xiaomi.push.channel_opened"
            `package` = clientLoginInfo.pkgName
            putExtra("ext_succeeded", succeeded)
            if (!succeeded) putExtra("ext_reason", reason)
            if (!reasonMessage.isNullOrEmpty()) putExtra("ext_reason_msg", reasonMessage)
            putExtra("ext_chid", clientLoginInfo.chid)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        sendBroadcast(context, intent, clientLoginInfo)
    }

    fun notifyKickedByServer(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        kickType: String?,
        kickReason: String?
    ) {
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            MyLog.e("mipush kicked by server")
            PushRuntime.observeChannelState(
                packageName = clientLoginInfo.pkgName,
                channelId = clientLoginInfo.chid,
                userId = clientLoginInfo.userId,
                session = clientLoginInfo.session,
                state = PushChannelState.Kicked,
                source = "ClientEventDispatcher.notifyKickedByServer",
                reasonMessage = kickReason
            )
            return
        }
        PushRuntime.observeChannelState(
            packageName = clientLoginInfo.pkgName,
            channelId = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = PushChannelState.Kicked,
            source = "ClientEventDispatcher.notifyKickedByServer",
            reasonMessage = kickReason
        )
        PushRuntimeChannelTracker.syncNow("ClientEventDispatcher.notifyKickedByServer")
        val intent = Intent().apply {
            action = "com.xiaomi.push.kicked"
            `package` = clientLoginInfo.pkgName
            putExtra("ext_kick_type", kickType)
            putExtra("ext_kick_reason", kickReason)
            putExtra("ext_chid", clientLoginInfo.chid)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        sendBroadcast(context, intent, clientLoginInfo)
    }

    fun notifyServiceStarted(context: Context) {
        PushRuntime.observeChannelEvent(
            packageName = context.packageName,
            action = "com.xiaomi.push.service_started",
            source = "ClientEventDispatcher.notifyServiceStarted"
        )
        val intent = Intent().apply {
            action = "com.xiaomi.push.service_started"
            if (MIUIUtils.isXMS()) {
                addFlags(0x1000000)
            }
        }
        context.sendBroadcast(intent)
    }

    fun getReceiverPermission(clientLoginInfo: PushClientsManager.ClientLoginInfo): String {
        return if ("9" != clientLoginInfo.chid) {
            clientLoginInfo.pkgName + ".permission.MIPUSH_RECEIVE"
        } else {
            clientLoginInfo.pkgName + ".permission.MIMC_RECEIVE"
        }
    }

    fun sendBroadcast(
        context: Context,
        intent: Intent,
        clientLoginInfo: PushClientsManager.ClientLoginInfo
    ) {
        if ("com.xiaomi.xmsf" == context.packageName) {
            context.sendBroadcast(intent)
        } else {
            context.sendBroadcast(intent, getReceiverPermission(clientLoginInfo))
        }
    }

    private fun sendToPeerOrBroadcast(
        context: Context,
        intent: Intent,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        allowPeer: Boolean
    ) {
        val peer = clientLoginInfo.peer
        if (allowPeer && peer != null && "9" == clientLoginInfo.chid) {
            val msg = Message.obtain(null, 17, intent)
            try {
                peer.send(msg)
                return
            } catch (_: RemoteException) {
                clientLoginInfo.peer = null
                val userId = clientLoginInfo.userId
                MyLog.w("peer may died: " + userId.substring(userId.lastIndexOf('@')))
            }
        }
        sendBroadcast(context, intent, clientLoginInfo)
    }
}
