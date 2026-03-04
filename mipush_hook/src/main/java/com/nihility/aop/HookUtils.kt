package com.nihility.aop

import java.lang.reflect.Field

/**
 * 现代化的 Hook 工具库
 *
 * 提供安全、便捷的反射操作 API，替代传统 AspectJ 织入。
 * 所有操作都是可空的，失败时返回 null 或不抛异常。
 *
 * ## 设计原则
 * - 异常友好：所有操作使用 runCatching 包装，避免链式反射失败
 * - 日志追踪：可选的调试日志，用于诊断 Hook 问题
 * - 类型安全：使用泛型支持类型推导
 */
object HookUtils {
    private var debugEnabled = false

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    /**
     * 修改指定类的静态字段值
     *
     * @param className 目标类的全限定名
     * @param fieldName 字段名称
     * @param value 新值
     * @return 是否成功修改
     *
     * 示例：
     * ```
     * hookStaticField("com.xiaomi.channel.commonutils.android.MIUIUtils", "isMIUI", true)
     * ```
     */
    fun hookStaticField(className: String, fieldName: String, value: Any?): Boolean {
        return runCatching {
            val clazz = Class.forName(className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true

            val finalValue = convertValue(field.type, value)
            field[null] = finalValue

            if (debugEnabled) {
                println("[HookUtils] ✓ Modified $className.$fieldName = $finalValue")
            }
            true
        }.onFailure { e ->
            if (debugEnabled) {
                println("[HookUtils] ✗ Failed to modify $className.$fieldName - ${e.message}")
            }
        }.getOrDefault(false)
    }

    /**
     * 修改指定对象的实例字段值
     *
     * @param obj 目标对象
     * @param fieldName 字段名称
     * @param value 新值
     * @return 是否成功
     */
    fun hookInstanceField(obj: Any, fieldName: String, value: Any?): Boolean {
        return runCatching {
            val field = obj::class.java.getDeclaredField(fieldName)
            field.isAccessible = true

            val finalValue = convertValue(field.type, value)
            field[obj] = finalValue

            if (debugEnabled) {
                println("[HookUtils] ✓ Modified ${obj::class.simpleName}.$fieldName = $finalValue")
            }
            true
        }.onFailure { e ->
            if (debugEnabled) {
                println("[HookUtils] ✗ Failed to modify ${obj::class.simpleName}.$fieldName - ${e.message}")
            }
        }.getOrDefault(false)
    }

    /**
     * 安全地获取静态字段值
     *
     * @param className 目标类的全限定名
     * @param fieldName 字段名称
     * @return 字段值，若获取失败返回 null
     */
    fun getStaticField(className: String, fieldName: String): Any? {
        return runCatching {
            val clazz = Class.forName(className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field[null]
        }.getOrNull()
    }

    /**
     * 安全地获取实例字段值
     *
     * @param obj 目标对象
     * @param fieldName 字段名称
     * @return 字段值，若获取失败返回 null
     */
    fun getInstanceField(obj: Any, fieldName: String): Any? {
        return runCatching {
            val field = obj::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field[obj]
        }.getOrNull()
    }

    /**
     * 检查类是否存在
     *
     * @param className 类的全限定名
     * @return 类是否存在
     */
    fun classExists(className: String): Boolean {
        return runCatching {
            Class.forName(className)
            true
        }.getOrDefault(false)
    }

    /**
     * 类型转换：根据目标类型转换值
     *
     * 支持基本类型的自动转换，如：
     * - `true` (Boolean) → `1` (Int)
     * - `"123"` (String) → `123` (Int)
     * - `1` (Int) → `1L` (Long)
     */
    private fun convertValue(targetType: Class<*>, value: Any?): Any? {
        if (value == null) return null
        if (targetType.isInstance(value)) return value

        return when {
            targetType == Int::class.javaPrimitiveType && value is Boolean -> if (value) 1 else 0
            targetType == Long::class.javaPrimitiveType && value is Number -> value.toLong()
            targetType == Float::class.javaPrimitiveType && value is Number -> value.toFloat()
            targetType == Double::class.javaPrimitiveType && value is Number -> value.toDouble()
            targetType == Boolean::class.javaPrimitiveType && value is Int -> value != 0
            else -> value
        }
    }

    /**
     * 在目标类存在时执行 Hook 操作
     * 若类不存在则安全地跳过，不抛出异常
     *
     * @param className 目标类名
     * @param action Hook 操作
     * @return 是否成功执行（类存在且操作成功）
     */
    fun ifPresent(className: String, action: () -> Boolean): Boolean {
        return if (classExists(className)) {
            action()
        } else {
            if (debugEnabled) {
                println("[HookUtils] ⊘ Class not found: $className")
            }
            false
        }
    }
}
