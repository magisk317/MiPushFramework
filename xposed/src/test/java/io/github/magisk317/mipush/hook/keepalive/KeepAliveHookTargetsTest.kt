package io.github.magisk317.mipush.hook.keepalive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeepAliveHookTargetsTest {
    @Test
    fun `android 17 targets use psc process state and current kill signature`() {
        val oom = KeepAliveHookTargets.oomApply.first()
        assertEquals("com.android.server.am.psc.OomAdjuster", oom.shape.owner)
        assertEquals(
            listOf("com.android.server.am.psc.ProcessRecordInternal", "boolean"),
            oom.shape.parameterTypes,
        )
        assertEquals("void", oom.shape.returnType)

        val kill = KeepAliveHookTargets.killLocked.first {
            it.shape.parameterTypes.any { type -> type.contains("ApplicationExitInfo") }
        }
        assertEquals(
            "android.app.ApplicationExitInfo\$AnrInfo",
            kill.shape.parameterTypes[4],
        )
        assertEquals(2, kill.valueIndex)
        assertEquals(3, kill.secondaryValueIndex)
    }

    @Test
    fun `api 33 targets retain the exact parameter roles`() {
        val appIdle = KeepAliveHookTargets.appIdle.single()
        val forceIdle = KeepAliveHookTargets.forceIdle.single()

        assertEquals(
            listOf(1, 2, 2),
            KeepAliveHookTargets.standbyBucket.map { it.valueIndex },
        )
        assertTrue(KeepAliveHookTargets.standbyBucket.all { it.packageIndex == 0 })
        val packageKill = KeepAliveHookTargets.packageKill.single()
        assertEquals(0, packageKill.packageIndex)
        assertEquals(10, packageKill.valueIndex)
        assertEquals(11, packageKill.secondaryValueIndex)
        assertEquals(1, appIdle.valueIndex)
        assertEquals(2, forceIdle.valueIndex)
    }

    @Test
    fun `resolver rejects same name with a changed parameter type`() {
        val target = IndexedHookTarget(
            capability = "test",
            shape = MethodShape(
                owner = ResolverFixture::class.java.name,
                name = "target",
                parameterTypes = listOf("java.lang.String", "int"),
                returnType = "void",
            ),
        )

        val resolved = KeepAliveHookTargets.resolve(ResolverFixture::class.java, listOf(target))

        assertTrue(resolved is MethodResolution.Resolved)
        assertEquals("target", (resolved as MethodResolution.Resolved).method.name)
    }

    @Test
    fun `resolver reports a missing exact target`() {
        val target = IndexedHookTarget(
            capability = "test",
            shape = MethodShape(
                owner = ResolverFixture::class.java.name,
                name = "target",
                parameterTypes = listOf("java.lang.String", "long"),
                returnType = "void",
            ),
        )

        assertEquals(MethodResolution.Missing, KeepAliveHookTargets.resolve(ResolverFixture::class.java, listOf(target)))
    }

    private class ResolverFixture {
        @Suppress("unused")
        fun target(packageName: String, value: Int) = Unit

        @Suppress("unused")
        fun target(packageName: String, value: Boolean) = Unit
    }
}
