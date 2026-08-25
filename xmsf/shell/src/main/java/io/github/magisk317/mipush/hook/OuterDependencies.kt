package io.github.magisk317.mipush.hook
import io.github.magisk317.mipush.push.hook.HookedMethodHandler

import io.github.magisk317.mipush.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushServiceCore

interface OuterDependencies {
    fun configuration(): Configurations?
    fun serviceListener(pushService: XMPushServiceCore): XMPushServiceListener?
    fun hookedMethodHandler(): HookedMethodHandler?
}
