package com.nihility

import com.nihility.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushService

class EmptyOuterDependencies : OuterDependencies {
    override fun configuration(): Configurations? = null
    override fun serviceListener(pushService: XMPushService): XMPushServiceListener? = null
    override fun hookedMethodHandler(): HookedMethodHandler? = null
}
