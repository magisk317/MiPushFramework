package io.github.magisk317.mipush.push.hook

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.hook.Hooked

internal object HookTrace {

    @JvmStatic
    fun mark(point: String) {
        Hooked.mark(point)
        logD("hook=$point")
    }
}
