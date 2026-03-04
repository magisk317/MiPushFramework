package com.nihility.hook

import android.content.Context
import com.nihility.aop.HookProvider
import com.nihility.aop.HookUtils

/**
 * XMPP 服务器配置 Hook
 *
 * 配置 MiPush SDK 使用国内 XMPP 服务器，提高国内网络环境的连接稳定性。
 *
 * 目标：
 * - 获取 com.xiaomi.smack.ConnectionConfiguration 中的 XMPP_SERVER_CHINA_HOST_P 字段
 * - 调用 setXmppServerHost 方法设置为中国服务器
 *
 * 优化点：
 * - 国内用户到国内服务器的网络延迟更低
 * - 避免国际线路的不稳定性
 * - 提高 Push 消息送达率和及时性
 */
class XmppServerConfigurationHook : HookProvider {
    override val name: String = "xmpp-server-configuration"
    override val priority: Int = 80

    override fun onHook(context: Context) {
        // 尝试获取中国 XMPP 服务器地址
        val serverHost = HookUtils.getStaticField(
            className = "com.xiaomi.smack.ConnectionConfiguration",
            fieldName = "XMPP_SERVER_CHINA_HOST_P"
        ) as? String

        if (serverHost != null) {
            // 使用反射调用 setXmppServerHost 方法
            HookUtils.ifPresent("com.xiaomi.smack.ConnectionConfiguration") {
                runCatching {
                    val klass = Class.forName("com.xiaomi.smack.ConnectionConfiguration")
                    val method = klass.getDeclaredMethod("setXmppServerHost", String::class.java)
                    method.isAccessible = true
                    method.invoke(null, serverHost)
                    true
                }.getOrDefault(false)
            }
        }
    }
}
