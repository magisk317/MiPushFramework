package io.github.magisk317.mipush.hook.fakedevice.compat

object ModuleCompatRegistry {
    private val autoCommonCandidates = setOf(
        "com.xiaomi.mipush.sdk.MiPushClient",
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.PushServiceReceiver",
        "org.android.agoo.xiaomi.MiPushBroadcastReceiver",
        "org.android.agoo.xiaomi.MiPushRegistar",
        "com.umeng.message.XiaomiIntentService",
        "cn.jpush.android.thirdpush.xiaomi.XMPushManager",
    )

    private val profilesByPackage: Map<String, ModuleCompatProfile> =
        GeneratedCompatProfiles.profiles.associateBy(ModuleCompatProfile::packageName)

    fun allProfiles(): List<ModuleCompatProfile> = GeneratedCompatProfiles.profiles

    fun getProfile(packageName: String): ModuleCompatProfile? = profilesByPackage[packageName]

    fun resolveProfile(
        packageName: String,
        processName: String,
        classLoader: ClassLoader?,
    ): ModuleCompatProfile? {
        return getProfile(packageName)
            ?: buildAutoCommonProfile(packageName, processName, classLoader)
    }

    fun resolveHookPipelines(packageName: String): List<HookPipelineId> {
        return getProfile(packageName)?.hookPipelines.orEmpty()
    }

    fun credentialOverride(packageName: String): ModuleCredential? {
        return getProfile(packageName)?.credentialOverride
    }

    internal fun buildAutoCommonProfile(
        packageName: String,
        processName: String,
        classLoader: ClassLoader?,
    ): ModuleCompatProfile? {
        if (classLoader == null) return null
        if (processName.isBlank()) return null
        if (processName != packageName && !processName.startsWith("$packageName:")) {
            return null
        }
        val hasMiPushSdk = autoCommonCandidates.any { className ->
            runCatching { classLoader.loadClass(className) }.isSuccess
        }
        if (!hasMiPushSdk) return null
        return ModuleCompatProfile(
            packageName = packageName,
            hookPipelines = listOf(HookPipelineId.COMMON),
            isAutoDetected = true,
        )
    }
}
