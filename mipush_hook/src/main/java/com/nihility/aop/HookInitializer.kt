package com.nihility.aop

import android.content.Context
import android.util.Log
import androidx.startup.Initializer

/**
 * 现代化的 Hook 初始化器
 *
 * 通过发现并执行所有 [HookProvider] 实现，实现 AOP 的现代化方案。
 * 替代传统 AspectJ 织入，具有更好的性能和可维护性。
 *
 * ## 工作流程
 * 1. App 启动时由 Startup 框架自动调用
 * 2. 通过反射发现所有 HookProvider 实现
 * 3. 按优先级排序执行各个 Hook
 * 4. 统一处理异常，避免单个 Hook 失败导致应用崩溃
 *
 * ## 配置方式
 * 在 AndroidManifest.xml 中声明：
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false">
 *     <meta-data
 *         android:name="com.nihility.aop.HookInitializer"
 *         android:value="androidx.startup" />
 * </provider>
 * ```
 *
 * ## 优势对比
 * - ✅ 完全替代 AspectJ 织入，编译速度快
 * - ✅ 支持多个独立的 Hook 提供者
 * - ✅ 优先级控制，易于调试
 * - ✅ 异常隔离，单个 Hook 失败不影响整体
 * - ✅ 调试日志完整
 */
class HookInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        try {
            executeHooks(context)
        } catch (e: Exception) {
            Log.e(TAG, "HookInitializer failed", e)
            // 不抛出异常，避免影响应用启动
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private fun executeHooks(context: Context) {
        val startTime = System.currentTimeMillis()
        val providers = discoverHookProviders()

        if (providers.isEmpty()) {
            Log.d(TAG, "No HookProviders found")
            return
        }

        // 按优先级降序排列（优先级高的先执行）
        val sortedProviders = providers.sortedByDescending { it.priority }

        Log.d(TAG, "Executing ${sortedProviders.size} hooks in priority order")

        var successCount = 0
        var failureCount = 0

        for (provider in sortedProviders) {
            try {
                provider.onHook(context)
                Log.d(TAG, "✓ Hook executed: ${provider.name} (priority=${provider.priority})")
                successCount++
            } catch (e: Exception) {
                failureCount++
                Log.w(TAG, "✗ Hook failed: ${provider.name}", e)
                // 继续执行后续 Hook，不中断
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Hook execution completed: $successCount success, $failureCount failed in ${duration}ms")
    }

    /**
     * 发现项目中所有的 HookProvider 实现
     *
     * 使用反射扫描类路径，查找所有实现了 HookProvider 接口的类。
     * 在实际项目中，可以使用 KSP 或 APT 进行编译期代码生成优化。
     */
    private fun discoverHookProviders(): List<HookProvider> {
        return listOfNotNull(
            // 系统检测 Hook
            runCatching {
                Class.forName("com.nihility.hook.MiUISystemHook")
                    .getDeclaredConstructor()
                    .newInstance() as? HookProvider
            }.getOrNull(),

            // IMEI 追踪规避 Hook
            runCatching {
                Class.forName("com.nihility.hook.DeviceTrackingAvoidanceHook")
                    .getDeclaredConstructor()
                    .newInstance() as? HookProvider
            }.getOrNull(),

            // XMPP 服务器配置 Hook
            runCatching {
                Class.forName("com.nihility.hook.XmppServerConfigurationHook")
                    .getDeclaredConstructor()
                    .newInstance() as? HookProvider
            }.getOrNull(),
        )
    }

    private companion object {
        private const val TAG = "HookInitializer"
    }
}
