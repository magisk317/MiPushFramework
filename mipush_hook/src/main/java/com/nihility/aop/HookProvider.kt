package com.nihility.aop

import android.content.Context

/**
 * 现代化的 Hook 提供者接口
 *
 * 替代传统 AspectJ 织入，提供声明式、可组合的 Hook 定义方式。
 * 每个实现类定义一类特定的 Hook 行为，由 KSP 或运行时反射发现。
 *
 * 使用示例：
 * ```
 * class MiPushSystemHook : HookProvider {
 *     override val name = "miui-system-detection"
 *     override val priority = 100
 *
 *     override fun onHook(context: Context) {
 *         hookFieldIfPresent(
 *             "com.xiaomi.channel.commonutils.android.MIUIUtils",
 *             "isMIUI",
 *             true
 *         )
 *     }
 * }
 * ```
 *
 * ## 相比 AspectJ 的优势
 * - ✅ 无编译期织入开销
 * - ✅ 易读的声明式 API
 * - ✅ 支持优先级控制
 * - ✅ 易于单元测试和 Mock
 * - ✅ 完全兼容 Kotlin DSL
 */
interface HookProvider {
    /**
     * Hook 的唯一标识符
     * 用于日志记录、调试和去重
     */
    val name: String

    /**
     * Hook 的执行优先级
     * 值越大越先执行（类似于 interceptor 的顺序）
     * 默认 0
     */
    val priority: Int
        get() = 0

    /**
     * 当 App 初始化时调用此方法执行 Hook
     * 通常在 Startup Initializer 或 Application#onCreate 中被调用
     *
     * @param context 应用上下文
     * @throws Exception 若 Hook 执行失败可抛出异常，会被统一处理
     */
    @Throws(Exception::class)
    fun onHook(context: Context)
}
