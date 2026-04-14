package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.stats.StatsHelper
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException

class BindJob(
    private val pushAction: IPushServiceAction,
    val loginInfo: PushClientsManager.ClientLoginInfo,
) : XMPushServiceJob(XMPushServiceJob.TYPE_BIND_UNBIND) {
    override fun getDesc(): String = "bind the client. ${loginInfo.chid}"

    override fun process() {
        try {
            if (!pushAction.isConnected) {
                MyLog.e("trying bind while the connection is not created, quit!")
                return
            }
            val activeClient = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(loginInfo.chid, loginInfo.userId)
            when {
                activeClient == null -> MyLog.w("ignore bind because the channel ${loginInfo.chid} is removed ")
                activeClient.status == PushClientsManager.ClientStatus.unbind -> {
                    activeClient.setStatus(PushClientsManager.ClientStatus.binding, 0, 0, null, null)
                    pushAction.currentConnection?.let { connection ->
                        connection.bind(activeClient)
                        StatsHelper.statsBind(pushAction, activeClient)
                    }
                }
                else -> MyLog.w("trying duplicate bind, ingore! ${activeClient.status}")
            }
        } catch (e: Exception) {
            MyLog.e(e)
            pushAction.disconnect(10, e)
        }
    }
}

class BindTimeoutJob(
    private val loginInfo: PushClientsManager.ClientLoginInfo,
) : XMPushServiceJob(XMPushServiceJob.TYPE_BIND_TIMEOUT) {
    companion object {
        const val BIND_TIMEOUT = 60000
    }

    override fun equals(other: Any?): Boolean {
        return other is BindTimeoutJob && other.loginInfo.chid == loginInfo.chid
    }

    override fun hashCode(): Int = loginInfo.chid.hashCode()

    override fun getDesc(): String = "bind time out. chid=${loginInfo.chid}"

    override fun process() {
        loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, 1, PushConstants.ERROR_BIND_TIMEOUT, null, null)
    }
}

class PingJob(
    private val pushAction: IPushServiceAction,
    private val isPong: Boolean = false,
) : XMPushServiceJob(XMPushServiceJob.TYPE_SEND_MSG) {
    override fun getDesc(): String = "send ping.."

    override fun process() {
        if (!pushAction.isConnected) {
            return
        }
        try {
            if (!isPong) {
                StatsHelper.pingStarted()
            }
            pushAction.currentConnection?.sendPing(isPong)
        } catch (e: XMPPException) {
            MyLog.e(e)
            pushAction.disconnect(10, e)
        }
    }
}

class ReBindJob(
    private val pushAction: IPushServiceAction,
    val loginInfo: PushClientsManager.ClientLoginInfo,
) : XMPushServiceJob(XMPushServiceJob.TYPE_SEND_MSG) {
    override fun getDesc(): String = "rebind the client. ${loginInfo.chid}"

    override fun process() {
        try {
            loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, 1, 16, null, null)
            pushAction.currentConnection?.let { connection ->
                connection.unbind(loginInfo.chid, loginInfo.userId)
                loginInfo.setStatus(PushClientsManager.ClientStatus.binding, 1, 16, null, null)
                connection.bind(loginInfo)
            }
        } catch (e: XMPPException) {
            MyLog.e(e)
            pushAction.disconnect(10, e)
        }
    }
}

class UnbindJob(
    private val pushAction: IPushServiceAction,
    val loginInfo: PushClientsManager.ClientLoginInfo,
    private val notifyType: Int,
    private val kickType: String?,
    private val reason: String?,
) : XMPushServiceJob(XMPushServiceJob.TYPE_BIND_UNBIND) {
    override fun getDesc(): String = "unbind the channel. ${loginInfo.chid}"

    override fun process() {
        if (loginInfo.status != PushClientsManager.ClientStatus.unbind && pushAction.currentConnection != null) {
            try {
                val blob = pushAction.runtimeObserver.constructUnbindBlob(loginInfo.chid, loginInfo.userId)
                if (blob != null) {
                    val bytes = blob.toByteArray(null).array()
                    pushAction.sendMessage(loginInfo.pkgName, bytes, true)
                }
            } catch (e: XMPPException) {
                MyLog.e(e)
                pushAction.disconnect(10, e)
            }
        }
        loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, notifyType, 0, reason, kickType)
    }
}
