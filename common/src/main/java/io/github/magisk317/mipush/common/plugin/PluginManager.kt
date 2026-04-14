package io.github.magisk317.mipush.common.plugin

import android.content.ComponentName
import io.github.aakira.napier.Napier

/**
 * 提供对于推送提供程序的检索功能
 */
object PluginManager {
    private const val TAG = "PluginManager"
    const val ARG_COMPONENT = "moe.yuuta.mipush.plugin.ARG_COMPONENT"

    /**
     * 检索该推送提供程序的推送管理服务是否可用且已启用。只有已启用的服务才能使用 API
     * @param name 对方服务 ComponentName
     * @return 是否启用
     */
    @JvmStatic
    fun isEnabled(name: ComponentName?): Boolean {
        if (name == null) {
            Napier.w("isEnabled called with null ComponentName", tag = TAG)
            return false
        }
        // Plugin system not yet implemented — default to enabled for backward compatibility.
        // When the plugin registry is implemented, this should query actual enabled state.
        Napier.d("isEnabled: ${name.flattenToShortString()} (default=true, plugin registry not implemented)", tag = TAG)
        return true
    }

    /**
     * 检查该组件是否可用（配置正确）
     */
    @JvmStatic
    fun verifyComponent(name: ComponentName): Boolean {
        // Plugin system not yet implemented — default to valid for backward compatibility.
        Napier.d("verifyComponent: ${name.flattenToShortString()} (default=true, plugin registry not implemented)", tag = TAG)
        return true
    }
}
