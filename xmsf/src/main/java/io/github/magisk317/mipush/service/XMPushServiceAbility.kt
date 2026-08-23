package io.github.magisk317.mipush.service

import com.xiaomi.push.service.XMPushServiceCore
import co.touchlab.kermit.Logger

class XMPushServiceAbility(pushService: XMPushServiceCore) : XMPushServiceListenerNotifier() {

    init {
        Logger.withTag("XMPushServiceAbility").d { "Initializing with service: $pushService" }
        XMPushServiceAbilityAssembler.createListeners(pushService).forEach(::addListener)
    }
}
