package io.github.magisk317.mipush.common.notification

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle

/** Shared visible-content extraction for app and hook notification paths. */
object NotificationContentSupport {
    fun firstText(extras: Bundle?, vararg keys: String): String? {
        if (extras == null) return null
        return keys.firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    @Suppress("DEPRECATION")
    fun hasCustomVisualContent(notification: Notification): Boolean {
        return notification.contentView != null ||
            notification.bigContentView != null ||
            notification.headsUpContentView != null
    }

    fun normalizedVisibleTextCandidates(notification: Notification): List<String> {
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
            .orEmpty()
        return buildList {
            add(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
            add(notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
            add(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            add(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            add(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            add(notification.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            add(notification.tickerText)
            addAll(lines)
        }.map(::normalizedVisibleText)
    }

    fun hasMeaningfulVisibleText(notification: Notification): Boolean {
        return normalizedVisibleTextCandidates(notification).any(String::isNotBlank)
    }

    fun hasMeaningfulVisibleText(
        context: Context,
        packageName: String,
        notification: Notification,
        channelName: CharSequence? = null,
        channelDescription: String? = null,
    ): Boolean {
        val ignoredTexts = buildSet {
            add(normalizedVisibleText(packageName))
            add(normalizedVisibleText(applicationLabelOrNull(context, packageName)))
            add(normalizedVisibleText(channelName))
            add(normalizedVisibleText(channelDescription))
        }.filterTo(mutableSetOf(), String::isNotBlank)
        return normalizedVisibleTextCandidates(notification).any { candidate ->
            candidate.isNotBlank() && candidate !in ignoredTexts
        }
    }

    fun hasMeaningfulVisibleContent(
        context: Context,
        packageName: String,
        notification: Notification,
        channelName: CharSequence? = null,
        channelDescription: String? = null,
    ): Boolean {
        return hasCustomVisualContent(notification) ||
            hasMeaningfulVisibleText(
                context = context,
                packageName = packageName,
                notification = notification,
                channelName = channelName,
                channelDescription = channelDescription,
            )
    }

    fun applicationLabelOrNull(context: Context, packageName: String): String? {
        return try {
            val packageManager = context.packageManager
            packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(packageManager)
                .toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun normalizedVisibleText(value: CharSequence?): String {
        val source = value?.toString().orEmpty()
        if (source.isEmpty()) return ""
        return buildString(source.length) {
            source.forEach { character ->
                val type = Character.getType(character)
                val isInvisible = character.isWhitespace() ||
                    type == Character.FORMAT.toInt() ||
                    type == Character.CONTROL.toInt() ||
                    type == Character.SURROGATE.toInt()
                if (!isInvisible) append(character)
            }
        }
    }
}
