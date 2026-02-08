package com.magisk317.push.hook

import com.elvishew.xlog.XLog
import com.magisk317.hook.Hooked

internal object HookTrace {
    private val logger = XLog.tag("HookTrace").build()

    @JvmStatic
    fun mark(point: String) {
        Hooked.mark(point)
        logger.d("hook=$point")
    }
}
