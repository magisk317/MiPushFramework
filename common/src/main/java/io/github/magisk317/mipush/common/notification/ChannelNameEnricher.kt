package io.github.magisk317.mipush.common.notification

import android.app.NotificationChannel

/**
 * Resolves full notification-channel names from a notification service dump.
 *
 * HyperOS may ellipsize names returned for foreign packages while active notification records
 * retain the full effective channel. Root execution stays in the owning runtime module; this
 * object only parses and merges supplied text.
 */
object ChannelNameEnricher {
    fun enrich(
        packageName: String,
        channels: List<NotificationChannel>,
        notificationDump: String,
        packageUid: Int? = null,
    ): List<NotificationChannel> {
        if (channels.isEmpty() || notificationDump.isBlank()) return channels
        val names = namesForPackage(packageName, notificationDump, packageUid)
        if (names.isEmpty()) return channels

        return channels.map { channel ->
            val candidate = resolveName(packageName, channel.id, names).orEmpty()
            val current = channel.name?.toString().orEmpty()
            if (shouldPrefer(current, candidate)) {
                channel.setName(candidate)
            }
            channel
        }
    }

    /**
     * Looks up a channel display name by exact id first, then by equivalent managed-channel ids
     * such as `mipush|pkg|src` vs `ch_pkg_src`.
     */
    fun resolveName(
        packageName: String,
        channelId: String,
        namesById: Map<String, String>,
    ): String? {
        namesById[channelId]?.takeIf { it.isNotBlank() }?.let { return it }
        var best: String? = null
        namesById.forEach { (id, name) ->
            if (name.isBlank()) return@forEach
            if (!channelIdsEquivalent(packageName, id, channelId)) return@forEach
            val current = best
            if (current == null || shouldPrefer(current, name)) {
                best = name
            }
        }
        return best
    }

    internal fun hasEllipsizedNames(channels: List<NotificationChannel>): Boolean =
        channels.any { looksEllipsized(it.name?.toString().orEmpty()) }

    internal fun namesForPackage(
        packageName: String,
        notificationDump: String,
        packageUid: Int? = null,
    ): Map<String, String> {
        val scoped = NotificationAppSettingsBlockSelector.select(
            notificationDump = notificationDump,
            packageName = packageName,
            preferredUid = packageUid,
        ) ?: return emptyMap()
        val resolved = parseNames(scoped).toMutableMap()
        val effective = parseEffectiveChannelNames(notificationDump, packageName, packageUid)

        effective.forEach { (effectiveId, effectiveName) ->
            val matchingIds = resolved.keys.filter { listedId ->
                channelIdsEquivalent(packageName, listedId, effectiveId)
            }
            if (matchingIds.isEmpty()) {
                resolved[effectiveId] = effectiveName
            } else {
                matchingIds.forEach { listedId ->
                    val current = resolved[listedId].orEmpty()
                    if (shouldPrefer(current, effectiveName)) {
                        resolved[listedId] = effectiveName
                    }
                }
            }
        }
        return resolved
    }

