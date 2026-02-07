package com.nihility

import com.nihility.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushService

interface OuterDependencies {
    fun configuration(): Configurations?
    fun serviceListener(pushService: XMPushService): XMPushServiceListener?
    fun hookedMethodHandler(): HookedMethodHandler?
}
