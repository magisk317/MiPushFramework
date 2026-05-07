package io.github.magisk317.mipush.hook.fakedevice.compat

object ModuleCompatRegistry {
    private val profilesByPackage: Map<String, ModuleCompatProfile> =
        GeneratedCompatProfiles.profiles.associateBy(ModuleCompatProfile::packageName)

    fun allProfiles(): List<ModuleCompatProfile> = GeneratedCompatProfiles.profiles

    fun getProfile(packageName: String): ModuleCompatProfile? = profilesByPackage[packageName]

    fun resolveHookPipelines(packageName: String): List<HookPipelineId> {
        return getProfile(packageName)?.hookPipelines.orEmpty()
    }

    fun credentialOverride(packageName: String): ModuleCredential? {
        return getProfile(packageName)?.credentialOverride
    }
}
