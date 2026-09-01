package io.github.magisk317.mipush.platform.support

import java.lang.reflect.Method

/** Reflection helpers for the XMSF-owned hidden notification backend. */
object NotificationManagerReflection {
    private val primitiveAliases = mapOf(
        Boolean::class.java to Boolean::class.javaPrimitiveType,
        Byte::class.java to Byte::class.javaPrimitiveType,
        Char::class.java to Char::class.javaPrimitiveType,
        Short::class.java to Short::class.javaPrimitiveType,
        Int::class.java to Int::class.javaPrimitiveType,
        Float::class.java to Float::class.javaPrimitiveType,
        Long::class.java to Long::class.javaPrimitiveType,
        Double::class.java to Double::class.javaPrimitiveType,
    )

    fun findMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterTypes.size == parameterTypes.size &&
                    method.parameterTypes.zip(parameterTypes).all { (actual, requested) ->
                        actual == requested || primitiveAliases[requested] == actual ||
                            primitiveAliases[actual] == requested
                    }
            }?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchMethodException("$name(${parameterTypes.joinToString()}) on $type")
    }

    fun newParceledListSlice(list: List<*>): Any =
        Class.forName("android.content.pm.ParceledListSlice")
            .getConstructor(List::class.java)
            .newInstance(list)
}
