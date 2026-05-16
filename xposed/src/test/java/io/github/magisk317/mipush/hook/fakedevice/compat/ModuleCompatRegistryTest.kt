package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `registry enables MiPush component visibility for hidden-sensitive packages`() {
        assertTrue(
            ModuleCompatRegistry.resolveHookPipelines("com.ss.android.ugc.aweme")
                .contains(HookPipelineId.MIPUSH_COMPONENT_VISIBILITY),
        )
        assertTrue(
            ModuleCompatRegistry.resolveHookPipelines("com.jingdong.app.mall")
                .contains(HookPipelineId.MIPUSH_COMPONENT_VISIBILITY),
        )
    }

    @Test
    fun `registry keeps remote discovered credential overrides`() {
        assertEquals(
            ModuleCredential(appId = "2882303761517506461", appKey = "5601750626461"),
            ModuleCompatRegistry.credentialOverride("com.jingdong.app.mall"),
        )
        assertEquals(
            ModuleCredential(appId = "2882303761517245189", appKey = "5461724563189"),
            ModuleCompatRegistry.credentialOverride("com.taobao.idlefish"),
        )
        assertEquals(
            ModuleCredential(appId = "2882303761517463096", appKey = "5101746355096"),
            ModuleCompatRegistry.credentialOverride("com.tencent.wework"),
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
        assertEquals(
            ModuleCredential(appId = "2882303761517155131", appKey = "5431715541131"),
            ModuleCompatRegistry.credentialOverride("com.coolapk.market"),
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
    fun `registry builds auto force register profile when mipush classes are present`() {
        val loader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                if (name == "com.xiaomi.mipush.sdk.MiPushClient") {
                    return String::class.java
                }
                throw ClassNotFoundException(name)
            }
        }

        val profile = ModuleCompatRegistry.buildAutoForceRegisterProfile(
            packageName = "com.example.auto",
            processName = "com.example.auto",
            classLoader = loader,
        )

        assertNotNull(profile)
        assertEquals(
            listOf(
                HookPipelineId.COMMON,
                HookPipelineId.HUAWEI_HMS,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
                HookPipelineId.MEIZU_PUSH,
                HookPipelineId.JPUSH,
                HookPipelineId.ALI_AGOO_ACCS,
                HookPipelineId.UMENG_PUSH,
                HookPipelineId.MIPUSH_COMPONENT_VISIBILITY,
            ),
            profile!!.hookPipelines,
        )
        assertTrue(profile.isAutoDetected)
    }

    @Test
    fun `registry builds auto force register profile for custom process names`() {
        val loader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                if (name == "com.tencent.android.mipush.XMPushMessageReceiver") {
                    return String::class.java
                }
                throw ClassNotFoundException(name)
            }
        }

        val profile = ModuleCompatRegistry.buildAutoForceRegisterProfile(
            packageName = "com.example.auto",
            processName = "com.vendor.customprocess",
            classLoader = loader,
        )

        assertNotNull(profile)
        assertTrue(profile!!.isAutoDetected)
    }

    @Test
    fun `registry skips auto force register profile for Xiaomi system packages`() {
        val loader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                if (name == "com.xiaomi.mipush.sdk.MiPushClient") {
                    return String::class.java
                }
                throw ClassNotFoundException(name)
            }
        }

        assertNull(
            ModuleCompatRegistry.buildAutoForceRegisterProfile(
                packageName = "com.xiaomi.account",
                processName = "com.xiaomi.account",
                classLoader = loader,
            ),
        )
        assertNull(
            ModuleCompatRegistry.buildAutoForceRegisterProfile(
                packageName = "com.miui.cloudservice",
                processName = "com.miui.cloudservice",
                classLoader = loader,
            ),
        )
    }

    @Test
    fun `registry keeps explicit Xiaomi package profile`() {
        val profile = ModuleCompatRegistry.getProfile("com.xiaomi.smarthome")

        assertNotNull(profile)
        assertEquals(
            listOf(
                HookPipelineId.JPUSH,
                HookPipelineId.VIVO_PUSH,
                HookPipelineId.OPPO_HEYTAP,
            ),
            profile!!.hookPipelines,
        )
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
