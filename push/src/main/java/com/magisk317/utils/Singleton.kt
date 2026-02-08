package com.magisk317.utils

typealias AutoReset = com.nihility.utils.Singleton.AutoReset

object Singleton {
    inline fun <reified T : Any> instance(): T = com.nihility.utils.Singleton.instance()

    inline fun <reified T : Any> reset(value: T? = null): AutoReset =
        com.nihility.utils.Singleton.reset(value)
}