    internal fun parseNames(output: String): Map<String, String> {
        val names = linkedMapOf<String, String>()
        extractBalancedBlocks(output, "NotificationChannel").forEach { raw ->
            val id = firstFieldValue(
                raw,
                Regex("""\bmId='([^']*)'"""),
                Regex("""\bid=([^\s,}]+)"""),
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
            if (name.isNotBlank()) {
                val current = names[id].orEmpty()
                if (current.isEmpty() || shouldPrefer(current, name)) {
                    names[id] = name
                }
            }
        }
        return names
    }

    internal fun parseEffectiveChannelNames(
        output: String,
        packageName: String,
        packageUid: Int? = null,
    ): Map<String, String> {
        val names = linkedMapOf<String, String>()
        extractEffectiveChannelBlocks(output).forEach { located ->
            val id = firstFieldValue(
                located.value,
                Regex("""\bmId='([^']*)'"""),
                Regex("""\bid=([^\s,}]+)"""),
            ) ?: return@forEach
            if (!effectiveEntryBelongsToPackage(output, located.start, packageName, id, packageUid)) {
                return@forEach
            }
            val name = firstFieldValue(
                located.value,
                Regex("""\bmName='([^']*)'"""),
                Regex("""\bmName="([^"]*)""""),
                Regex("""\bmName=([^,}]*)"""),
            ).orEmpty()
            if (name.isNotBlank()) {
                val current = names[id].orEmpty()
                if (current.isEmpty() || shouldPrefer(current, name)) {
                    names[id] = name
                }
            }
        }
        return names
    }

    internal fun channelIdsEquivalent(
        packageName: String,
        first: String,
        second: String,
    ): Boolean {
        if (first == second) return true
        val firstSource = managedChannelSource(packageName, first) ?: return false
        val secondSource = managedChannelSource(packageName, second) ?: return false
        return firstSource == secondSource
    }

    private fun managedChannelSource(packageName: String, channelId: String): String? {
        val prefixes = listOf(
            "mipush|" + packageName + "|",
            "mipush_" + packageName + "_",
            "ch_" + packageName + "_",
        )
        prefixes.forEach { prefix ->
            if (channelId.startsWith(prefix)) {
                return channelId.removePrefix(prefix).takeIf(String::isNotEmpty)
            }
        }
        return null
    }

    private fun effectiveEntryBelongsToPackage(
        output: String,
        effectiveStart: Int,
        packageName: String,
        channelId: String,
        packageUid: Int?,
    ): Boolean {
        if (managedChannelSource(packageName, channelId) != null ||
            channelId == "ch_" + packageName
        ) {
            return true
        }

        val recordStart = output.lastIndexOf("NotificationRecord", effectiveStart)
        if (recordStart < 0) return false
        val context = output.substring(recordStart, effectiveStart)
        val packagePattern = Regex("""\bpkg=""" + Regex.escape(packageName) + """(?=[\s,}:])""")
        if (!packagePattern.containsMatchIn(context)) return false
        val expectedUserId = packageUid?.div(PER_USER_RANGE)
        if (expectedUserId == null) return true
        val userId = Regex("""\buser=UserHandle\{(\d+)\}""")
            .find(context)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return false
        return userId == expectedUserId
    }

    private fun shouldPrefer(current: String, candidate: String): Boolean {
        if (candidate.isBlank() || candidate == current) return false
        val currentEllipsized = looksEllipsized(current)
        val candidateEllipsized = looksEllipsized(candidate)
        if (currentEllipsized != candidateEllipsized) {
            return currentEllipsized
        }
        return candidate.length > current.length
    }

    private fun looksEllipsized(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.endsWith("...") || trimmed.endsWith("…")
    }

    private data class LocatedBlock(
        val start: Int,
        val value: String,
    )

    private const val PER_USER_RANGE = 100_000

    private fun extractEffectiveChannelBlocks(output: String): List<LocatedBlock> {
        val marker = "effectiveNotificationChannel=NotificationChannel{"
        val channelOffset = "effectiveNotificationChannel=".length
        val blocks = mutableListOf<LocatedBlock>()
        var index = 0

        while (index < output.length) {
            val markerStart = output.indexOf(marker, index)
            if (markerStart < 0) break
            val channelStart = markerStart + channelOffset
            val openBrace = channelStart + "NotificationChannel".length
            val end = findBalancedBlockEnd(output, openBrace)
            if (end == null) {
                index = markerStart + marker.length
                continue
            }
            blocks += LocatedBlock(
                start = markerStart,
                value = output.substring(channelStart, end + 1),
            )
            index = end + 1
        }
        return blocks
    }

    private fun extractBalancedBlocks(output: String, type: String): List<String> {
        val prefix = type + "{"
        val blocks = mutableListOf<String>()
        var index = 0

        while (index < output.length) {
            val start = output.indexOf(prefix, index)
            if (start < 0) break
            val openBrace = start + type.length
            val end = findBalancedBlockEnd(output, openBrace)
            if (end == null) {
                index = start + prefix.length
                continue
            }
            blocks += output.substring(start, end + 1)
            index = end + 1
        }
        return blocks
    }

    private fun findBalancedBlockEnd(output: String, openBrace: Int): Int? {
        if (openBrace !in output.indices || output[openBrace] != '{') return null
        var depth = 0
        for (index in openBrace until output.length) {
            when (output[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun firstFieldValue(raw: String, vararg patterns: Regex): String? {
        patterns.forEach { pattern ->
            val value = pattern.find(raw)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.trim('\'', '"')
                ?.takeIf { it.isNotEmpty() && it != "null" }
            if (value != null) return value
        }
        return null
    }
}
