package com.xiaomi.channel.commonutils.reflect

import android.util.Log
import com.xiaomi.channel.commonutils.android.SystemUtils
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object JavaCalls {
    private const val LOG_TAG = "JavaCalls"

    private val PRIMITIVE_MAP: MutableMap<Class<*>, Class<*>> = hashMapOf(
        Boolean::class.java to Boolean::class.javaPrimitiveType!!,
        Byte::class.java to Byte::class.javaPrimitiveType!!,
        Char::class.java to Char::class.javaPrimitiveType!!,
        Short::class.java to Short::class.javaPrimitiveType!!,
        Int::class.java to Int::class.javaPrimitiveType!!,
        Float::class.java to Float::class.javaPrimitiveType!!,
        Long::class.java to Long::class.javaPrimitiveType!!,
        Double::class.java to Double::class.javaPrimitiveType!!,
        Boolean::class.javaPrimitiveType!! to Boolean::class.javaPrimitiveType!!,
        Byte::class.javaPrimitiveType!! to Byte::class.javaPrimitiveType!!,
        Char::class.javaPrimitiveType!! to Char::class.javaPrimitiveType!!,
        Short::class.javaPrimitiveType!! to Short::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!! to Int::class.javaPrimitiveType!!,
        Float::class.javaPrimitiveType!! to Float::class.javaPrimitiveType!!,
        Long::class.javaPrimitiveType!! to Long::class.javaPrimitiveType!!,
        Double::class.javaPrimitiveType!! to Double::class.javaPrimitiveType!!
    )

    class JavaParam<T>(
        @JvmField val clazz: Class<out T>,
        @JvmField val obj: T
    )

    @JvmStatic
    fun callMethod(obj: Any?, name: String, vararg args: Any?): Any? {
        return try {
            if (obj != null) callMethodOrThrow(obj, name, *args) else null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when call Method '$name' in $obj, $e")
            null
        }
    }

    @JvmStatic
    @Throws(
        IllegalAccessException::class, NoSuchMethodException::class,
        SecurityException::class, IllegalArgumentException::class,
        InvocationTargetException::class
    )
    fun callMethodOrThrow(obj: Any?, name: String, vararg args: Any?): Any? {
        if (obj == null) return null
        val paramTypes = getParameterTypes(*args)
        val params = getParameters(*args)
        return getDeclaredMethod(obj.javaClass, name, *paramTypes)
            .invoke(obj, *params)
    }

    @JvmStatic
    fun callStaticMethod(className: String, name: String, vararg args: Any?): Any? {
        return try {
            callStaticMethodOrThrow(SystemUtils.loadClass(null, className), name, *args)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when call Method '$name' in $className, $e")
            null
        }
    }

    @JvmStatic
    @Throws(
        IllegalAccessException::class, NoSuchMethodException::class,
        SecurityException::class, IllegalArgumentException::class,
        InvocationTargetException::class
    )
    fun callStaticMethodOrThrow(cls: Class<*>, name: String, vararg args: Any?): Any {
        val paramTypes = getParameterTypes(*args)
        val params = getParameters(*args)
        return getDeclaredMethod(cls, name, *paramTypes)
            .invoke(null, *params) as Any
    }

    @JvmStatic
    @Throws(
        IllegalAccessException::class, NoSuchMethodException::class,
        SecurityException::class, ClassNotFoundException::class,
        IllegalArgumentException::class, InvocationTargetException::class
    )
    fun callStaticMethodOrThrow(className: String, name: String, vararg args: Any?): Any {
        val paramTypes = getParameterTypes(*args)
        val params = getParameters(*args)
        return getDeclaredMethod(
            SystemUtils.loadClass(null, className),
            name,
            *paramTypes
        ).invoke(null, *params) as Any
    }

    private fun compareClassLists(clsArr: Array<Class<*>>, clsArr2: Array<out Class<*>?>): Boolean {
        if (clsArr2.isEmpty()) {
            return clsArr.isEmpty()
        }
        if (clsArr.size != clsArr2.size) {
            return false
        }
        for (i in clsArr.indices) {
            val cls2 = clsArr2[i]
            if (cls2 != null && !clsArr[i].isAssignableFrom(cls2)) {
                val map = PRIMITIVE_MAP
                if (!map.containsKey(clsArr[i]) || map[clsArr[i]] != map[cls2]) {
                    return false
                }
            }
        }
        return true
    }

    private fun findMethodByName(methodArr: Array<Method>, name: String, paramTypes: Array<out Class<*>?>): Method? {
        for (method in methodArr) {
            if (method.name == name && compareClassLists(method.parameterTypes, paramTypes)) {
                return method
            }
        }
        return null
    }

    @Throws(NoSuchMethodException::class, SecurityException::class)
    private fun getDeclaredMethod(cls: Class<*>, name: String, vararg paramTypes: Class<*>?): Method {
        val method = findMethodByName(cls.declaredMethods, name, paramTypes)
        if (method != null) {
            method.isAccessible = true
            return method
        }
        val superCls = cls.superclass
        if (superCls != null) {
            return getDeclaredMethod(superCls, name, *paramTypes)
        }
        throw NoSuchMethodException()
    }

    private fun getDefaultValue(cls: Class<*>): Any? {
        return when {
            Int::class.java.isAssignableFrom(cls) || Int::class.javaPrimitiveType == cls ||
            Byte::class.java.isAssignableFrom(cls) || Byte::class.javaPrimitiveType == cls ||
            Short::class.java.isAssignableFrom(cls) || Short::class.javaPrimitiveType == cls ||
            Long::class.java.isAssignableFrom(cls) || Long::class.javaPrimitiveType == cls ||
            Double::class.java.isAssignableFrom(cls) || Double::class.javaPrimitiveType == cls ||
            Float::class.java.isAssignableFrom(cls) || Float::class.javaPrimitiveType == cls -> 0
            Boolean::class.java.isAssignableFrom(cls) || Boolean::class.javaPrimitiveType == cls -> false
            Char::class.java.isAssignableFrom(cls) || Char::class.javaPrimitiveType == cls -> 0.toChar()
            else -> null
        }
    }

    @JvmStatic
    fun getField(obj: Any, name: String): Any? {
        return try {
            getFieldOrThrow(obj.javaClass, obj, name)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when call getField '$name' in $obj, $e")
            null
        }
    }

    @Throws(IllegalAccessException::class, NoSuchFieldException::class)
    fun getFieldOrThrow(cls: Class<out Any>, obj: Any?, name: String): Any {
        var superclass: Class<*>? = cls
        var field: Field? = null
        while (field == null) {
            try {
                val declaredField = superclass!!.getDeclaredField(name)
                declaredField.isAccessible = true
                field = declaredField
            } catch (e: NoSuchFieldException) {
                superclass = superclass?.superclass
            }
            if (superclass == null) {
                throw NoSuchFieldException()
            }
        }
        field.isAccessible = true
        return field.get(obj) as Any
    }

    private fun getParameterTypes(vararg args: Any?): Array<Class<*>?> {
        if (args.isEmpty()) return emptyArray()
        return args.map { obj ->
            if (obj is JavaParam<*>) {
                obj.clazz
            } else {
                obj?.javaClass
            }
        }.toTypedArray()
    }

    private fun getParameters(vararg args: Any?): Array<Any?> {
        if (args.isEmpty()) return emptyArray()
        return args.map { obj ->
            if (obj is JavaParam<*>) {
                obj.obj
            } else {
                obj
            }
        }.toTypedArray()
    }

    @JvmStatic
    fun getStaticField(cls: Class<out Any>?, name: String): Any? {
        return try {
            getFieldOrThrow(cls!!, null, name)
        } catch (e: Exception) {
            Log.w(
                LOG_TAG,
                "Meet exception when call getStaticField '$name' in ${cls?.simpleName}, $e"
            )
            null
        }
    }

    @JvmStatic
    fun getStaticField(className: String, name: String): Any? {
        return try {
            getFieldOrThrow(SystemUtils.loadClass(null, className), null, name)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when call getStaticField '$name' in $className, $e")
            null
        }
    }

    @JvmStatic
    fun newEmptyInstance(cls: Class<*>): Any? {
        return try {
            newEmptyInstanceOrThrow(cls)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when make instance as a ${cls.simpleName}, $e")
            null
        }
    }

    @Throws(
        IllegalAccessException::class, InstantiationException::class,
        ClassNotFoundException::class, InvocationTargetException::class
    )
    fun newEmptyInstanceOrThrow(cls: Class<*>): Any {
        val declaredConstructors = cls.declaredConstructors
        if (declaredConstructors.isEmpty()) {
            throw IllegalArgumentException("Can't get even one available constructor for $cls")
        }
        val constructor: Constructor<*> = declaredConstructors[0]
        constructor.isAccessible = true
        val parameterTypes = constructor.parameterTypes
        if (parameterTypes.isEmpty()) {
            return constructor.newInstance()
        }
        val objArr = arrayOfNulls<Any>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            objArr[i] = getDefaultValue(parameterTypes[i])
        }
        return constructor.newInstance(*objArr)
    }

    @JvmStatic
    fun newInstance(cls: Class<*>, vararg args: Any?): Any? {
        return try {
            newInstanceOrThrow(cls, *args)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when make instance as a ${cls.simpleName}, $e")
            null
        }
    }

    @JvmStatic
    fun newInstance(className: String, vararg args: Any?): Any? {
        return try {
            newInstanceOrThrow(className, *args)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when make instance as a $className, $e")
            null
        }
    }

    @Throws(
        IllegalAccessException::class, NoSuchMethodException::class,
        InstantiationException::class, SecurityException::class,
        IllegalArgumentException::class, InvocationTargetException::class
    )
    fun newInstanceOrThrow(cls: Class<*>, vararg args: Any?): Any {
        val paramTypes = getParameterTypes(*args)
        val params = getParameters(*args)
        return cls.getConstructor(*paramTypes).newInstance(*params)
    }

    @Throws(
        IllegalAccessException::class, NoSuchMethodException::class,
        InstantiationException::class, SecurityException::class,
        ClassNotFoundException::class, IllegalArgumentException::class,
        InvocationTargetException::class
    )
    fun newInstanceOrThrow(className: String, vararg args: Any?): Any {
        val params = getParameters(*args)
        return newInstanceOrThrow(
            SystemUtils.loadClass(null, className),
            *params
        )
    }

    @JvmStatic
    fun setField(obj: Any, name: String, value: Any?) {
        try {
            setFieldOrThrow(obj, name, value)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Meet exception when call setField '$name' in $obj, $e")
        }
    }

    @Throws(IllegalAccessException::class, NoSuchFieldException::class)
    fun setFieldOrThrow(obj: Any, name: String, value: Any?) {
        var superclass: Class<*>? = obj.javaClass
        var declaredField: Field? = null
        while (declaredField == null) {
            try {
                declaredField = superclass!!.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                superclass = superclass?.superclass
            }
            if (superclass == null) {
                throw NoSuchFieldException()
            }
        }
        declaredField.isAccessible = true
        declaredField.set(obj, value)
    }
}
