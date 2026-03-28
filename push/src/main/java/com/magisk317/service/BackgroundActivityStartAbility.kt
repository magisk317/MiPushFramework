package com.magisk317.service

import com.xiaomi.push.service.BackgroundActivityStartEnabler
import com.xiaomi.push.service.XMPushService

class BackgroundActivityStartAbility(
    private val pushService: XMPushService
) : XMPushServiceListener {
    override fun created() {
        BackgroundActivityStartEnabler.initialize(pushService)
    }
}
