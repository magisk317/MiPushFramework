package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.hook.fakedevice.DouyinMiuiGateHook.matchesProviderGate
import io.github.magisk317.xposed.LoadParam
import java.lang.reflect.Method
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DouyinMiuiGateHookTest {
    object GateFixture {
        @JvmStatic
        fun isMiui(): Boolean = false

        @JvmStatic
        fun isMiui(ignoreCache: Boolean): Boolean = !ignoreCache

        @JvmStatic
        fun boxedIsMiui(): Boolean = true

        fun isMiuiInstance(): Boolean = true
    }

    object WrongNameFixture {
        @JvmStatic
        fun isMiuiX(): Boolean = true
    }

    private fun methodsOf(clazz: Class<*>): List<Method> = clazz.declaredMethods.filter { it.matchesProviderGate() }

    @Test
    fun `matches only the static zero-argument primitive boolean isMiui`() {
        val matched = methodsOf(GateFixture::class.java)
        assertTrue(matched.size == 1)
        assertTrue(matched.single().parameterCount == 0)
        assertFalse(
            methodsOf(GateFixture::class.java).any { it.name == "isMiui" && it.parameterCount == 1 },
        )
        assertFalse(
            methodsOf(GateFixture::class.java).any { !java.lang.reflect.Modifier.isStatic(it.modifiers) },
        )
    }

    @Test
    fun `wrong method name does not match the gate`() {
        assertTrue(methodsOf(WrongNameFixture::class.java).isEmpty())
    }

    // --- issue #143: the package-level latch turned a transient miss into a permanent one --------

    /** A loader that can see nothing, standing in for the provisional loader at package load. */
    private class BlindClassLoader : ClassLoader(null) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = throw ClassNotFoundException(name)
    }

    private fun loadParam(loader: ClassLoader, packageName: String = "com.ss.android.ugc.aweme") =
        LoadParam(packageName, "$packageName:push", loader)

    @BeforeEach
    fun resetGateClaims() {
        DouyinMiuiGateHook.resetForTest()
    }

    @Test
    fun `a loader that cannot see the gate class claims nothing`() {
        // The old code recorded the package as handled before the lookup, so this miss locked the
        // package for the rest of the process. A miss must leave no trace.
        DouyinMiuiGateHook.install(loadParam(BlindClassLoader()))
        assertTrue(
            DouyinMiuiGateHook.claimedLoaderIds("com.ss.android.ugc.aweme").isEmpty(),
            "a failed class lookup must not claim its loader",
        )
    }

    @Test
    fun `a later loader still gets an attempt after an earlier loader missed`() {
        val missed = BlindClassLoader()
        DouyinMiuiGateHook.install(loadParam(missed))
        val authoritative = BlindClassLoader()
        assertTrue(
            DouyinMiuiGateHook.shouldAttempt(
                loaderId = System.identityHashCode(authoritative),
                claimed = DouyinMiuiGateHook.claimedLoaderIds("com.ss.android.ugc.aweme"),
            ),
            "the authoritative loader must not be locked out by a provisional miss",
        )
        // Re-running install with the new loader must reach the lookup rather than return early.
        DouyinMiuiGateHook.install(loadParam(authoritative))
        assertTrue(DouyinMiuiGateHook.claimedLoaderIds("com.ss.android.ugc.aweme").isEmpty())
    }

    @Test
    fun `the same loader is never retried once it owns the gate`() {
        assertFalse(DouyinMiuiGateHook.shouldAttempt(loaderId = 7, claimed = setOf(7)))
    }

    @Test
    fun `loader claims stop at the bound instead of growing with loader churn`() {
        val claimed = (1..DouyinMiuiGateHook.MAX_LOADERS_PER_PACKAGE).toSet()
        assertFalse(DouyinMiuiGateHook.shouldAttempt(loaderId = 99, claimed = claimed))
        assertTrue(DouyinMiuiGateHook.shouldAttempt(loaderId = 99, claimed = claimed - 1))
    }

    @Test
    fun `gate hits log every early call then thin out`() {
        (1L..DouyinMiuiGateHook.VERBOSE_HIT_LIMIT).forEach { assertTrue(DouyinMiuiGateHook.shouldLogHit(it)) }
        assertFalse(DouyinMiuiGateHook.shouldLogHit(DouyinMiuiGateHook.VERBOSE_HIT_LIMIT + 1))
        assertTrue(DouyinMiuiGateHook.shouldLogHit(DouyinMiuiGateHook.SPARSE_HIT_MODULO))
        assertFalse(DouyinMiuiGateHook.shouldLogHit(DouyinMiuiGateHook.SPARSE_HIT_MODULO + 1))
        assertFalse(DouyinMiuiGateHook.shouldLogHit(0))
    }
}
