package io.github.magisk317.mipush.hook

import io.github.magisk317.mipush.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushService

interface OuterDependencies {
    fun configuration(): Configurations?
    fun serviceListener(pushService: XMPushService): XMPushServiceListener?
    fun hookedMethodHandler(): HookedMethodHandler?
}
