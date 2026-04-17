package io.github.magisk317.mipush.common.utils

import java.util.concurrent.ConcurrentHashMap

object Singleton {
    @PublishedApi
    internal val instances = ConcurrentHashMap<Class<*>, Any>()

    @PublishedApi
    internal val userInstances = ConcurrentHashMap<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> instance(): T {
        val klass = T::class.java

        val userObj = userInstances[klass] as? T
        if (userObj != null) {
            return userObj
        }

        // Double-check: read from instances before falling through to synchronized create.
        val obj = instances[klass] as? T
        if (obj != null) {
            return obj
        }

        return create(klass)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> instance(vararg reified: T): T {
        if (reified.isNotEmpty()) {
            throw IllegalArgumentException(
                "Please don't pass any values here. Java will detect class automagically."
            )
        }
        val klass = reified.javaClass.componentType as Class<T>
        return get(klass)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(klass: Class<T>): T {
        val userObj = userInstances[klass] as? T
        if (userObj != null) {
            return userObj
        }

        val obj = instances[klass] as? T
        if (obj != null) {
            return obj
        }

        return create(klass)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : Any> create(klass: Class<T>): T {
        // Fast path outside lock — ConcurrentHashMap read is safe.
        var obj = instances[klass] as? T
        if (obj != null) {
            return obj
        }
        // Slow path: synchronize on the class to avoid duplicate instantiation per type
        // while not blocking unrelated types.
        synchronized(klass) {
            obj = instances[klass] as? T
            if (obj != null) {
                return obj
            }
            return try {
                val constructor = klass.getDeclaredConstructor()
                constructor.isAccessible = true
                val newObj = constructor.newInstance()
                instances[klass] = newObj as Any
                newObj
            } catch (e: Throwable) {
                throw RuntimeException("Singleton.create failed for ${klass.name}", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> reset(value: T? = null): AutoReset {
        val klass = T::class.java
        if (value == null) {
            userInstances.remove(klass)
        } else {
            userInstances[klass] = value
        }
        return AutoReset(klass)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> reset(vararg reified: T): AutoReset {
        val klass = reified.javaClass.componentType as Class<T>
        if (reified.isEmpty()) {
            userInstances.remove(klass)
        } else {
            userInstances[klass] = reified[0] as Any
        }
        return AutoReset(klass)
    }

    @JvmStatic
    fun <T : Any> reset(klass: Class<T>, value: T?): AutoReset {
        if (value == null) {
            userInstances.remove(klass)
        } else {
            userInstances[klass] = value
        }
        return AutoReset(klass)
    }

    class AutoReset(private val klass: Class<*>) : AutoCloseable {
        override fun close() {
            userInstances.remove(klass)
        }
    }
}
