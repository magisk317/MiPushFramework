package io.github.magisk317.mipush.xposed

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Invoker
import io.github.libxposed.api.XposedModule
import java.lang.IllegalStateException
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

object XposedRuntime {
    @Volatile
    private var module: XposedModule? = null

    fun install(module: XposedModule) {
        this.module = module
    }

    internal fun hook(executable: Executable, methodHook: MethodHook): HookHandle {
        if (executable is Method && (Modifier.isAbstract(executable.modifiers) || Modifier.isNative(executable.modifiers))) {
            return HookHandle(null)
        }
        val activeModule = module ?: throw IllegalStateException("libxposed runtime is not installed")
        executable.isAccessible = true
        val handleRef = AtomicReference<XposedInterface.HookHandle?>()
        val handle = activeModule.hook(executable).intercept { chain ->
            methodHook.intercept(chain, handleRef)
        }
        handleRef.set(handle)
        return HookHandle(handle)
    }

    internal fun invokeOriginal(member: Member, thisObject: Any?, args: Array<out Any?>): Any? {
        val activeModule = module ?: return invokeMemberRaw(member, thisObject, args)
        return when (member) {
            is Method -> activeModule.getInvoker(member)
                .setType(Invoker.Type.ORIGIN)
                .invoke(thisObject, *args)
            is Constructor<*> -> activeModule.getInvoker(member)
                .setType(Invoker.Type.ORIGIN)
                .newInstance(*args)
            else -> throw IllegalArgumentException("Unsupported member: $member")
        }
    }

    fun log(priority: Int, tag: String?, message: String, throwable: Throwable? = null) {
        val activeModule = module
        if (activeModule == null) {
            if (throwable == null) {
                Log.println(priority, tag ?: "MiPush", message)
            } else {
                Log.println(priority, tag ?: "MiPush", "$message\n${Log.getStackTraceString(throwable)}")
            }
            return
        }
        if (throwable == null) {
            activeModule.log(priority, tag, message)
        } else {
            activeModule.log(priority, tag, message, throwable)
        }
    }
}

class HookHandle internal constructor(
    private val handle: XposedInterface.HookHandle?,
) {
    fun unhook() {
        handle?.unhook()
    }
}

open class MethodHookParam(
    open val method: Member,
    open var thisObject: Any?,
    open var args: Array<Any?>,
    private val chain: XposedInterface.Chain?,
    private val handleProvider: () -> XposedInterface.HookHandle?,
) {
    var returnEarly: Boolean = false

    open var result: Any? = null
        set(value) {
            field = value
            throwable = null
            returnEarly = true
        }

    open var throwable: Throwable? = null
        set(value) {
            field = value
            returnEarly = true
        }

    fun hasThrowable(): Boolean = throwable != null

    fun invokeOriginal(): Any? {
        return XposedRuntime.invokeOriginal(method, thisObject, args)
    }

    fun unhook() {
        handleProvider()?.unhook()
    }
}

typealias HookAction = MethodHookParam.() -> Unit
typealias ReplaceAction = MethodHookParam.() -> Any?
typealias HookCallback = HookContext.() -> Unit

class MethodHook(callback: HookCallback) {
    private val context = HookContext(this).apply(callback)

    fun intercept(
        chain: XposedInterface.Chain,
        handleRef: AtomicReference<XposedInterface.HookHandle?>,
    ): Any? {
        val param = MethodHookParam(
            method = chain.executable,
            thisObject = chain.thisObject,
            args = chain.args.toTypedArray(),
            chain = chain,
            handleProvider = { handleRef.get() },
        )

        val replaceAction = context.replaceAction
        if (replaceAction != null && context.needHook?.invoke() != false) {
            runCatching {
                param.result = replaceAction.invoke(param)
            }.onFailure {
                param.throwable = it
            }
            context.afterAction?.invoke(param)
            param.throwable?.let { throw it }
            return param.result
        }

        context.beforeAction?.invoke(param)
        if (!param.returnEarly) {
            try {
                param.result = chain.proceed(param.args)
                param.returnEarly = false
            } catch (t: Throwable) {
                param.throwable = t
            }
        }

        context.afterAction?.invoke(param)
        param.throwable?.let { throw it }
        return param.result
    }
}

