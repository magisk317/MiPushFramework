package com.xiaomi.network
import io.github.magisk317.mipush.protocol.model.*

fun interface HostFilter {
    fun accept(str: String): Boolean
}
