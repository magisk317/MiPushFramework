package com.magisk317.hook

import com.magisk317.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushService

class EmptyOuterDependencies : OuterDependencies {
    override fun configuration(): Configurations? = null
    override fun serviceListener(pushService: XMPushService): XMPushServiceListener? = null
    override fun hookedMethodHandler(): HookedMethodHandler? = null
}
