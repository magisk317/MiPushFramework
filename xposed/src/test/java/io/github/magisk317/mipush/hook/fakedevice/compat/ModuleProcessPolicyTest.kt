package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleProcessPolicyTest {
    private val baseProfile = ModuleCompatProfile(
        packageName = "com.example.app",
        hookPipelines = listOf(HookPipelineId.COMMON),
    )

    @Test
    fun `default policy allows main and known push processes`() {
        assertTrue(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.example.app"))
        assertTrue(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.example.app:push"))
        assertTrue(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.example.app:mipush"))
    }

    @Test
    fun `default policy rejects unrelated and denied processes`() {
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.other.app"))
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.example.app:webview"))
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(baseProfile, "com.example.app", "com.example.app:isolated"))
    }

    @Test
    fun `profile overrides allowed and denied process rules`() {
        val profile = baseProfile.copy(
            allowedProcessSuffixes = setOf(":sync"),
            deniedProcessPrefixes = listOf(":sync"),
        )
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(profile, "com.example.app", "com.example.app:push"))
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(profile, "com.example.app", "com.example.app:sync"))
    }

    @Test
    fun `auto detected profile allows custom process names`() {
        val profile = ModuleCompatProfile(
            packageName = "com.example.app",
            hookPipelines = emptyList(),
            isAutoDetected = true,
        )

        assertTrue(ModuleProcessPolicy.shouldHandleProcess(profile, "com.example.app", "com.vendor.customprocess"))
        assertFalse(ModuleProcessPolicy.shouldHandleProcess(profile, "com.example.app", ""))
    }
}
