package io.github.magisk317.mipush.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Executable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method as ReflectMethod
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

class XposedHelpersCompatTest {
    @Test
    fun `callMethod unwraps invocation target exception like legacy Xposed helper`() {
        val target = HelperTarget()
        val thrown = assertThrows(XposedHelpers.InvocationTargetError::class.java) {
            target.callMethod("throwChecked")
        }

        assertSame(target.failure, thrown.targetException)
        assertSame(target.failure, thrown.cause)
    }

    @Test
    fun `callMethod best match handles boxed primitive arguments`() {
        assertEquals("int:7", HelperTarget().callMethod("overloaded", 7))
    }

    @Test
    fun `callMethod exact overload honors explicit parameter types`() {
        val target = HelperTarget()

        assertEquals(
            "number:7",
            target.callMethod("overloaded", arrayOf(Number::class.java), 7),
        )
    }

    @Test
    fun `set object field walks superclass fields`() {
        val target = HelperTarget()

        XposedHelpers.setObjectField(target, "parentValue", "patched")

        assertEquals("patched", target.parentValue())
    }

    @Test
    fun `method hook forwards before-mutated arguments to libxposed chain`() {
        val method = HelperTarget::class.java.getDeclaredMethod(
            "overloaded",
            Int::class.javaPrimitiveType,
        )
        val chain = FakeChain(method, HelperTarget(), arrayOf(1)) { args ->
            "int:${args[0]}"
        }

        val result = MethodHook {
            doBefore {
                args[0] = 9
            }
        }.intercept(chain, AtomicReference())

        assertEquals("int:9", result)
        assertEquals(9, chain.proceededArgs?.single())
    }

    @Test
    fun `method hook after callback can override proceeded result`() {
        val method = HelperTarget::class.java.getDeclaredMethod(
            "overloaded",
            Int::class.javaPrimitiveType,
        )
        val chain = FakeChain(method, HelperTarget(), arrayOf(7)) { "original" }

        val result = MethodHook {
            doAfter {
                result = "$result-patched"
            }
        }.intercept(chain, AtomicReference())

        assertEquals("original-patched", result)
    }

    @Test
    fun `method hook after callback can recover original throwable`() {
        val method = HelperTarget::class.java.getDeclaredMethod("throwChecked")
        val failure = IllegalStateException("chain boom")
        val chain = FakeChain(method, HelperTarget(), emptyArray()) {
            throw failure
        }

        val result = MethodHook {
            doAfter {
                assertSame(failure, throwable)
                throwable = null
                result = "fallback"
            }
        }.intercept(chain, AtomicReference())

        assertEquals("fallback", result)
    }

    @Test
    fun `invokeOriginal bypasses libxposed chain in compat fallback`() {
        val method = HelperTarget::class.java.getDeclaredMethod(
            "overloaded",
            Int::class.javaPrimitiveType,
        )
        val chain = FakeChain(method, HelperTarget(), arrayOf(3)) {
            throw AssertionError("invokeOriginal must not proceed through the hook chain")
        }

        val result = MethodHook {
            replace {
                invokeOriginal()
            }
        }.intercept(chain, AtomicReference())

        assertEquals("int:3", result)
        assertNull(chain.proceededArgs)
    }

    @Test
    fun `api101 runtime installs hook without hook id`() {
        val framework = RecordingFramework()
        XposedRuntime.install(testModule(framework), apiVersion = 101)
        try {
            val method = HelperTarget::class.java.getDeclaredMethod(
                "overloaded",
                Int::class.javaPrimitiveType,
            )

            val handle = method.hook { doAfter { result = result } }

            assertNotNull(handle)
            assertEquals(1, framework.hookCalls)
            assertEquals(0, framework.builders.single().setIdCalls)
            assertEquals(1, framework.builders.single().interceptCalls)
        } finally {
            XposedRuntime.resetForTest()
        }
    }

