package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.service.runtime.BackgroundActivityStartEnabler
import com.xiaomi.push.service.XMPushServiceCore

class BackgroundActivityStartAbility(
    private val pushService: XMPushServiceCore
) : XMPushServiceListener {
    override fun created() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            BackgroundActivityStartEnabler.initialize(pushService)
        }
    }
}
