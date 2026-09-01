package io.github.magisk317.mipush.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import io.github.magisk317.mipush.common.notification.ChannelNameEnricher
import io.github.magisk317.mipush.notification.policy.NotificationAppSettingsBlockSelector
import io.github.magisk317.mipush.notification.policy.NotificationDumpCommandContract
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade

private object RootLog {
    fun d(tag: String, message: String?) = Logger.withTag(tag).d { message.orEmpty() }
    fun w(tag: String, message: String?) = Logger.withTag(tag).w { message.orEmpty() }
    fun e(tag: String, message: String?, throwable: Throwable?) =
        if (throwable == null) Logger.withTag(tag).e { message.orEmpty() }
        else Logger.withTag(tag).e(throwable) { message.orEmpty() }
}

object NotificationRootFallback {
    private const val TAG = "RootNotificationHelper"
    private val SIMPLE_CHANNEL_PATTERN = Regex("""channelId=([^\s,]+).*?importance=(\d+)""")
    private val POLICY_MARKERS = listOf(
        "zenMode=",
        "condition://",
        "ConditionProvider",
        "EVERY_NIGHT_DEFAULT_RULE",
    )

    private var rootAvailable: Boolean? = null

    // dumpsys notification --noredact 导出全系统通道状态（避免 HyperOS 省略名）；一次抓取服务同屏查询。
    // 记录列表逐行查询会在极短时间内对同一/多个包发起大量查询，这里用短 TTL 缓存整份 dump，
    // 把 root 往返从"每行一次"压到"每个 TTL 窗口一次"，并按包缓存解析结果避免同屏重复解析。
    private const val DUMP_TTL_MS = 3_000L
    private data class DumpSnapshot(val capturedAt: Long, val output: String)
    private val dumpLock = Any()
    @Volatile private var dumpSnapshot: DumpSnapshot? = null
    private data class PackageIdentity(val packageName: String, val uid: Int?)
    private val channelParseCache =
        java.util.concurrent.ConcurrentHashMap<PackageIdentity, List<NotificationChannel?>>()
    private val groupParseCache =
        java.util.concurrent.ConcurrentHashMap<PackageIdentity, List<NotificationChannelGroup?>>()

    /** 返回当前有效的 dumpsys 快照；过期则刷新一次并清空解析缓存。返回 null 表示 root 取数失败。 */
    private fun currentDump(): String? {
        val now = System.currentTimeMillis()
        dumpSnapshot?.let { if (now - it.capturedAt < DUMP_TTL_MS) return it.output }
        synchronized(dumpLock) {
            val cached = dumpSnapshot
            val inner = System.currentTimeMillis()
            if (cached != null && inner - cached.capturedAt < DUMP_TTL_MS) return cached.output
            // Prefer --noredact: plain dumpsys ellipsizes cross-package channel names on HyperOS.
            val fresh = readNotificationServiceDump(::execDumpsys) ?: return null
            dumpSnapshot = DumpSnapshot(inner, fresh)
            channelParseCache.clear()
            groupParseCache.clear()
            return fresh
        }
    }

    internal fun readNotificationServiceDump(runCommand: (String) -> String?): String? =
        NotificationDumpCommandContract.readDump(
            runCommand = runCommand,
            isUsable = ::isUsableNotificationServiceDump,
        )