class HookContext(@Suppress("unused") private val methodHook: MethodHook) {
    internal var beforeAction: HookAction? = null
        private set

    internal var afterAction: HookAction? = null
        private set

    internal var replaceAction: ReplaceAction? = null
        private set

    internal var needHook: (() -> Boolean)? = null
        private set

    fun doBefore(action: HookAction) {
        beforeAction = action
    }

    fun doAfter(action: HookAction) {
        afterAction = action
    }

    fun replace(action: ReplaceAction) {
        replaceAction = action
    }

    fun replace(hookCheck: () -> Boolean, action: ReplaceAction) {
        needHook = hookCheck
        replaceAction = action
    }

}

fun Method.hook(callback: HookCallback): HookHandle =
    XposedRuntime.hook(this, MethodHook(callback))

fun Constructor<*>.hook(callback: HookCallback): HookHandle =
    XposedRuntime.hook(this, MethodHook(callback))

fun Class<*>.hookMethod(methodName: String, vararg parameterTypes: Class<*>, callback: HookCallback): HookHandle =
    findMethodExact(this, methodName, *parameterTypes).hook(callback)

fun Class<*>.hookConstructor(vararg parameterTypes: Class<*>, callback: HookCallback): HookHandle =
    findConstructorExact(this, *parameterTypes).hook(callback)

fun Class<*>.hookAllConstructor(callback: HookCallback): Set<HookHandle> =
    declaredConstructors.map { it.hook(callback) }.toSet()

fun Class<*>.hookAllMethods(methodName: String, callback: HookCallback): Set<HookHandle> =
    collectMethods(this, methodName).map { it.hook(callback) }.toSet()

fun hookMethod(
    className: String,
    classLoader: ClassLoader,
    methodName: String,
    vararg parameterTypes: Class<*>,
    callback: HookCallback,
): HookHandle = classLoader.findClass(className).hookMethod(methodName, *parameterTypes, callback = callback)

fun hookConstructor(
    className: String,
    classLoader: ClassLoader,
    @Suppress("unused") methodName: String,
    vararg parameterTypes: Class<*>,
    callback: HookCallback,
): HookHandle = classLoader.findClass(className).hookConstructor(*parameterTypes, callback = callback)

fun Any.callMethod(methodName: String, vararg args: Any?): Any? =
    XposedHelpers.callMethod(this, methodName, *args)

