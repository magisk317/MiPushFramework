package io.github.magisk317.mipush.hook.fakedevice.compat

object ModuleCompatRegistry {
    private val autoAggressivePipelines = listOf(
        HookPipelineId.COMMON,
        HookPipelineId.HUAWEI_HMS,
        HookPipelineId.VIVO_PUSH,
        HookPipelineId.OPPO_HEYTAP,
        HookPipelineId.MEIZU_PUSH,
        HookPipelineId.JPUSH,
        HookPipelineId.ALI_AGOO_ACCS,
        HookPipelineId.UMENG_PUSH,
    )

    private val autoForceRegisterCandidates = setOf(
        "com.xiaomi.mipush.sdk.MiPushClient",
        "com.xiaomi.mipush.sdk.MessageHandleService",
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.PushServiceReceiver",
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.push.service.XMJobService",
        "com.xiaomi.push.service.receivers.MIPushMessageHandler",
        "com.xiaomi.push.service.receivers.SmpMIPushMessageHandler",
        "com.xiaomi.push.service.receivers.WidgetProviderMIPushMessageHandler",
        "org.android.agoo.xiaomi.MiPushBroadcastReceiver",
        "org.android.agoo.xiaomi.MiPushRegistar",
        "com.umeng.message.XiaomiIntentService",
        "cn.jpush.android.thirdpush.xiaomi.XMPushManager",
        "com.tencent.android.mipush.XMPushMessageReceiver",
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
            ?: buildAutoForceRegisterProfile(packageName, processName, classLoader)
    }

    fun resolveHookPipelines(packageName: String): List<HookPipelineId> {
        return getProfile(packageName)?.hookPipelines.orEmpty()
    }

    fun credentialOverride(packageName: String): ModuleCredential? {
        return getProfile(packageName)?.credentialOverride
    }

    internal fun buildAutoForceRegisterProfile(
        packageName: String,
        processName: String,
        classLoader: ClassLoader?,
    ): ModuleCompatProfile? {
        if (classLoader == null) return null
        if (processName.isBlank()) return null
        val hasMiPushSdk = autoForceRegisterCandidates.any { className ->
            runCatching { classLoader.loadClass(className) }.isSuccess
        }
        if (!hasMiPushSdk) return null
        return ModuleCompatProfile(
            packageName = packageName,
            hookPipelines = autoAggressivePipelines,
            isAutoDetected = true,
        )
    }
}
