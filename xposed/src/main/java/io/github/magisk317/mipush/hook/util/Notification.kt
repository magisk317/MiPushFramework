package io.github.magisk317.mipush.hook.util

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.xposed.newInstance

fun Notification.newBuilder(context: Context): Notification.Builder {
    return Notification.Builder::class.java.newInstance(context, this) as Notification.Builder
}

fun Notification.getText(): String? {
    return extras.getString(Notification.EXTRA_TEXT)
}

fun Notification.getTitle(): String? {
    return extras.getString(Notification.EXTRA_TITLE)
}

fun Notification.getBigTitle(): String? {
    return extras.getString(Notification.EXTRA_TITLE_BIG)
}

fun Notification.getInboxLines(): Array<CharSequence>? {
    return extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
}

fun Notification.getSummaryText(): CharSequence? {
    return extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
}

fun Notification.getBigText(): String? {
    return extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
}

fun Notification.getSubText(): String? {
    return extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
}

fun Notification.getTickerTextValue(): String? {
    return tickerText?.toString()
}

fun Notification.hasCustomVisualContent(): Boolean {
    return contentView != null || bigContentView != null || headsUpContentView != null
}

fun Notification.normalizedVisibleTextCandidates(): List<String> {
    val lines = getInboxLines()
        ?.map { it.toString() }
        .orEmpty()
    return buildList {
        add(getTitle().orEmpty())
        add(getBigTitle().orEmpty())
        add(getText().orEmpty())
        add(getBigText().orEmpty())
        add(getSubText().orEmpty())
        add(getSummaryText()?.toString().orEmpty())
        add(getTickerTextValue().orEmpty())
        addAll(lines)
    }.map { it.normalizedVisibleText() }
}

fun Notification.hasMeaningfulVisibleText(): Boolean {
    return normalizedVisibleTextCandidates().any { it.isNotBlank() }
}

fun Notification.hasMeaningfulVisibleText(
    context: Context,
    packageName: String,
    channelName: CharSequence? = null,
    channelDescription: String? = null
): Boolean {
    val ignoredTexts = buildSet {
        add(packageName.normalizedVisibleText())
        add(context.getApplicationLabelOrNull(packageName).normalizedVisibleText())
        add(channelName.normalizedVisibleText())
        add(channelDescription.normalizedVisibleText())
    }.filterTo(mutableSetOf()) { it.isNotBlank() }
    return normalizedVisibleTextCandidates().any { candidate ->
        candidate.isNotBlank() && candidate !in ignoredTexts
    }
}

fun Context.getApplicationLabelOrNull(packageName: String): String? {
    return try {
        val pm = packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }
}

fun CharSequence?.normalizedVisibleText(): String {
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
