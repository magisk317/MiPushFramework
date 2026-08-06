package io.github.magisk317.mipush.hook.keepalive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeepAliveHookTargetsTest {
    @Test
    fun `api 33 targets retain the exact parameter roles`() {
        val bucket = KeepAliveHookTargets.standbyBucket.single()
        val appIdle = KeepAliveHookTargets.appIdle.single()
        val forceIdle = KeepAliveHookTargets.forceIdle.single()

        assertEquals(2, bucket.valueIndex)
        assertEquals(0, bucket.packageIndex)
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
