package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Message
import android.os.RemoteException
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog

internal object ClientEventDispatcherChannelSupport {
    fun notifyChannelClosed(
        context: Context,
        observer: IPushRuntimeObserver,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        reason: Int
    ) {
        observer.onChannelEvent(
            packageName = clientLoginInfo.pkgName,
            event = "com.xiaomi.push.channel_closed",
            reason = "ClientEventDispatcher.notifyChannelClosed"
        )
        observer.onChannelStateChanged(
            packageName = clientLoginInfo.pkgName,
            chid = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = PushChannelState.Closed,
            reason = "ClientEventDispatcher.notifyChannelClosed",
            reasonCode = reason,
            reasonMsg = null
        )
        observer.syncChannelTracker("ClientEventDispatcher.notifyChannelClosed")
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
        pushAction: IPushServiceAction,
        observer: IPushRuntimeObserver,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        succeeded: Boolean,
        reason: Int,
        reasonMessage: String?,
        pushEventProcessor: MIPushEventProcessor
    ) {
        observer.onChannelEvent(
            packageName = clientLoginInfo.pkgName,
            event = if (succeeded) "com.xiaomi.push.channel_opened" else "com.xiaomi.push.channel_open_failed",
            reason = "ClientEventDispatcher.notifyChannelOpenResult"
        )
        observer.onChannelStateChanged(
            packageName = clientLoginInfo.pkgName,
            chid = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = if (succeeded) PushChannelState.Bound else PushChannelState.OpenFailed,
            reason = "ClientEventDispatcher.notifyChannelOpenResult",
            reasonCode = reason.takeIf { !succeeded },
            reasonMsg = reasonMessage
        )
        observer.syncChannelTracker("ClientEventDispatcher.notifyChannelOpenResult")
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            pushEventProcessor.processChannelOpenResult(pushAction, clientLoginInfo, succeeded, reason, reasonMessage)
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
        sendBroadcast(pushAction.context, intent, clientLoginInfo)
    }

    fun notifyKickedByServer(
        context: Context,
        observer: IPushRuntimeObserver,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        kickType: String?,
        kickReason: String?
    ) {
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            MyLog.e("mipush kicked by server")
            observer.onChannelStateChanged(
                packageName = clientLoginInfo.pkgName,
                chid = clientLoginInfo.chid,
                userId = clientLoginInfo.userId,
                session = clientLoginInfo.session,
                state = PushChannelState.Kicked,
                reason = "ClientEventDispatcher.notifyKickedByServer",
                reasonCode = null,
                reasonMsg = kickReason
            )
            return
        }
        observer.onChannelStateChanged(
            packageName = clientLoginInfo.pkgName,
            chid = clientLoginInfo.chid,
            userId = clientLoginInfo.userId,
            session = clientLoginInfo.session,
            state = PushChannelState.Kicked,
            reason = "ClientEventDispatcher.notifyKickedByServer",
            reasonCode = null,
            reasonMsg = kickReason
        )
        observer.syncChannelTracker("ClientEventDispatcher.notifyKickedByServer")
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

    fun notifyServiceStarted(context: Context, observer: IPushRuntimeObserver) {
        observer.onChannelEvent(
            packageName = context.packageName,
            event = "com.xiaomi.push.service_started",
            reason = "ClientEventDispatcher.notifyServiceStarted"
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