    @Test
    fun `api102 runtime installs hook with stable hook id`() {
        val framework = RecordingFramework()
        XposedRuntime.install(testModule(framework), apiVersion = 102)
        try {
            val method = HelperTarget::class.java.getDeclaredMethod(
                "overloaded",
                Int::class.javaPrimitiveType,
            )

            val handle = method.hook { doAfter { result = result } }

            assertNotNull(handle)
            assertEquals(1, framework.hookCalls)
            assertEquals(1, framework.builders.single().setIdCalls)
            assertTrue(framework.builders.single().id.orEmpty().startsWith("mipush:"))
            assertEquals(1, framework.builders.single().interceptCalls)
        } finally {
            XposedRuntime.resetForTest()
        }
    }

    private open class ParentTarget {
        private var parentValue: String = "original"

        fun parentValue(): String = parentValue
    }

    private class HelperTarget : ParentTarget() {
        val failure = IllegalStateException("boom")

        fun overloaded(value: Int): String = "int:$value"

        fun overloaded(value: Number): String = "number:$value"

        fun throwChecked(): Unit = throw failure
    }

    private class FakeChain(
        private val executable: Executable,
        private val receiver: Any?,
        args: Array<Any?>,
        private val action: (Array<Any?>) -> Any?,
    ) : XposedInterface.Chain {
        private val initialArgs = args.toList()
        var proceededArgs: Array<Any?>? = null
            private set

        override fun getExecutable(): Executable = executable

        override fun getThisObject(): Any? = receiver

        override fun getArgs(): List<Any?> = initialArgs

        override fun getArg(index: Int): Any? = initialArgs[index]

        override fun proceed(): Any? = proceed(initialArgs.toTypedArray())

        override fun proceed(args: Array<Any?>): Any? {
            proceededArgs = args
            return action(args)
        }

        override fun proceedWith(thisObject: Any): Any? = proceed()

        override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = proceed(args)
    }

    private fun testModule(framework: RecordingFramework): XposedModule {
        return TestModule().apply {
            attachFramework(framework.proxy, Runnable {})
        }
    }

    private class TestModule : XposedModule()

    private class RecordingFramework : InvocationHandler {
        val proxy: XposedInterface = Proxy.newProxyInstance(
            XposedInterface::class.java.classLoader,
            arrayOf(XposedInterface::class.java),
            this,
        ) as XposedInterface
        val builders = mutableListOf<RecordingHookBuilder>()
        var hookCalls = 0

        override fun invoke(proxy: Any, method: ReflectMethod, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "hook" -> {
                    hookCalls += 1
                    RecordingHookBuilder(args?.first() as Executable).also(builders::add)
                }
                "getFrameworkName" -> "libxposed"
                "getFrameworkVersion" -> "test"
                "getFrameworkVersionCode" -> 1L
                "getFrameworkProperties" -> 0L
                "log" -> Unit
                "toString" -> "RecordingFramework"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        }

        private fun defaultValue(returnType: Class<*>): Any? {
            return when (returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Character.TYPE -> 0.toChar()
                java.lang.Void.TYPE -> Unit
                else -> null
            }
        }
    }

    private class RecordingHookBuilder(
        private val executable: Executable,
    ) : XposedInterface.HookBuilder {
        var id: String? = null
        var setIdCalls = 0
        var interceptCalls = 0

        override fun setPriority(priority: Int): XposedInterface.HookBuilder = this

        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this

        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            interceptCalls += 1
            return RecordingHookHandle(executable, id)
        }

        override fun setId(id: String?): XposedInterface.HookBuilder {
            setIdCalls += 1
            this.id = id
            return this
        }
    }

    private class RecordingHookHandle(
        private val executable: Executable,
        private val id: String?,
    ) : XposedInterface.HookHandle {
        override fun getExecutable(): Executable = executable

        override fun unhook() = Unit

        override fun getId(): String? = id

        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            return RecordingHookHandle(executable, id)
        }
    }
}
