package io.github.magisk317.mipush.hook.fakedevice.compat

data class ModuleCredential(
    val appId: String,
    val appKey: String,
)

data class ModuleCompatProfile(
    val packageName: String,
    val hookPipelines: List<HookPipelineId>,
    val credentialOverride: ModuleCredential? = null,
    val allowedProcessSuffixes: Set<String>? = null,
    val deniedProcessPrefixes: List<String>? = null,
)
