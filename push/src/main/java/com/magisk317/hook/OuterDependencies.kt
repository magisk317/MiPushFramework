package com.magisk317.hook

import com.magisk317.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushService

interface OuterDependencies {
    fun configuration(): Configurations?
    fun serviceListener(pushService: XMPushService): XMPushServiceListener?
    fun hookedMethodHandler(): HookedMethodHandler?
}
