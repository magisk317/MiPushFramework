package io.github.magisk317.mipush.service

import com.xiaomi.push.service.XMPushServiceCore
import io.github.aakira.napier.Napier

class XMPushServiceAbility(pushService: XMPushServiceCore) : XMPushServiceListenerNotifier() {

    init {
        Napier.d("Initializing with service: $pushService", tag = "XMPushServiceAbility")
        XMPushServiceAbilityAssembler.prepare(pushService)
        XMPushServiceAbilityAssembler.createListeners(pushService).forEach(::addListener)
    }
}
