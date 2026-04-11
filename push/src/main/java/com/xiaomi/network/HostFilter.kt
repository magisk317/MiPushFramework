package com.xiaomi.network

fun interface HostFilter {
    fun accept(str: String): Boolean
}
