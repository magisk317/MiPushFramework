package io.github.magisk317.mipush.platform.support

import android.content.Context
import com.xiaomi.xmsf.R
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ParseUtils {
    @JvmStatic
    fun parseDate(dateString: String): Date {
        return try {
            val localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            Date.from(localDateTime.toInstant(ZoneOffset.UTC))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getFriendlyDateString(fromServer: Date, current: Date, context: Context): String {
        val calendarCurrent = Calendar.getInstance()
        val calendarServer = Calendar.getInstance()
        calendarServer.time = fromServer
        calendarCurrent.time = current

        val diff = calendarCurrent.timeInMillis - calendarServer.timeInMillis
        val min = diff / (60 * 1000)
        val hour = diff / (60 * 60 * 1000)
        val day = diff / (24 * 60 * 60 * 1000)

        if (day < 1) {
            if (hour < 1) {
                return if (min < 1) {
                    context.getString(R.string.date_format_just, context.getString(R.string.date_just))
                } else {
                    context.getString(
                        R.string.date_format_normal,
                        min.toString(),
                        context.resources.getQuantityString(R.plurals.date_minutes, min.toInt())
                    )
                }
            }
            return if (hour < 24) {
                context.getString(
                    R.string.date_format_normal,
                    hour.toString(),
                    context.resources.getQuantityString(R.plurals.date_hours, hour.toInt())
                )
            } else {
                parseDate(calendarServer.time)
            }
        }

        return if (day < 30) {
            context.getString(
                R.string.date_format_normal,
                day.toString(),
                context.resources.getQuantityString(R.plurals.date_days, day.toInt())
            )
        } else {
            context.getString(R.string.date_format_long, parseDate(calendarServer.time))
        }
    }

    @JvmStatic
    fun parseDate(date: Date): String {
        return DateFormat.getDateTimeInstance().format(date)
    }
}
