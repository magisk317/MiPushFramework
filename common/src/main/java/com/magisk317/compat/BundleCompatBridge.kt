package com.magisk317.compat

import android.os.Bundle
import java.lang.reflect.InvocationTargetException

object BundleCompatBridge {
    private val getLegacy by lazy {
        Bundle::class.java.getMethod("get", String::class.java)
    }

    fun get(bundle: Bundle, key: String): Any? {
        return try {
            getLegacy.invoke(bundle, key)
        } catch (error: InvocationTargetException) {
            throw RuntimeException(error.targetException ?: error)
        }
    }
}
