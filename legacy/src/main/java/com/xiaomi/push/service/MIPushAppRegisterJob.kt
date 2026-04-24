package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException

class MIPushAppRegisterJob(
    private val pushService: XMPushService,
    private val packageName: String,
    private val appId: String,
    private val appToken: String,
    private val payload: ByteArray,
) : XMPushService.Job(XMPushServiceJob.TYPE_BIND_UNBIND) {
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
        if (!pushService.isConnected) {
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
}
