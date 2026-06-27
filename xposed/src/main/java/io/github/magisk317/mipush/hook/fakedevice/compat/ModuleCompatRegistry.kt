package io.github.magisk317.mipush.hook.fakedevice.compat

import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy

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
        HookPipelineId.MIPUSH_COMPONENT_VISIBILITY,
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

    // 显式覆盖特定包的配置（用于修正生成配置中的错误或特殊需求）
    private val overrideProfiles: Map<String, ModuleCompatProfile> = mapOf(
        // registration-only 示例：仅注册，不挂载任何 hook 管道
        "com.alibaba.android.rimet" to ModuleCompatProfile(
            packageName = "com.alibaba.android.rimet",
            hookPipelines = emptyList(),
            credentialOverride = null,
            isAutoDetected = false
        )
        // 如有其他需要覆盖的包，在此添加
    )

    private val profilesByPackage: Map<String, ModuleCompatProfile> =
        (GeneratedCompatProfiles.profiles
            .filter { ZygiskPackagePolicy.isManagedPackage(it.packageName) }
            .associateBy(ModuleCompatProfile::packageName) + overrideProfiles)
            .toMap() // 确保不可变，且覆盖映射优先

    fun allProfiles(): List<ModuleCompatProfile> = profilesByPackage.values.toList()

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
        if (!ZygiskPackagePolicy.isManagedPackage(packageName)) return null
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