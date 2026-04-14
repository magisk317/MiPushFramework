package io.github.magisk317.mipush.framework.hook

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog

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
