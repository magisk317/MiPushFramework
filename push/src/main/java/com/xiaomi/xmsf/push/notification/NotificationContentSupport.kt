package com.xiaomi.xmsf.push.notification

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager

internal object NotificationContentSupport {
    fun hasMeaningfulVisibleText(
        context: Context,
        packageName: String,
        notification: Notification,
        channel: NotificationChannel?
    ): Boolean {
        if (notification.hasCustomVisualContent()) {
            return true
        }
        val ignoredTexts = buildSet {
            add(packageName.normalizedVisibleText())
            add(context.getApplicationLabelOrNull(packageName).normalizedVisibleText())
            add(channel?.name.normalizedVisibleText())
            add(channel?.description.normalizedVisibleText())
        }.filterTo(mutableSetOf()) { it.isNotBlank() }
        return notification.normalizedVisibleTextCandidates().any { candidate ->
            candidate.isNotBlank() && candidate !in ignoredTexts
        }
    }

    @Suppress("DEPRECATION")
    private fun Notification.hasCustomVisualContent(): Boolean {
        return contentView != null || bigContentView != null || headsUpContentView != null
    }

    private fun Notification.normalizedVisibleTextCandidates(): List<String> {
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            .orEmpty()
        return buildList {
            add(extras.getCharSequence(Notification.EXTRA_TITLE))
            add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
            add(extras.getCharSequence(Notification.EXTRA_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            add(tickerText)
            addAll(lines)
        }.map { it.normalizedVisibleText() }
    }

    private fun Context.getApplicationLabelOrNull(packageName: String): String? {
        return try {
            val pm = packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun CharSequence?.normalizedVisibleText(): String {
        val source = this?.toString().orEmpty()
        if (source.isEmpty()) return ""
        return buildString(source.length) {
            source.forEach { ch ->
                val type = Character.getType(ch)
                val keep = when {
                    ch.isWhitespace() -> false
                    type == Character.FORMAT.toInt() -> false
                    type == Character.CONTROL.toInt() -> false
                    type == Character.SURROGATE.toInt() -> false
                    else -> true
                }
                if (keep) append(ch)
            }
        }
    }
}
