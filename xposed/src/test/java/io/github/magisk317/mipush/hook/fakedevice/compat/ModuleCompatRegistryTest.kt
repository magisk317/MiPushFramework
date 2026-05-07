package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleCompatRegistryTest {
    @Test
    fun `registry resolves hook pipelines for known package`() {
        assertEquals(
            listOf(HookPipelineId.QQ, HookPipelineId.XGPUSH),
            ModuleCompatRegistry.resolveHookPipelines("com.tencent.mobileqq"),
        )
    }

    @Test
    fun `registry resolves credential override for known package`() {
        assertEquals(
            ModuleCredential(appId = "2882303761517509924", appKey = "5571750917924"),
            ModuleCompatRegistry.credentialOverride("com.ss.android.ugc.aweme"),
        )
    }

    @Test
    fun `registry keeps credential only profiles`() {
        val profile = ModuleCompatRegistry.getProfile("com.unionpay")
        assertNotNull(profile)
        assertEquals(emptyList<HookPipelineId>(), profile!!.hookPipelines)
        assertEquals("2882303761517149951", profile.credentialOverride?.appId)
    }

    @Test
    fun `registry keeps registration only profile without hook pipelines`() {
        val profile = ModuleCompatRegistry.getProfile("com.alibaba.android.rimet")
        assertNotNull(profile)
        assertEquals(emptyList<HookPipelineId>(), profile!!.hookPipelines)
        assertNull(profile.credentialOverride)
    }

    @Test
    fun `registry returns null for unknown package`() {
        assertNull(ModuleCompatRegistry.getProfile("com.example.unknown"))
    }
}
