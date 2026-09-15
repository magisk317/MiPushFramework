package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.compat.legacyhuawei.LegacyHuaweiSignatureCompat
import io.github.magisk317.mipush.hook.fakedevice.compat.HookPipelineId
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleCompatRegistry
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.MagiskOtel

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
        fun emit(result: String, reason: String, statusOk: Boolean = true, pipelineCount: Int? = null) {
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "fake_device",
                "reason" to reason,
            )
            if (packageName.isNotBlank()) attrs["target_package"] = packageName
            if (pipelineCount != null) attrs["found_count"] = pipelineCount.toString()
            MagiskOtel.event(name = "hook.load", attributes = attrs, statusOk = statusOk)
        }
        XLog.d(TAG, "fake() called with: packageName = $packageName, processName = $processName")
        if (packageName == "com.google.android.webview") {
            XLog.d(TAG, "fake() called, ignore $packageName")
            emit(result = "skip", reason = "webview")
            return
        }
        if (processName.isBlank()) {
            XLog.w(TAG, "skip fake() for package without processName: $packageName")
            emit(result = "skip", reason = "blank_process")
            return
        }

        val profile = ModuleCompatRegistry.resolveProfile(packageName, processName, lpparam.classLoader)
        if (profile == null) {
            XLog.d(TAG, "skip fake() without profile for $packageName in process=$processName")
            emit(result = "skip", reason = "no_profile")
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
            emit(result = "ok", reason = "registration_only", pipelineCount = 0)
            return
        }

        // The Agoo click payload still needs result replacement on native Xiaomi devices.
        // Install this narrowly scoped hook before the device guard skips FakeDevice vendor shims.
        if (HookPipelineId.ALI_AGOO_ACCS in pipelines) {
            AgooClickDecryptHook.install(lpparam)
        }

        // ByteDance's MiPush provider gate is a Java-class capability check
        // (ToolUtils.isMiui resolves miui.os.Build). Property spoofing cannot provide a class,
        // and the guard below would otherwise skip every pipeline on genuine or spoofed Xiaomi
        // brand values, so this exact-predicate hook installs ahead of the guard as well.
        if (HookPipelineId.DOUYIN in pipelines) {
            DouyinMiuiGateHook.install(lpparam)
        }

        if (android.os.Build.BRAND.equals("Xiaomi", ignoreCase = true) || android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            XLog.i(TAG, "Zygisk spoofing detected (or native Xiaomi device) for $packageName, skipping FakeDevice pipelines")
            emit(result = "skip", reason = "xiaomi_device", pipelineCount = pipelines.size)
            return
        }

        LegacyHuaweiSignatureCompat.hook(lpparam)
        val distinctPipelines = pipelines.distinct()
        distinctPipelines.forEach { pipelineId ->
            createPipelineHook(pipelineId).fake(lpparam)
        }
        emit(result = "ok", reason = "pipelines_installed", pipelineCount = distinctPipelines.size)
    }
}

/** [BaseHook] adapter for [FakeDevice] to participate in the hook dispatch list. */
class FakeDeviceHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        if (param.processName.isBlank()) return
        if (param.packageName == "android" || param.packageName == "system") return
        FakeDevice.fake(param)
    }
}
