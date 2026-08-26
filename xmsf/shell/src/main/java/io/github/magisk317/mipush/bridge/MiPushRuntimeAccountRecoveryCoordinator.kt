package io.github.magisk317.mipush.bridge

import android.content.Context
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.BindJob
import com.xiaomi.push.service.IPushRuntimeObserver
import com.xiaomi.push.service.MIPushAccountUtils
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.XMPushServiceJob
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.runtime.PushRuntime

internal class MiPushRuntimeAccountRecoveryCoordinator(
    private val appContext: Context,
    private val observerState: MiPushRuntimeObserverState,
    private val accountClientCoordinator: MiPushRuntimeAccountClientCoordinator,
    private val observer: IPushRuntimeObserver,
) {
    fun scheduleInvalidSignatureRefresh() {
        logW("SMACK: channel bind failed due to invalid-sig, scheduling account refresh and reconnect")
        val service = observerState.service() ?: return

        service.executeJob(
            object : XMPushServiceCore.Job(XMPushServiceJob.TYPE_PREPARE_MIPUSH_ACCOUNT) {
                override fun getDesc(): String = "refresh mi push account after invalid-sig"

                override fun process() {
                    try {
                        val newAccount = MIPushAccountUtils.register(
                            service,
                            service.packageName,
                            MIPushAccountUtils.MIPUSH_MIUI_APPID,
                            MIPushAccountUtils.MIPUSH_MIUI_APP_TOKEN,
                            observer,
                        )
                        if (newAccount != null) {
                            accountClientCoordinator.attachAccount(
                                newAccount,
                                service,
                                PushClientsManager.getInstance(),
                            )
                            val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(
                                PushConstants.MIPUSH_CHANNEL,
                                newAccount.account,
                            )
                            if (service.isConnected && client != null) {
                                service.executeJob(BindJob(service, client))
                            } else {
                                service.scheduleConnect(true)
                            }
                        } else {
                            logW("register returned null after invalid-sig, scheduling reconnect to retry")
                            PushRuntime.observeAccountEvent(
                                "refresh_failed_invalid_sig",
                                "MiPushRuntimeObserverBridge.resolveBindResult",
                            )
                            service.scheduleConnect(true)
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        logW("failed to register new account after invalid-sig", e)
                    }
                }
            },
        )
    }
}
