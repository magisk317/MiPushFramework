package io.github.magisk317.mipush.push.hook

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder

object HookTrace {

    @Volatile
    var enabled: Boolean = false

    @JvmStatic
    fun mark(point: String) {
        PushShellBridgeHolder.require().markHook(point)
        if (enabled) {
            logD("hook=$point")
        }
    }
}
