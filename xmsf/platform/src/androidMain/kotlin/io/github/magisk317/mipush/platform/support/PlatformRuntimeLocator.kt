package io.github.magisk317.mipush.platform.support

import kotlin.reflect.KClass

/**
 * Host-installed service resolver for platform-support code that moved out of
 * the shell but still needs a Koin-resolved collaborator (config value
 * conversion, the configurations singleton). The shell installs the resolver
 * during DI bootstrap; a missing resolver is a programming error.
 */
object PlatformRuntimeLocator {
    @Volatile
    var resolver: ((KClass<*>) -> Any?)? = null

    fun <T : Any> get(cls: KClass<T>): T {
        val value = resolver?.invoke(cls)
            ?: error("Platform runtime resolver not installed for ${cls.qualifiedName}")
        return cls.java.cast(value)!!
    }
}
