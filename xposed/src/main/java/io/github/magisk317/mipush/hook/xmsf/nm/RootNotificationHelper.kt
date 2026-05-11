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

    private fun parseChannels(output: String, packageName: String): List<NotificationChannel?> {
        val channels = mutableListOf<NotificationChannel?>()
        val channelPattern = Regex(
            """NotificationChannel\{.*?id=([^\s,}]+).*?importance=(\d+).*?name=([^}]*)\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val simplePattern = Regex("""channelId=([^\s,]+).*?importance=(\d+)""")

        channelPattern.findAll(output).forEach { match ->
            val channelId = match.groupValues[1]
            val importance = match.groupValues[2].toIntOrNull() ?: NotificationManager.IMPORTANCE_DEFAULT
            val name = match.groupValues[3].trim()
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

    private fun parseGroups(output: String, packageName: String): List<NotificationChannelGroup?> {
        val groups = mutableListOf<NotificationChannelGroup?>()
        val groupPattern = Regex(
            """NotificationChannelGroup\{.*?id=([^\s,}]+).*?name=([^}]*)\}""",
            RegexOption.DOT_MATCHES_ALL
        )

        groupPattern.findAll(output).forEach { match ->
            val groupId = match.groupValues[1]
            val name = match.groupValues[2].trim()
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
}
