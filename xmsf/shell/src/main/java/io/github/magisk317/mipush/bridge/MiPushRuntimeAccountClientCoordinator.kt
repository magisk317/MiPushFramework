package io.github.magisk317.mipush.bridge

import com.xiaomi.push.service.MIPushAccount
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushServiceCore
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker

internal class MiPushRuntimeAccountClientCoordinator(
    private val observerState: MiPushRuntimeObserverState,
) {
    fun attachAccountClient(client: Any) {
        val account = client as? MIPushAccount ?: return
        val service = observerState.service() ?: XMPushServiceLifecycleBridge.peekService() ?: return
        val created = attachAccount(account, service, PushClientsManager.getInstance())
        PushRuntimeChannelTracker.syncNow(
            if (created) {
                "MiPushRuntimeObserverBridge.attachAccountClient:created"
            } else {
                "MiPushRuntimeObserverBridge.attachAccountClient:existing"
            },
        )
    }

    fun attachAccount(
        account: MIPushAccount,
        service: XMPushServiceCore,
        manager: PushClientsManager,
    ): Boolean = synchronized(manager) {
        if (manager.getAllClientLoginInfoByChid(PushConstants.MIPUSH_CHANNEL).isNotEmpty()) {
            return@synchronized false
        }
        account.toClientLoginInfo(service, service).also { loginInfo ->
            MIPushHelper.prepareClientLoginInfo(service, loginInfo)
            manager.addActiveClient(loginInfo)
        }
        true
    }
}
