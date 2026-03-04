package com.nihility.hook

import android.content.Context
import com.nihility.aop.HookProvider
import com.nihility.aop.HookUtils

/**
 * MIUI 系统检测伪装 Hook
 *
 * 针对 MiPush SDK 中的 MIUI 系统检测进行伪装，
 * 使应用能够在非 MIUI 设备或定制 ROM 上正常运行。
 *
 * 目标：
 * - 修改 com.xiaomi.channel.commonutils.android.MIUIUtils.isMIUI 字段
 * - 伪装系统为 MIUI，绕过 SDK 的系统检查
 */
class MiUISystemHook : HookProvider {
    override val name: String = "miui-system-detection"
    override val priority: Int = 100  // 优先级最高，在其他 Hook 之前执行

    override fun onHook(context: Context) {
        HookUtils.hookStaticField(
            className = "com.xiaomi.channel.commonutils.android.MIUIUtils",
            fieldName = "isMIUI",
            value = true
        )
    }
}
