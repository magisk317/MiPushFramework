package io.github.magisk317.mipush.platform.support

import java.lang.reflect.Method
import java.util.HashSet

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
        return findMethod(type, name, parameterTypes, HashSet()).apply {
            isAccessible = true
        }
    }

    private fun findMethod(
        type: Class<*>,
        name: String,
        parameterTypes: Array<out Class<*>>,
        visited: MutableSet<Class<*>>,
    ): Method {
        if (!visited.add(type)) {
            throw NoSuchMethodException("$name(${parameterTypes.joinToString()}) on $type")
        }
        type.declaredMethods.firstOrNull { method ->
            method.name == name && method.parameterTypes.size == parameterTypes.size &&
                method.parameterTypes.zip(parameterTypes).all { (actual, requested) ->
                    actual == requested || primitiveAliases[requested] == actual ||
                        primitiveAliases[actual] == requested
                }
        }?.let { return it }

        type.interfaces.forEach { interfaceType ->
            runCatching {
                return findMethod(interfaceType, name, parameterTypes, visited)
            }
        }
        type.superclass?.let { superclass ->
            return findMethod(superclass, name, parameterTypes, visited)
        }
        throw NoSuchMethodException("$name(${parameterTypes.joinToString()}) on ${type.name}")
    }

    fun newParceledListSlice(list: List<*>): Any =
        Class.forName("android.content.pm.ParceledListSlice")
            .getConstructor(List::class.java)
            .newInstance(list)
}
