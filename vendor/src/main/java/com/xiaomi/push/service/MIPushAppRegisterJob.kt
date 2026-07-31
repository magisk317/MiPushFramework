package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException

class MIPushAppRegisterJob(
    private val pushService: XMPushServiceCore,
    private val packageName: String,
    private val appId: String,
    private val appToken: String,
    private val payload: ByteArray,
) : XMPushServiceCore.Job(XMPushServiceJob.TYPE_BIND_UNBIND) {
    override fun getDesc(): String = "register app"

    override fun process() {
        var account = pushService.runtimeObserver.loadAccount(pushService, "MIPushAppRegisterJob.process")
        if (account == null) {
            account = pushService.runtimeObserver.registerAccount(
                pushService,
                packageName,
                appId,
                appToken,
                "MIPushAppRegisterJob.process",
            )
        }
        if (account == null) {
            MyLog.e("no account for mipush")
            pushService.runtimeObserver.onAccountEvent(packageName, "account_missing")
            pushService.runtimeObserver.onRegistrationResult(packageName, false, "MIPushAppRegisterJob.process", "no_account")
            MIPushClientManager.notifyRegisterError(pushService, 70000002, "no account")
            return
        }
        pushService.runtimeObserver.onAccountEvent(packageName, "account_ready")
        MyLog.w("[MIPushAppRegisterJob] start prepare client info for $packageName")
        val activeClients = PushClientsManager.getInstance().getAllClientLoginInfoByChid("5")
        val client = if (activeClients.isEmpty()) {
            MyLog.w("[MIPushAppRegisterJob] create new client info for $packageName")
            account.toClientLoginInfo(pushService, pushService).also { loginInfo ->
                MIPushHelper.prepareClientLoginInfo(pushService, loginInfo)
                PushClientsManager.getInstance().addActiveClient(loginInfo)
                pushService.runtimeObserver.syncChannelTracker("MIPushAppRegisterJob.process:add_client")
            }
        } else {
            MyLog.w("[MIPushAppRegisterJob] reuse existing client info for $packageName")
            activeClients.iterator().next()
        }
        MyLog.w("[MIPushAppRegisterJob] client info ready, status=${client.status}")
        val connected = pushService.isConnected
        if (shouldCacheRegistrationPayload(connected, client.status)) {
            cacheRegistrationPayload()
        }
        if (!connected) {
            pushService.runtimeObserver.onChannelStateChanged(
                packageName,
                client.chid,
                client.userId,
                client.session,
                PushChannelState.Binding,
                "MIPushAppRegisterJob.process",
                null,
                "schedule_connect_for_register",
            )
            pushService.scheduleConnect(true)
            return
        }
        try {
            when (client.status) {
                PushClientsManager.ClientStatus.binded -> {
                    pushService.runtimeObserver.onRegistrationStateChanged(
                        packageName,
                        PushRegistrationState.Registering,
                        "MIPushAppRegisterJob.process",
                        "registration_payload_sent",
                    )
                    MIPushHelper.sendPacket(pushService, packageName, payload)
                }

                PushClientsManager.ClientStatus.unbind -> {
                    pushService.runtimeObserver.onChannelStateChanged(
                        packageName,
                        client.chid,
                        client.userId,
                        client.session,
                        PushChannelState.Binding,
                        "MIPushAppRegisterJob.process",
                        null,
                        "bind_for_register",
                    )
                    // The cached payload is flushed once the channel reaches binded; see
                    // PushClientsManager.ClientLoginInfo.setStatus.
                    pushService.executeJob(BindJob(pushService, client))
                }

                else -> Unit
            }
        } catch (e: XMPPException) {
            MyLog.e("meet error, disconnect connection. $e")
            pushService.runtimeObserver.onRegistrationResult(packageName, false, "MIPushAppRegisterJob.process", "send_packet_failed")
            pushService.disconnect(10, e)
        }
    }

    private fun cacheRegistrationPayload() {
        // Stock XMSF 7.4.67-C f0 caches through h0.d only while disconnected or chid 5 is
        // unbound. The older shared SDK 3.7.9 cached every request before this job, which left a
        // directly sent payload queued for the next bind; this runtime follows the newer XMSF path.
        pushService.runtimeObserver.cacheRegistrationRequest(packageName, payload)
    }

    companion object {
        internal fun shouldCacheRegistrationPayload(
            connected: Boolean,
            clientStatus: PushClientsManager.ClientStatus,
        ): Boolean = !connected || clientStatus == PushClientsManager.ClientStatus.unbind
    }
}

/**
 * Flushes registration payloads cached by [MIPushAppRegisterJob] once chid 5 is bound.
 *
 * Queued from [PushClientsManager.ClientLoginInfo.setStatus] right after the status field flips to
 * [PushClientsManager.ClientStatus.binded], so it covers every bind source. The binded guard below
 * is kept as a cheap sanity check: the channel can be torn down again between the enqueue and this
 * job running, in which case the payload stays queued for the next binded transition.
 */
class FlushPendingRegistrationJob(
    private val pushAction: IPushServiceAction,
    private val packageName: String,
) : XMPushServiceCore.Job(XMPushServiceJob.TYPE_BIND_UNBIND) {
    override fun getDesc(): String = "flush pending registration for $packageName"

    override fun process() {
        if (!pushAction.isConnected) {
            MyLog.w("[FlushPendingRegistrationJob] skip flush, not connected. pkg=$packageName")
            return
        }
        val bound = PushClientsManager.getInstance()
            .getAllClientLoginInfoByChid(PushConstants.MIPUSH_CHANNEL)
            .any { it.status == PushClientsManager.ClientStatus.binded }
        if (!bound) {
            MyLog.w("[FlushPendingRegistrationJob] skip flush, chid 5 not binded. pkg=$packageName")
            return
        }
        MyLog.w("[FlushPendingRegistrationJob] flushing pending registration for $packageName")
        MIPushClientManager.processPendingRegistrationRequest(pushAction, pushAction.context)
    }
}
