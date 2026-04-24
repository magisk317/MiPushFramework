package com.xiaomi.channel.commonutils.misc

import android.text.TextUtils
import android.util.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object DateTimeHelper {
    const val DAY_IN_HOUR = 24
    const val DAY_IN_MINUTE = 1440
    const val DAY_IN_MS = 86400000
    const val DAY_IN_SECOND = 86400
    const val HOUR_IN_MINUTE = 60
    const val HOUR_IN_MS = 3600000
    const val HOUR_IN_SECOND = 3600
    const val MINUTE_IN_MS = 60000
    const val MINUTE_IN_SECOND = 60
    const val SECOND_IN_MS = 1000
    const val WEEK_IN_DAY = 7
    const val WEEK_IN_HOUR = 168
    const val WEEK_IN_MINUTE = 10080
    const val WEEK_IN_MS = 604800000
    const val WEEK_IN_SECOND = 604800

    private const val LOG_TAG = "common/DateTimeHelper"
    val sBeijingTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    const val sHourInMinutes: Long = 60

    fun getCurrentString(pattern: String): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(System.currentTimeMillis()))
    }

    fun getCurrentTiemstamp(): Long = Calendar.getInstance(sBeijingTimeZone).timeInMillis

    fun getElapsedMinutesFromHour(): Long = getElapsedMinutesFromHour(getCurrentTiemstamp())

    fun getElapsedMinutesFromHour(timestamp: Long): Long = getElapsedMinutesFromToday(timestamp) % 60

    fun getElapsedMinutesFromToday(): Long = getElapsedMinutesFromToday(getCurrentTiemstamp())

    fun getElapsedMinutesFromToday(timestamp: Long): Long =
        (timestamp - getTodayStartTimestamp(timestamp)) / 60000

    fun getTodayStartTimestamp(): Long = getTodayStartTimestamp(getCurrentTiemstamp())

    fun getTodayStartTimestamp(timestamp: Long): Long = timestamp - (timestamp % DAY_IN_MS)

    fun getTomorrowStartTimestamp(timestamp: Long): Long =
        (timestamp - (timestamp % DAY_IN_MS)) + DAY_IN_MS

    fun getWeekday(date: Date): String {
        val calendar = Calendar.getInstance(sBeijingTimeZone, Locale.CHINA)
        calendar.time = date
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            else -> "周六"
        }
    }

    @Throws(org.xml.sax.SAXException::class)
    fun parseDate(str: String): Long {
        if (TextUtils.isEmpty(str)) return -1L
        val gregorianCalendar = GregorianCalendar()
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(str)
            if (date != null) {
                gregorianCalendar.time = date
            } else {
                return -1L
            }
            gregorianCalendar.timeZone = sBeijingTimeZone
            gregorianCalendar.timeInMillis
        } catch (e: ParseException) {
            Log.e(LOG_TAG, "Failed to parse date", e)
            -1L
        }
    }
}
