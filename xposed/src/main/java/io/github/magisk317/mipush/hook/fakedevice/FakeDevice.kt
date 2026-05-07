package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage
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
            HookPipelineId.PINDUODUO -> PinDuoDuo()
            HookPipelineId.FAKE_MIUI_ONLY -> FakeMiuiOnly()
            HookPipelineId.COOLAPK -> CoolApk()
        }
    }

    fun fake(lpparam: XC_LoadPackage.LoadPackageParam) {
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

        val profile = ModuleCompatRegistry.getProfile(packageName)
        if (profile == null) {
            XLog.d(TAG, "skip fake() without profile for $packageName in process=$processName")
            return
        }

        // Registration-only profiles still need the runtime registration hook.
        ForceMiPushRegister.hook(lpparam)

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
