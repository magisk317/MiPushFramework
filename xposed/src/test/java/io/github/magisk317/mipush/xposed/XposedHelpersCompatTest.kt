package io.github.magisk317.mipush.xposed

import io.github.libxposed.api.XposedInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Executable
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
}
