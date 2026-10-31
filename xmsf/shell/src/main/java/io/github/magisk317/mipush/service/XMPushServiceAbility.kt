package io.github.magisk317.mipush.service

import com.xiaomi.push.service.XMPushServiceCore
import io.github.magisk317.xposed.logging.XLog

class XMPushServiceAbility(pushService: XMPushServiceCore) : XMPushServiceListenerNotifier() {

    init {
        XLog.tagged("XMPushServiceAbility").d { "Initializing with service: $pushService" }
        XMPushServiceAbilityAssembler.createListeners(pushService).forEach(::addListener)
    }
}
