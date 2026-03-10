package com.magisk317.compat

import android.os.Parcel
import java.lang.reflect.InvocationTargetException

object ParcelCompatBridge {
    private val readHashMapLegacy by lazy {
        Parcel::class.java.getMethod("readHashMap", ClassLoader::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun readHashMap(parcel: Parcel, classLoader: ClassLoader?): HashMap<Any?, Any?>? {
        return try {
            readHashMapLegacy.invoke(parcel, classLoader) as HashMap<Any?, Any?>?
        } catch (error: InvocationTargetException) {
            throw RuntimeException(error.targetException ?: error)
        }
    }
}
