package io.github.magisk317.mipush.notification.policy

/**
 * Platform-neutral notification progress/countdown text parsing.
 *
 * Extracts progress percentages, countdown durations, and contextual hints
 * from notification title/content text.
 */
object NotificationProgressTextPolicy {
    const val NO_PROGRESS = -1

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
    private const val MAX_MINUTES = 1_440L
    private const val MAX_HOURS = 72L
    private const val MAX_SECONDS = 3_600L

    private val progressPattern = Regex("(\\d{1,3})\\s*%")
    private val minutesPattern = durationPattern("分钟|min|mins|minute|minutes")
    private val hoursPattern = durationPattern("小时|hour|hours|hr|hrs")
    private val secondsPattern = durationPattern("秒|sec|second|seconds")
    private val clockPattern = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)")

    fun extractProgressPercent(text: String?): Int {
        val match = progressPattern.find(text.orEmpty()) ?: return NO_PROGRESS
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 } ?: NO_PROGRESS
    }

    fun extractCountdownMillis(title: String?, content: String?): Long {
        return extractDurationMillis("${title.orEmpty()} ${content.orEmpty()}")
    }

    fun extractDurationMillis(text: String?): Long {
        val source = text.orEmpty()
        durationMillis(source, minutesPattern, MAX_MINUTES, MILLIS_PER_MINUTE)?.let { return it }
        durationMillis(source, hoursPattern, MAX_HOURS, MILLIS_PER_HOUR)?.let { return it }
        durationMillis(source, secondsPattern, MAX_SECONDS, MILLIS_PER_SECOND)?.let { return it }
        return clockDurationMillis(source)
    }

    fun resolveAlertHint(title: String?, content: String?): String? {
        val text = "${title.orEmpty()} ${content.orEmpty()}"
        return when {
            text.contains("闹钟") || text.contains("alarm", ignoreCase = true) -> "闹钟"
            text.contains("提醒") || text.contains("reminder", ignoreCase = true) -> "提醒"
            text.contains("待办") || text.contains("todo", ignoreCase = true) -> "待办"
            text.contains("会议") || text.contains("meeting", ignoreCase = true) -> "会议"
            text.contains("倒计时") || text.contains("countdown", ignoreCase = true) -> "倒计时"
            else -> null
        }
    }

    fun resolveProgressHint(title: String?, content: String?): String? {
        val text = "${title.orEmpty()} ${content.orEmpty()}"
        return when {
            text.contains("下载") || text.contains("download", ignoreCase = true) -> "下载中"
            text.contains("上传") || text.contains("upload", ignoreCase = true) -> "上传中"
            text.contains("安装") || text.contains("install", ignoreCase = true) -> "安装中"
            text.contains("更新") || text.contains("update", ignoreCase = true) -> "更新中"
            text.contains("同步") || text.contains("sync", ignoreCase = true) -> "同步中"
            else -> null
        }
    }

    private fun durationPattern(units: String): Regex =
        Regex("(\\d+)\\s*(?:$units)", RegexOption.IGNORE_CASE)

    private fun durationMillis(text: String, pattern: Regex, maximum: Long, multiplier: Long): Long? {
        val value = pattern.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return value.takeIf { it in 1..maximum }?.times(multiplier)
    }

    private fun clockDurationMillis(text: String): Long {
        val match = clockPattern.find(text) ?: return 0L
        val first = match.groupValues[1].toLongOrNull() ?: return 0L
        val second = match.groupValues[2].toLongOrNull() ?: return 0L
        val third = match.groupValues[3].toLongOrNull()
        val totalSeconds = if (third == null) {
            if (second !in 0..59) return 0L
            first * 60 + second
        } else {
            if (second !in 0..59 || third !in 0..59) return 0L
            if (first > 24 || first == 24L && (second != 0L || third != 0L)) return 0L
            first * 3_600 + second * 60 + third
        }
        return totalSeconds.takeIf { it > 0 }?.times(MILLIS_PER_SECOND) ?: 0L
    }
}
