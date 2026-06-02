package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class FocusSemanticTranslatorTest {

    @Test
    fun `non MIUI progress focus is translated to native live update semantics`() {
        val plan = FocusSemanticTranslator.plan(
            context = RuntimeEnvironment.getApplication(),
            metaInfo = PushMetaInfo().apply {
                title = "状态更新"
                description = "处理中"
            },
            packageName = "com.example.app",
            configuredFocusParam = progressFocusParam,
            generatedFocusParam = null,
            generatedFocusCandidate = true,
            capabilities = nonMiui(supportsNativeLiveUpdates = true),
        )

        assertFalse(plan.attachMiuiFocusExtras)
        assertFalse(plan.allowIslandProxy)
        assertTrue(plan.useNativeProgress)
        assertEquals("native_live_update", plan.reason)
        assertEquals(LiveUpdateDetector.ProgressCategory.DOWNLOAD, plan.nativeDetection?.category)
        assertEquals(65, plan.nativeDetection?.progressPercent)
    }

    @Test
    fun `non MIUI non progress focus remains a standard notification`() {
        val plan = FocusSemanticTranslator.plan(
            context = RuntimeEnvironment.getApplication(),
            metaInfo = PushMetaInfo().apply {
                title = "Alice"
                description = "hello"
            },
            packageName = "com.example.chat",
            configuredFocusParam = chatFocusParam,
            generatedFocusParam = null,
            generatedFocusCandidate = true,
            capabilities = nonMiui(supportsNativeLiveUpdates = true),
        )

        assertFalse(plan.attachMiuiFocusExtras)
        assertFalse(plan.allowIslandProxy)
        assertFalse(plan.useNativeProgress)
        assertNull(plan.nativeDetection)
        assertEquals("standard_notification", plan.reason)
    }

    @Test
    fun `non MIUI progress focus falls back below Android 16`() {
        val plan = FocusSemanticTranslator.plan(
            context = RuntimeEnvironment.getApplication(),
            metaInfo = PushMetaInfo().apply {
                title = "状态更新"
                description = "处理中"
            },
            packageName = "com.example.app",
            configuredFocusParam = progressFocusParam,
            generatedFocusParam = null,
            generatedFocusCandidate = true,
            capabilities = nonMiui(supportsNativeLiveUpdates = false),
        )

        assertTrue(plan.useNativeProgress)
        assertEquals("fallback_progress", plan.reason)
        assertEquals(65, plan.nativeDetection?.progressPercent)
    }

    @Test
    fun `MIUI configured focus keeps private extras instead of native translation`() {
        val plan = FocusSemanticTranslator.plan(
            context = RuntimeEnvironment.getApplication(),
            metaInfo = PushMetaInfo().apply {
                title = "状态更新"
                description = "处理中"
            },
            packageName = "com.example.app",
            configuredFocusParam = progressFocusParam,
            generatedFocusParam = null,
            generatedFocusCandidate = true,
            capabilities = miui(),
        )

        assertTrue(plan.attachMiuiFocusExtras)
        assertFalse(plan.allowIslandProxy)
        assertFalse(plan.useNativeProgress)
        assertNotNull(plan.semantic)
        assertEquals("miui_focus_extras", plan.reason)
    }

    @Test
    fun `MIUI generated focus uses island proxy path`() {
        val plan = FocusSemanticTranslator.plan(
            context = RuntimeEnvironment.getApplication(),
            metaInfo = PushMetaInfo().apply {
                title = "骑手已取餐"
                description = "正在配送中 预计5分钟送达"
            },
            packageName = "com.example.delivery",
            configuredFocusParam = null,
            generatedFocusParam = progressFocusParam,
            generatedFocusCandidate = true,
            capabilities = miui(),
        )

        assertFalse(plan.attachMiuiFocusExtras)
        assertTrue(plan.allowIslandProxy)
        assertFalse(plan.useNativeProgress)
        assertEquals("miui_island_proxy", plan.reason)
    }

    private fun nonMiui(supportsNativeLiveUpdates: Boolean): FocusSemanticTranslator.Capabilities {
        return FocusSemanticTranslator.Capabilities(
            supportsMiuiFocusExtras = false,
            supportsNativeLiveUpdates = supportsNativeLiveUpdates,
            supportsSemanticColors = supportsNativeLiveUpdates,
        )
    }

    private fun miui(): FocusSemanticTranslator.Capabilities {
        return FocusSemanticTranslator.Capabilities(
            supportsMiuiFocusExtras = true,
            supportsNativeLiveUpdates = true,
            supportsSemanticColors = true,
        )
    }

    private val progressFocusParam = """
        {
          "param_v2": {
            "businessName": "mipush_framework_push",
            "iconTextInfo": {
              "title": "下载更新",
              "content": "下载中 65%"
            },
            "hintInfo": {
              "title": "下载中"
            },
            "progressBar": {
              "progress": 65
            }
          }
        }
    """.trimIndent()

    private val chatFocusParam = """
        {
          "param_v2": {
            "businessName": "mipush_framework_push",
            "chatInfo": {
              "title": "Alice",
              "content": "hello"
            }
          }
        }
    """.trimIndent()
}
