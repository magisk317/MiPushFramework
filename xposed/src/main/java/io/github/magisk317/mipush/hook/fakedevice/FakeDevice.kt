package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.hook.compat.legacyhuawei.LegacyHuaweiSignatureCompat
import io.github.magisk317.mipush.hook.fakedevice.compat.HookPipelineId
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleCompatRegistry
import io.github.magisk317.mipush.hook.XLog

object FakeDevice {
    private const val TAG = "FakeDevice"

    private fun createPipelineHook(pipelineId: HookPipelineId): IFakeDevice {
        return when (pipelineId) {
            HookPipelineId.COMMON -> Common()
            HookPipelineId.ALIPAY -> Alipay()
            HookPipelineId.DOUYIN -> DouYin()
            HookPipelineId.QQ -> QQ()
            HookPipelineId.XGPUSH -> XGPush()
            HookPipelineId.HUAWEI_HMS -> HuaweiHmsPush()
            HookPipelineId.VIVO_PUSH -> VivoPush()
            HookPipelineId.OPPO_HEYTAP -> OppoHeytapPush()
            HookPipelineId.MEIZU_PUSH -> MeizuPush()
            HookPipelineId.JPUSH -> JPush()
            HookPipelineId.ALI_AGOO_ACCS -> AliAgooAccs()
            HookPipelineId.UMENG_PUSH -> UmengPush()
            HookPipelineId.PINDUODUO -> PinDuoDuo()
            HookPipelineId.MIPUSH_COMPONENT_VISIBILITY -> MiPushComponentVisibility()
            HookPipelineId.FAKE_MIUI_ONLY -> FakeMiuiOnly()
            HookPipelineId.COOLAPK -> CoolApk()
        }
    }

    fun fake(lpparam: LoadParam) {
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()
        XLog.d(TAG, "fake() called with: packageName = $packageName, processName = $processName")
        if (packageName == "com.google.android.webview") {
            XLog.d(TAG, "fake() called, ignore $packageName")
            return
        }
        if (processName.isBlank()) {
            XLog.w(TAG, "skip fake() for package without processName: $packageName")
            return
        }

        val profile = ModuleCompatRegistry.resolveProfile(packageName, processName, lpparam.classLoader)
        if (profile == null) {
            XLog.d(TAG, "skip fake() without profile for $packageName in process=$processName")
            return
        }
        if (profile.isAutoDetected) {
            XLog.i(
                TAG,
                "auto-detected MiPush compat profile for $packageName in process=$processName " +
                    "pipelines=${profile.hookPipelines.joinToString()}"
            )
        }

        // Registration-only profiles still need the runtime registration hook.
        ForceMiPushRegister.hook(lpparam, profile)

        val pipelines = profile.hookPipelines
        if (pipelines.isEmpty()) {
            XLog.d(TAG, "registration-only profile active for $packageName in process=$processName")
            return
        }

        LegacyHuaweiSignatureCompat.hook(lpparam)
        pipelines.distinct().forEach { pipelineId ->
            createPipelineHook(pipelineId).fake(lpparam)
        }
    }
}
