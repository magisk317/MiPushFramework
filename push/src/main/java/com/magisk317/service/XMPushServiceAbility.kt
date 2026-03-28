package com.magisk317.service

import com.xiaomi.push.service.XMPushService
import io.github.aakira.napier.Napier

class XMPushServiceAbility(pushService: XMPushService) : XMPushServiceListenerNotifier() {

    init {
        Napier.d("Initializing with service: $pushService", tag = "XMPushServiceAbility")
        XMPushServiceAbilityAssembler.prepare(pushService)
        XMPushServiceAbilityAssembler.createListeners(pushService).forEach(::addListener)
    }
}
