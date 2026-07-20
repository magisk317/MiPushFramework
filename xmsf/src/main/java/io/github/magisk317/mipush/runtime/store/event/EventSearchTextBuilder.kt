package io.github.magisk317.mipush.runtime.store.event

import android.content.Context
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.Global

/**
 * 生成与记录列表 UI 对齐的可搜索文本快照。
 *
 * 背景:历史上搜索只打 `dev_info`(原始 thrift JSON),而用户眼里的可搜字段
 * (本地化应用名 / 通知标题 / 正文 / 状态摘要)大多是运行时派生、并不落库,
 * 导致"搜应用名 / 通道 / 标题 / 正文几乎必然无结果"。
 *
 * 这里在入库时一次性把这些展示字段拼进 [Event.searchText],搜索只打这一列,
 * 从根源上消除"搜索列 ≠ 展示字段"的错位。
 *
 * 字段来源:
 * - 包名(始终包含,保证"搜 com.tencent.mm"可命中)
 * - 本地化应用名(取不到就跳过)
 * - 事件标题([EventType.getTitle],通知为标题、其余多为应用名)
 * - 事件摘要/正文([EventType.getSummary],通知为正文、其余为状态文案如"注册")
 *
 * 加密消息在入库时若无 regSec 解不出标题/正文,则快照可能只含包名 + 应用名,
 * 这是预期行为(搜索本就不该期待命中加密原文)。
 */
object EventSearchTextBuilder {

    /**
     * 用全局 application context 生成快照(入库路径通常没有显式 Context)。
     * 取不到 context 时退化为仅包名。
     */
    fun build(type: EventType): String? {
        val context = Utils.getApplication() ?: return type.pkg?.takeIf { it.isNotBlank() }
        return build(context, type)
    }

    fun build(context: Context, type: EventType): String {
        // linkedSet:保序去重,避免 getTitle 回退成应用名时与包名段重复。
        val parts = LinkedHashSet<String>()

        val pkg = type.pkg
        if (!pkg.isNullOrBlank()) {
            parts.add(pkg)
            runCatching { Global.applicationNameCache().getAppName(context, pkg)?.toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != pkg }
                ?.let { parts.add(it) }
        }

        runCatching { type.getTitle(context).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { parts.add(it) }

        runCatching { type.getSummary(context)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { parts.add(it) }

        return parts.joinToString(separator = " ")
    }
}
