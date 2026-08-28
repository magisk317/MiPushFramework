package com.xiaomi.mipush.sdk

import java.util.Locale
import java.util.TimeZone

/**
 * Pure accept-time conversion retained behind [PushMessageProcessor]'s public stock façade.
 *
 * Parsing intentionally remains direct so malformed lists preserve the façade's existing
 * index and number-format exception behavior.
 */
internal object PushMessageProcessorTimeZoneConverter {
    fun convert(timeZone: TimeZone, targetTimeZone: TimeZone, times: List<String>): List<String> {
        if (timeZone == targetTimeZone) {
            return times
        }
        val rawOffset = ((timeZone.rawOffset - targetTimeZone.rawOffset) / 1000) / 60
        val startHour = times[0].split(":")[0].toLong()
        val start = ((((startHour * 60) + times[0].split(":")[1].toLong()) - rawOffset) + 1440) % 1440
        val end = ((((times[1].split(":")[0].toLong() * 60) + times[1].split(":")[1].toLong()) - rawOffset) + 1440) % 1440
        return arrayListOf(
            String.format(Locale.US, "%1$02d:%2$02d", start / 60, start % 60),
            String.format(Locale.US, "%1$02d:%2$02d", end / 60, end % 60),
        )
    }
}