fun Any.callMethod(methodName: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? =
    findMethodExact(this.javaClass, methodName, *parameterTypes).invokeAccessible(this, args)

fun Class<*>.callStaticMethod(methodName: String, vararg args: Any?): Any? =
    XposedHelpers.callStaticMethod(this, methodName, *args)

fun Class<*>.callStaticMethod(
    methodName: String,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?,
): Any? = findMethodExact(this, methodName, *parameterTypes).invokeAccessible(null, args)

fun Class<*>.newInstance(vararg args: Any?): Any =
    findConstructorBestMatch(this, args).newInstanceAccessible(args)

fun Class<*>.newInstance(parameterTypes: Array<Class<*>>, vararg args: Any?): Any =
    findConstructorExact(this, *parameterTypes).newInstanceAccessible(args)

fun ClassLoader.findClass(className: String): Class<*> = findClass(className, this)

fun findClass(className: String, classLoader: ClassLoader?): Class<*> =
    XposedHelpers.findClass(className, classLoader)

fun findMethodExact(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method {
    val target = clazz ?: throw NoSuchMethodException("Class is null for method $methodName")
    val resolved = resolveParameterTypes(target.classLoader, parameterTypes)
    var current: Class<*>? = target
    while (current != null) {
        try {
            return current.getDeclaredMethod(methodName, *resolved).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            current = current.superclass
        }
    }
    throw NoSuchMethodException("Method not found: ${target.name}#$methodName")
}

fun findConstructorExact(clazz: Class<*>?, vararg parameterTypes: Any?): Constructor<*> {
    val target = clazz ?: throw NoSuchMethodException("Class is null for constructor")
    val resolved = resolveParameterTypes(target.classLoader, parameterTypes)
    return target.getDeclaredConstructor(*resolved).apply { isAccessible = true }
}

fun findConstructorExact(className: String, classLoader: ClassLoader?, vararg parameterTypes: Any?): Constructor<*> =
    findConstructorExact(findClass(className, classLoader), *parameterTypes)

fun currentApplication(): Application? = runCatching {
    val activityThread = Class.forName("android.app.ActivityThread")
    activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
        .invoke(null) as? Application
}.getOrNull()

fun invokeOriginalMethod(method: Member, thisObject: Any?, args: Array<Any?>): Any? =
    XposedRuntime.invokeOriginal(method, thisObject, args)

fun MethodHookParam.setMiPushExtra(key: String, value: Any?) {
    extrasFor(this)[key] = value
}

fun MethodHookParam.getMiPushExtra(key: String): Any? = extrasFor(this)[key]

inline fun <reified T> Any.getOrNull(name: String): T? = getField(name, T::class.java)

inline operator fun <reified T> Any.get(name: String): T = getField(name, T::class.java)!!

inline operator fun <reified T> Any.set(name: String, value: T?) = setField(name, value, T::class.java)

fun <T> Any.getField(name: String, fieldClazz: Class<T>): T? {
    val obj = if (this is Class<*>) null else this
    val thisClass = if (this is Class<*>) this else this.javaClass
    val field = findField(thisClass, name)
    val value = getFieldValue(field, obj, fieldClazz)
    @Suppress("UNCHECKED_CAST")
    return value as? T?
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.setField(name: String, value: T?, fieldClass: Class<T>) {
    val obj = if (this is Class<*>) null else this
    val thisClass = if (this is Class<*>) this else this.javaClass
    val field = findField(thisClass, name)
    setFieldValue(field, obj, value, fieldClass)
}

object XposedHelpers {
    class ClassNotFoundError(cause: Throwable) : Error(cause)
    class InvocationTargetError(val targetException: Throwable) : Error(targetException)

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> = try {
        if (classLoader == null) {
            Class.forName(className)
        } else {
            Class.forName(className, false, classLoader)
        }
    } catch (t: ClassNotFoundException) {
        throw ClassNotFoundError(t)
    } catch (t: NoClassDefFoundError) {
        throw ClassNotFoundError(t)
    }

    fun findMethodExact(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method =
        io.github.magisk317.mipush.xposed.findMethodExact(clazz, methodName, *parameterTypes)

    fun findConstructorExact(clazz: Class<*>?, vararg parameterTypes: Any?): Constructor<*> =
        io.github.magisk317.mipush.xposed.findConstructorExact(clazz, *parameterTypes)

    fun findConstructorExact(className: String, classLoader: ClassLoader?, vararg parameterTypes: Any?): Constructor<*> =
        io.github.magisk317.mipush.xposed.findConstructorExact(className, classLoader, *parameterTypes)

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        val target = obj ?: throw NullPointerException("callMethod target is null for $methodName")
        return findMethodBestMatch(target.javaClass, methodName, args).invokeAccessible(target, args)
    }

    fun callStaticMethod(clazz: Class<*>?, methodName: String, vararg args: Any?): Any? {
        val target = clazz ?: throw NullPointerException("callStaticMethod class is null for $methodName")
        return findMethodBestMatch(target, methodName, args).invokeAccessible(null, args)
    }

    fun newInstance(clazz: Class<*>?, vararg args: Any?): Any {
        val target = clazz ?: throw NullPointerException("newInstance class is null")
        return findConstructorBestMatch(target, args).newInstanceAccessible(args)
    }

    fun findField(clazz: Class<*>?, fieldName: String): Field =
        io.github.magisk317.mipush.xposed.findField(clazz ?: throw NoSuchFieldException(fieldName), fieldName)

    fun getObjectField(obj: Any?, fieldName: String): Any? {
        val target = obj ?: throw NullPointerException("getObjectField target is null for $fieldName")
        return findField(target.javaClass, fieldName).get(target)
    }

    fun getIntField(obj: Any?, fieldName: String): Int {
        val target = obj ?: throw NullPointerException("getIntField target is null for $fieldName")
        return findField(target.javaClass, fieldName).getInt(target)
    }

    fun setIntField(obj: Any?, fieldName: String, value: Int) {
        val target = obj ?: throw NullPointerException("setIntField target is null for $fieldName")
        findField(target.javaClass, fieldName).setInt(target, value)
    }

    fun setObjectField(obj: Any?, fieldName: String, value: Any?) {
        val target = obj ?: throw NullPointerException("setObjectField target is null for $fieldName")
        findField(target.javaClass, fieldName).set(target, value)
    }

    fun setStaticBooleanField(clazz: Class<*>?, fieldName: String, value: Boolean) {
        val target = clazz ?: throw NullPointerException("setStaticBooleanField class is null for $fieldName")
        findField(target, fieldName).setBoolean(null, value)
    }

    fun invokeOriginalMethod(method: Member, thisObject: Any?, args: Array<Any?>): Any? =
        io.github.magisk317.mipush.xposed.invokeOriginalMethod(method, thisObject, args)
}

private val extraFields: MutableMap<MethodHookParam, MutableMap<String, Any?>> =
    Collections.synchronizedMap(WeakHashMap())

private fun extrasFor(param: MethodHookParam): MutableMap<String, Any?> =
    synchronized(extraFields) { extraFields.getOrPut(param) { HashMap() } }

private fun invokeMember(member: Member, thisObject: Any?, args: Array<out Any?>): Any? {
    return when (member) {
        is Method -> member.invokeAccessible(thisObject, args)
        is Constructor<*> -> member.newInstanceAccessible(args)
        else -> throw IllegalArgumentException("Unsupported member: $member")
    }
}

private fun invokeMemberRaw(member: Member, thisObject: Any?, args: Array<out Any?>): Any? {
    return when (member) {
        is Method -> {
            member.isAccessible = true
            member.invoke(thisObject, *args)
        }
        is Constructor<*> -> {
            member.isAccessible = true
            member.newInstance(*args)
        }
        else -> throw IllegalArgumentException("Unsupported member: $member")
    }
}

private fun Method.invokeAccessible(thisObject: Any?, args: Array<out Any?>): Any? = try {
    isAccessible = true
    invoke(thisObject, *args)
} catch (e: InvocationTargetException) {
    throw XposedHelpers.InvocationTargetError(e.targetException ?: e.cause ?: e)
}

private fun Constructor<*>.newInstanceAccessible(args: Array<out Any?>): Any = try {
    isAccessible = true
    newInstance(*args)
} catch (e: InvocationTargetException) {
    throw XposedHelpers.InvocationTargetError(e.targetException ?: e.cause ?: e)
}

private fun resolveParameterTypes(classLoader: ClassLoader?, parameterTypes: Array<out Any?>): Array<Class<*>> {
    return parameterTypes.map { parameterType ->
        when (parameterType) {
            null -> throw NoSuchMethodException("Parameter type is null")
            is Class<*> -> parameterType
            is String -> findClass(parameterType, classLoader)
            else -> throw NoSuchMethodException("Unsupported parameter type: $parameterType")
        }
    }.toTypedArray()
}

private fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
    val methods = mutableListOf<Method>()
    var current: Class<*>? = clazz
    while (current != null) {
        current.declaredMethods
            .filter { it.name == methodName }
            .forEach {
                it.isAccessible = true
                methods += it
            }
        current = current.superclass
    }
    return methods
}

private fun findMethodBestMatch(clazz: Class<*>, methodName: String, args: Array<out Any?>): Method {
    val methods = collectMethods(clazz, methodName)
    val argTypes = args.map { it?.javaClass }.toTypedArray<Class<*>?>()
    return methods.maxByOrNull { scoreExecutable(it.parameterTypes, argTypes) }
        ?.takeIf { scoreExecutable(it.parameterTypes, argTypes) >= 0 }
        ?: throw NoSuchMethodException("No suitable method for ${clazz.name}#$methodName")
}

private fun findConstructorBestMatch(clazz: Class<*>, args: Array<out Any?>): Constructor<*> {
    val argTypes = args.map { it?.javaClass }.toTypedArray<Class<*>?>()
    return clazz.declaredConstructors.maxByOrNull { scoreExecutable(it.parameterTypes, argTypes) }
        ?.takeIf { scoreExecutable(it.parameterTypes, argTypes) >= 0 }
        ?.apply { isAccessible = true }
        ?: throw NoSuchMethodException("No suitable constructor for ${clazz.name}")
}

private fun scoreExecutable(parameterTypes: Array<Class<*>>, argTypes: Array<Class<*>?>): Int {
    if (parameterTypes.size != argTypes.size) return -1
    var score = 0
    for (index in parameterTypes.indices) {
        val parameterType = wrapPrimitive(parameterTypes[index])
        val argType = argTypes[index]
        if (argType == null) {
            if (parameterTypes[index].isPrimitive) return -1
            continue
        }
        if (!parameterType.isAssignableFrom(argType)) return -1
        if (parameterType == argType) score += 2 else score += 1
    }
    return score
}

private fun findField(clazz: Class<*>, fieldName: String): Field {
    var current: Class<*>? = clazz
    while (current != null) {
        try {
            return current.getDeclaredField(fieldName).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    throw NoSuchFieldException("Field not found: ${clazz.name}#$fieldName")
}

private fun getFieldValue(field: Field, obj: Any?, fieldClazz: Class<*>): Any? {
    return when (wrapPrimitive(fieldClazz)) {
        Boolean::class.javaObjectType -> field.getBoolean(obj)
        Byte::class.javaObjectType -> field.getByte(obj)
        Char::class.javaObjectType -> field.getChar(obj)
        Double::class.javaObjectType -> field.getDouble(obj)
        Float::class.javaObjectType -> field.getFloat(obj)
        Int::class.javaObjectType -> field.getInt(obj)
        Long::class.javaObjectType -> field.getLong(obj)
        Short::class.javaObjectType -> field.getShort(obj)
        else -> field.get(obj)
    }
}

private fun setFieldValue(field: Field, obj: Any?, value: Any?, fieldClass: Class<*>) {
    when (wrapPrimitive(fieldClass)) {
        Boolean::class.javaObjectType -> field.setBoolean(obj, value as Boolean)
        Byte::class.javaObjectType -> field.setByte(obj, value as Byte)
        Char::class.javaObjectType -> field.setChar(obj, value as Char)
        Double::class.javaObjectType -> field.setDouble(obj, value as Double)
        Float::class.javaObjectType -> field.setFloat(obj, value as Float)
        Int::class.javaObjectType -> field.setInt(obj, value as Int)
        Long::class.javaObjectType -> field.setLong(obj, value as Long)
        Short::class.javaObjectType -> field.setShort(obj, value as Short)
        else -> field.set(obj, value)
    }
}

private fun wrapPrimitive(clazz: Class<*>): Class<*> {
    return when (clazz) {
        java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
        java.lang.Byte.TYPE -> Byte::class.javaObjectType
        java.lang.Character.TYPE -> Char::class.javaObjectType
        java.lang.Double.TYPE -> Double::class.javaObjectType
        java.lang.Float.TYPE -> Float::class.javaObjectType
        java.lang.Integer.TYPE -> Int::class.javaObjectType
        java.lang.Long.TYPE -> Long::class.javaObjectType
        java.lang.Short.TYPE -> Short::class.javaObjectType
        java.lang.Void.TYPE -> Void::class.javaObjectType
        else -> clazz
    }
}
