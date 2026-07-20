package io.github.magisk317.mipush.hook.xmsf.nm

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.util.BoundedRootRunner

object RootNotificationHelper {
    private const val TAG = "RootNotificationHelper"
    private val POLICY_MARKERS = listOf(
        "zenMode=",
        "condition://",
        "ConditionProvider",
        "EVERY_NIGHT_DEFAULT_RULE",
    )

    private var rootAvailable: Boolean? = null

    // dumpsys notification 导出的是全系统所有包的通道状态；一次抓取即可服务同屏内所有包的查询。
    // 记录列表逐行查询会在极短时间内对同一/多个包发起大量查询，这里用短 TTL 缓存整份 dump，
    // 把 root 往返从"每行一次"压到"每个 TTL 窗口一次"，并按包缓存解析结果避免同屏重复解析。
    private const val DUMP_TTL_MS = 3_000L
    private data class DumpSnapshot(val capturedAt: Long, val output: String)
    private val dumpLock = Any()
    @Volatile private var dumpSnapshot: DumpSnapshot? = null
    private val channelParseCache = java.util.concurrent.ConcurrentHashMap<String, List<NotificationChannel?>>()
    private val groupParseCache = java.util.concurrent.ConcurrentHashMap<String, List<NotificationChannelGroup?>>()

    /** 返回当前有效的 dumpsys 快照；过期则刷新一次并清空解析缓存。返回 null 表示 root 取数失败。 */
    private fun currentDump(): String? {
        val now = System.currentTimeMillis()
        dumpSnapshot?.let { if (now - it.capturedAt < DUMP_TTL_MS) return it.output }
        synchronized(dumpLock) {
            val cached = dumpSnapshot
            val inner = System.currentTimeMillis()
            if (cached != null && inner - cached.capturedAt < DUMP_TTL_MS) return cached.output
            val fresh = execDumpsys("dumpsys notification") ?: return null
            dumpSnapshot = DumpSnapshot(inner, fresh)
            channelParseCache.clear()
            groupParseCache.clear()
            return fresh
        }
    }

    /** 通道发生写操作（创建/删除）后可调用，丢弃缓存避免读到旧状态。 */
    fun invalidateCache() {
        synchronized(dumpLock) {
            dumpSnapshot = null
            channelParseCache.clear()
            groupParseCache.clear()
        }
    }

    fun isRootAvailable(): Boolean {
        rootAvailable?.let { return it }
        val result = BoundedRootRunner.run("id", timeoutMs = 3_000L)
        val available = result.isSuccess && result.stdout.contains("uid=0")
        rootAvailable = available
        XLog.d(TAG, "root check: $available")
        if (!available && result.stderr.isNotBlank()) {
            XLog.d(TAG, "root check failed: ${result.stderr}")
        }
        return available
    }

    fun getNotificationChannel(packageName: String, channelId: String?): NotificationChannel? {
        if (channelId.isNullOrEmpty()) return null
        val channels = getNotificationChannels(packageName) ?: return null
        // 同一 channelId 可能存在于多个命名空间（xmsf / 目标 App），取最低 importance（最严格优先）
        return channels.filterNotNull()
            .filter { it.id == channelId }
            .minByOrNull { it.importance }
    }

    fun getNotificationChannels(packageName: String): List<NotificationChannel?>? {
        // Newer Android/MIUI builds ignore "channels <pkg>" and dump the whole manager state.
        // Prefer scoped extraction from full dumpsys when available.
        channelParseCache[packageName]?.let {
            XLog.d(TAG, "getNotificationChannels cache-hit pkg=$packageName count=${it.size}")
            return it
        }
        val output = currentDump() ?: return null
        val scoped = extractAppSettingsBlock(output, packageName) ?: output
        val channels = parseChannels(scoped, packageName)
        XLog.d(TAG, "getNotificationChannels dumpsys pkg=$packageName rawCount=${channels.size} scoped=${scoped !== output}")
        // 去重：同一 channelId 保留最低 importance（用户在 App 命名空间禁用的通道优先于 xmsf 命名空间的副本）
        val deduped = channels.filterNotNull()
            .groupBy { it.id }
            .map { (_, group) -> group.minByOrNull { it.importance } }
            .sortedBy { it?.id }
        channelParseCache[packageName] = deduped
        return deduped
    }

    fun getNotificationChannelGroup(packageName: String, groupId: String): NotificationChannelGroup? {
        val groups = getNotificationChannelGroups(packageName) ?: return null
        return groups.filterNotNull().firstOrNull { it.id == groupId }
    }

    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? {
        groupParseCache[packageName]?.let {
            XLog.d(TAG, "getNotificationChannelGroups cache-hit pkg=$packageName count=${it.size}")
            return it
        }
        val output = currentDump() ?: return null
        val scoped = extractAppSettingsBlock(output, packageName) ?: output
        val groups = parseGroups(scoped, packageName)
        XLog.d(TAG, "getNotificationChannelGroups dumpsys pkg=$packageName count=${groups.size}")
        groupParseCache[packageName] = groups
        return groups
    }

