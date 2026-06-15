package io.github.magisk317.mipush.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Executable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method as ReflectMethod
import java.lang.reflect.Proxy

class LibXposedHotReloadTest {

    private lateinit var module: TestModule
    private lateinit var api: LibXposedHookApiImpl
    private lateinit var framework: RecordingFramework

    @BeforeEach
    fun setup() {
        framework = RecordingFramework()
        module = testModule(framework)
        XposedRuntime.install(module, 102)
        api = XposedRuntime.hookApi as LibXposedHookApiImpl
    }

    @AfterEach
    fun teardown() {
        XposedRuntime.resetForTest()
    }

    @Test
    fun `hot reload replaces hooks and unhooks stale ones`() {
        val method = String::class.java.getMethod("length")
        
        val oldHandle1 = RecordingHookHandle(method, "handle_1")
        val oldHandle2 = RecordingHookHandle(method, "handle_2")
        val oldHandle3 = RecordingHookHandle(method, "handle_3")
        
        val callback = MethodHook { replace { 0 } }
        val methodHookId = "mipush:${callback.hookIdentity}@java.lang.String#length()"
        
        oldHandle1.testId = methodHookId
        
        api.beginHotReload(listOf(oldHandle1, oldHandle2, oldHandle3))

        val newHandle = api.hookExecutable(method, callback, XposedInterface.Hooker { null })
        
        assertEquals(oldHandle1, newHandle)
        assertTrue(oldHandle1.replaced)

        val removed = api.finishHotReload()
        assertEquals(2, removed)
        assertTrue(oldHandle2.unhooked)
        assertTrue(oldHandle3.unhooked)
        assertFalse(oldHandle1.unhooked)
    }

    private fun testModule(framework: RecordingFramework): TestModule {
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
        var testId: String?,
    ) : XposedInterface.HookHandle {
        var unhooked = false
        var replaced = false

        override fun getExecutable(): Executable = executable

        override fun unhook() {
            unhooked = true
        }

        override fun getId(): String? = testId

        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            replaced = true
            return this
        }
    }
}
