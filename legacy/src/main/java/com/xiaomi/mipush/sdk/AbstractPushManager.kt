package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

interface AbstractPushManager {
    fun register()

    fun unregister()
}
