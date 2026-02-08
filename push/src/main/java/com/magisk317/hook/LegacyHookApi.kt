package com.magisk317.hook

typealias Configurations = com.nihility.Configurations
typealias Dependencies = com.nihility.Dependencies
typealias HookedMethodHandler = com.nihility.HookedMethodHandler
typealias OuterDependencies = com.nihility.OuterDependencies

object Hooked {
    @JvmStatic
    fun mark(id: String) = com.nihility.Hooked.mark(id)

    @JvmStatic
    fun contains(id: String): Boolean = com.nihility.Hooked.contains(id)
}
