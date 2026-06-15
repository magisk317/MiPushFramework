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
        return channels.filterNotNull().firstOrNull { it.id == channelId }
    }

    fun getNotificationChannels(packageName: String): List<NotificationChannel?>? {
        val output = execDumpsys("dumpsys notification channels $packageName") ?: return null
        return parseChannels(output, packageName)
    }

    fun getNotificationChannelGroup(packageName: String, groupId: String): NotificationChannelGroup? {
        val groups = getNotificationChannelGroups(packageName) ?: return null
        return groups.filterNotNull().firstOrNull { it.id == groupId }
    }

    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? {
        val output = execDumpsys("dumpsys notification groups $packageName") ?: return null
        return parseGroups(output, packageName)
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
