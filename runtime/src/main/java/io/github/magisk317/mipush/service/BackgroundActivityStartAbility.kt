package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.service.runtime.BackgroundActivityStartEnabler
import com.xiaomi.push.service.XMPushService

class BackgroundActivityStartAbility(
    private val pushService: XMPushService
) : XMPushServiceListener {
    override fun created() {
        BackgroundActivityStartEnabler.initialize(pushService)
    }
}