    private fun isUsableNotificationServiceDump(output: String): Boolean =
        output.contains("NotificationChannel{") ||
            output.contains("NotificationChannelGroup{") ||
            SIMPLE_CHANNEL_PATTERN.containsMatchIn(output)

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
        val result = AppRootAccessFacade.runRootCommand("id", timeoutMs = 3_000L)
        val available = result.isSuccess && result.stdoutText.contains("uid=0")
        rootAvailable = available
        RootLog.d(TAG, "root check: $available")
        if (!available && result.stderrText.isNotBlank()) {
            RootLog.d(TAG, "root check failed: ${result.stderrText}")
        }
        return available
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?,
        packageUid: Int? = null,
    ): NotificationChannel? {
        if (channelId.isNullOrEmpty()) return null
        val channels = getNotificationChannels(packageName, packageUid) ?: return null
        // 同一 channelId 可能存在于多个命名空间（xmsf / 目标 App），取最低 importance（最严格优先）
        return channels.filterNotNull()
            .filter { it.id == channelId }
            .minByOrNull { it.importance }
    }

    fun getNotificationChannels(
        packageName: String,
        packageUid: Int? = null,
    ): List<NotificationChannel?>? {
        // Newer Android/MIUI builds ignore "channels <pkg>" and dump the whole manager state.
        // Prefer scoped extraction from full dumpsys when available.
        val identity = PackageIdentity(packageName, packageUid)
        channelParseCache[identity]?.let {
            RootLog.d(TAG, "getNotificationChannels cache-hit pkg=$packageName uid=$packageUid count=${it.size}")
            return it
        }
        val output = currentDump() ?: return null
        val scoped = selectAppSettingsBlock(output, packageName, packageUid)
        val channels = parseChannels(scoped, packageName).filterNotNull()
        RootLog.d(
            TAG,
            "getNotificationChannels dumpsys pkg=$packageName uid=$packageUid " +
                "rawCount=${channels.size} scoped=${scoped !== output}",
        )
        // HyperOS may truncate channel names in the AppSettings block (e.g. "即时消...").
        // 复用 common 中的解析器，从 effectiveNotificationChannel 合并完整名称。
        val enriched = ChannelNameEnricher.enrich(packageName, channels, output, packageUid)
        // 去重：同一 channelId 保留最低 importance（用户在 App 命名空间禁用的通道优先于 xmsf 命名空间的副本）
        val deduped = enriched
            .groupBy { it.id }
            .map { (_, group) -> group.minByOrNull { it.importance } }
            .sortedBy { it?.id }
        channelParseCache[identity] = deduped
        return deduped
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String,
        packageUid: Int? = null,
    ): NotificationChannelGroup? {
        val groups = getNotificationChannelGroups(packageName, packageUid) ?: return null
        return groups.filterNotNull().firstOrNull { it.id == groupId }
    }

    fun getNotificationChannelGroups(
        packageName: String,
        packageUid: Int? = null,
    ): List<NotificationChannelGroup?>? {
        val identity = PackageIdentity(packageName, packageUid)
        groupParseCache[identity]?.let {
            RootLog.d(TAG, "getNotificationChannelGroups cache-hit pkg=$packageName uid=$packageUid count=${it.size}")
            return it
        }
        val output = currentDump() ?: return null
        val scoped = selectAppSettingsBlock(output, packageName, packageUid)
        val groups = parseGroups(scoped, packageName)
        RootLog.d(TAG, "getNotificationChannelGroups dumpsys pkg=$packageName uid=$packageUid count=${groups.size}")
        groupParseCache[identity] = groups
        return groups
    }

    internal fun selectAppSettingsBlock(
        output: String,
        packageName: String,
        packageUid: Int?,
    ): String = NotificationAppSettingsBlockSelector.select(
        notificationDump = output,
        packageName = packageName,
        preferredUid = packageUid,
    ) ?: output

    fun areNotificationsEnabled(packageName: String): Boolean? {
        val output = execDumpsys("dumpsys notification policy") ?: return null
        val pattern = Regex("""$packageName.*?enqueue.*?=(true|false)""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(output)?.groupValues?.get(1)?.toBoolean()
    }

    private fun execDumpsys(command: String): String? {
        val result = AppRootAccessFacade.runRootCommand(command)
        if (!result.isSuccess) {
            val error = result.stderrText.ifBlank { result.stdoutText }.ifBlank { "unknown" }
            RootLog.w(TAG, "dumpsys failed: $command, exit=${result.exitCode}, err=$error")
            return null
        }
        return result.stdoutText
    }

    internal fun parseChannels(output: String, packageName: String): List<NotificationChannel?> {
        val channels = mutableListOf<NotificationChannel?>()

        extractBalancedBlocks(output, "NotificationChannel").forEach { raw ->
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
                Regex("""\bmName='([^']*)'"""),
                Regex("""\bmName="([^"]*)""""),
                Regex("""\bmName=([^,}]*)"""),
                Regex("""\bname='([^']*)'"""),
                Regex("""\bname="([^"]*)""""),
                Regex("""\bname=([^,}]*)"""),
            ).orEmpty()
            if (isPolicyChannel(raw, channelId, name)) {
                RootLog.d(TAG, "skip policy channel while parsing $packageName: $channelId")
                return@forEach
            }
            runCatching {
                val channel = NotificationChannel(channelId, name.ifEmpty { channelId }, importance)
                channels.add(channel)
            }.onFailure {
                RootLog.w(TAG, "parse channel failed: $channelId, ${it.message}")
            }
        }

        if (channels.isEmpty()) {
            SIMPLE_CHANNEL_PATTERN.findAll(output).forEach { match ->
                val channelId = match.groupValues[1]
                val importance = match.groupValues[2].toIntOrNull() ?: NotificationManager.IMPORTANCE_DEFAULT
                val raw = surroundingText(output, match.range)
                if (isPolicyChannel(raw, channelId, channelId)) {
                    RootLog.d(TAG, "skip simple policy channel while parsing $packageName: $channelId")
                    return@forEach
                }
                runCatching {
                    val channel = NotificationChannel(channelId, channelId, importance)
                    channels.add(channel)
                }.onFailure {
                    RootLog.w(TAG, "parse simple channel failed: $channelId, ${it.message}")
                }
            }
        }

        RootLog.d(TAG, "parsed ${channels.size} channels for $packageName")
        return channels
    }

    internal fun parseGroups(output: String, packageName: String): List<NotificationChannelGroup?> {
        val groups = mutableListOf<NotificationChannelGroup?>()

        extractBalancedBlocks(output, "NotificationChannelGroup").forEach { raw ->
            val groupId = firstFieldValue(
                raw,
                Regex("""\bmId='([^']*)'"""),
                Regex("""\bid=([^\s,}]+)""")
            ) ?: return@forEach
            val name = firstFieldValue(
                raw,
                Regex("""\bmName='([^']*)'"""),
                Regex("""\bmName="([^"]*)""""),
                Regex("""\bmName=([^,}]*)"""),
                Regex("""\bname='([^']*)'"""),
                Regex("""\bname="([^"]*)""""),
                Regex("""\bname=([^,}]*)"""),
            ).orEmpty()
            if (isPolicyChannel(raw, groupId, name)) {
                RootLog.d(TAG, "skip policy channel group while parsing $packageName: $groupId")
                return@forEach
            }
            runCatching {
                val group = NotificationChannelGroup(groupId, name.ifEmpty { groupId })
                groups.add(group)
            }.onFailure {
                RootLog.w(TAG, "parse group failed: $groupId, ${it.message}")
            }
        }

        RootLog.d(TAG, "parsed ${groups.size} groups for $packageName")
        return groups
    }

    /**
     * Extract `Type{...}` blocks with nested braces (HyperOS vibration effect dumps nest `{}`).
     * The old `[^}]*` regex stopped at the first `}` and could drop later fields on some ROMs.
     */
    internal fun extractBalancedBlocks(output: String, type: String): List<String> {
        val prefix = "$type{"
        val blocks = mutableListOf<String>()
        var index = 0
        while (index < output.length) {
            val start = output.indexOf(prefix, index)
            if (start < 0) break
            val openBrace = start + type.length
            if (openBrace >= output.length || output[openBrace] != '{') {
                index = start + prefix.length
                continue
            }
            var depth = 0
            var cursor = openBrace
            while (cursor < output.length) {
                when (output[cursor]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            blocks += output.substring(start, cursor + 1)
                            index = cursor + 1
                            break
                        }
                    }
                }
                cursor++
            }
            if (depth != 0) {
                // Unbalanced trailing dump: keep a conservative single-level slice.
                val fallbackEnd = output.indexOf('}', openBrace + 1).let { if (it < 0) output.length else it + 1 }
                blocks += output.substring(start, fallbackEnd)
                index = fallbackEnd
            }
        }
        return blocks
    }

    private fun firstFieldValue(raw: String, vararg patterns: Regex): String? {
        for (pattern in patterns) {
            val value = pattern.find(raw)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.trim('\'', '"')
                ?.takeIf { it.isNotEmpty() && it != "null" }
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
