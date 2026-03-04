package com.nihility.hook

import android.content.Context
import com.nihility.aop.HookProvider
import com.nihility.aop.HookUtils

/**
 * 设备追踪规避 Hook
 *
 * 防止 MiPush SDK 收集设备标识符信息。
 *
 * 目标：
 * - 修改 com.xiaomi.channel.commonutils.android.DeviceInfo.sCachedIMEI 字段
 * - 将真实 IMEI 替换为空字符串，切断设备追踪链路
 *
 * 隐私保护：
 * 此 Hook 有助于保护用户隐私，防止跨应用的设备指纹追踪。
 */
class DeviceTrackingAvoidanceHook : HookProvider {
    override val name: String = "device-tracking-avoidance"
    override val priority: Int = 90

    override fun onHook(context: Context) {
        HookUtils.hookStaticField(
            className = "com.xiaomi.channel.commonutils.android.DeviceInfo",
            fieldName = "sCachedIMEI",
            value = ""
        )
    }
}
