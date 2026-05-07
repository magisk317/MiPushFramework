package io.github.magisk317.mipush.push.hook

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.hook.Hooked

internal object HookTrace {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "HookTrace")
    }

    @JvmStatic
    fun mark(point: String) {
        Hooked.mark(point)
        logger.d("hook=$point")
    }
}