    private fun extractAppSettingsBlock(output: String, packageName: String): String? {
        val marker = "AppSettings: $packageName "
        val start = output.indexOf(marker)
        if (start < 0) return null
        val next = output.indexOf("AppSettings: ", start + marker.length)
        return if (next > start) output.substring(start, next) else output.substring(start)
    }

    fun areNotificationsEnabled(packageName: String): Boolean? {
        val output = execDumpsys("dumpsys notification policy") ?: return null
        val pattern = Regex("""$packageName.*?enqueue.*?=(true|false)""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(output)?.groupValues?.get(1)?.toBoolean()
    }

    private fun execDumpsys(command: String): String? {
        val result = BoundedRootRunner.run(command)
        if (!result.isSuccess) {
            val error = result.stderr.ifBlank { result.stdout }.ifBlank { "unknown" }
            XLog.w(TAG, "dumpsys failed: $command, exit=${result.exitCode}, err=$error")
            return null
        }
        return result.stdout
    }

    internal fun parseChannels(output: String, packageName: String): List<NotificationChannel?> {
        val channels = mutableListOf<NotificationChannel?>()
        val simplePattern = Regex("""channelId=([^\s,]+).*?importance=(\d+)""")

        notificationBlockPattern("NotificationChannel").findAll(output).forEach { match ->
            val raw = match.value
            val channelId = firstFieldValue(
                raw,
                Regex("""\bmId='([^']*)'"""),
                Regex("""\bid=([^\s,}]+)""")
            ) ?: return@forEach
            val importance = firstFieldValue(
                raw,
                Regex("""\bmImportance=(\d+)"""),
                Regex("""\bimportance=(\d+)""")
            )?.toIntOrNull() ?: NotificationManager.IMPORTANCE_DEFAULT
            val name = firstFieldValue(
                raw,
                Regex("""\bmName=([^,}]*)"""),
                Regex("""\bname=([^,}]*)""")
            ).orEmpty()
            if (isPolicyChannel(raw, channelId, name)) {
                XLog.d(TAG, "skip policy channel while parsing $packageName: $channelId")
                return@forEach
            }
            try {
                val channel = NotificationChannel(channelId, name.ifEmpty { channelId }, importance)
                channels.add(channel)
            } catch (e: Exception) {
                XLog.w(TAG, "parse channel failed: $channelId, ${e.message}")
            }
        }

        if (channels.isEmpty()) {
            simplePattern.findAll(output).forEach { match ->
                val channelId = match.groupValues[1]
                val importance = match.groupValues[2].toIntOrNull() ?: NotificationManager.IMPORTANCE_DEFAULT
                val raw = surroundingText(output, match.range)
                if (isPolicyChannel(raw, channelId, channelId)) {
                    XLog.d(TAG, "skip simple policy channel while parsing $packageName: $channelId")
                    return@forEach
                }
                try {
                    val channel = NotificationChannel(channelId, channelId, importance)
                    channels.add(channel)
                } catch (e: Exception) {
                    XLog.w(TAG, "parse simple channel failed: $channelId, ${e.message}")
                }
            }
        }

        XLog.d(TAG, "parsed ${channels.size} channels for $packageName")
        return channels
    }

    internal fun parseGroups(output: String, packageName: String): List<NotificationChannelGroup?> {
        val groups = mutableListOf<NotificationChannelGroup?>()

        notificationBlockPattern("NotificationChannelGroup").findAll(output).forEach { match ->
            val raw = match.value
            val groupId = firstFieldValue(
                raw,
                Regex("""\bmId='([^']*)'"""),
                Regex("""\bid=([^\s,}]+)""")
            ) ?: return@forEach
            val name = firstFieldValue(
                raw,
                Regex("""\bmName=([^,}]*)"""),
                Regex("""\bname=([^,}]*)""")
            ).orEmpty()
            if (isPolicyChannel(raw, groupId, name)) {
                XLog.d(TAG, "skip policy channel group while parsing $packageName: $groupId")
                return@forEach
            }
            try {
                val group = NotificationChannelGroup(groupId, name.ifEmpty { groupId })
                groups.add(group)
            } catch (e: Exception) {
                XLog.w(TAG, "parse group failed: $groupId, ${e.message}")
            }
        }

        XLog.d(TAG, "parsed ${groups.size} groups for $packageName")
        return groups
    }

    private fun notificationBlockPattern(type: String): Regex =
        Regex("""$type\{[^}]*\}""", RegexOption.DOT_MATCHES_ALL)

    private fun firstFieldValue(raw: String, vararg patterns: Regex): String? {
        for (pattern in patterns) {
            val value = pattern.find(raw)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.trim('\'', '"')
                ?.takeIf { it.isNotEmpty() }
            if (value != null) return value
        }
        return null
    }

    private fun surroundingText(output: String, range: IntRange): String {
        val start = (range.first - 200).coerceAtLeast(0)
        val end = (range.last + 200).coerceAtMost(output.lastIndex)
        if (start > end) return ""
        return output.substring(start, end + 1)
    }

    private fun isPolicyChannel(raw: String, id: String, name: String): Boolean {
        val text = "$raw $id $name"
        return POLICY_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) }
    }
}
