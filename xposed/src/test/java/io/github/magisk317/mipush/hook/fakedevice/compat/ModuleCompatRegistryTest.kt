package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleCompatRegistryTest {
    @Test
    fun `registry resolves hook pipelines for known package`() {
        assertEquals(
            listOf(
                HookPipelineId.QQ,
                HookPipelineId.XGPUSH,
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
            ),
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
    fun `registry keeps credential override with vendor pipelines`() {
        val profile = ModuleCompatRegistry.getProfile("com.unionpay")
        assertNotNull(profile)
        assertEquals(
            listOf(
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
                HookPipelineId.MEIZU_PUSH,
            ),
            profile!!.hookPipelines,
        )
        assertEquals("2882303761517149951", profile.credentialOverride?.appId)
    }

    @Test
    fun `registry resolves vendor fallback pipelines for coolapk`() {
        assertEquals(
            listOf(
                HookPipelineId.COOLAPK,
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
                HookPipelineId.MEIZU_PUSH,
            ),
            ModuleCompatRegistry.resolveHookPipelines("com.coolapk.market"),
        )
    }

    @Test
    fun `registry resolves ali umeng and vendor pipelines for lark`() {
        assertEquals(
            listOf(
                HookPipelineId.DOUYIN,
                HookPipelineId.ALI_AGOO_ACCS,
                HookPipelineId.UMENG_PUSH,
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
                HookPipelineId.MEIZU_PUSH,
            ),
            ModuleCompatRegistry.resolveHookPipelines("com.ss.android.lark"),
        )
    }

    @Test
    fun `registry resolves jpush pipelines for explicit package`() {
        assertEquals(
            listOf(
                HookPipelineId.JPUSH,
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
                HookPipelineId.MEIZU_PUSH,
            ),
            ModuleCompatRegistry.resolveHookPipelines("com.ziroom.ziroomcustomer"),
        )
    }

    @Test
    fun `registry builds auto common profile when mipush classes are present`() {
        val loader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                if (name == "com.xiaomi.mipush.sdk.MiPushClient") {
                    return String::class.java
                }
                throw ClassNotFoundException(name)
            }
        }

        val profile = ModuleCompatRegistry.buildAutoCommonProfile(
            packageName = "com.example.auto",
            processName = "com.example.auto",
            classLoader = loader,
        )

        assertNotNull(profile)
        assertEquals(listOf(HookPipelineId.COMMON), profile!!.hookPipelines)
        assertTrue(profile.isAutoDetected)
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
