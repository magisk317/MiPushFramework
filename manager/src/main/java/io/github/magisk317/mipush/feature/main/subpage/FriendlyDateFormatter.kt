package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import io.github.magisk317.mipush.manager.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal fun friendlyDateString(fromServer: Date, current: Date, context: Context): String {
    val calendarCurrent = Calendar.getInstance().apply { time = current }
    val calendarServer = Calendar.getInstance().apply { time = fromServer }
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
                    context.resources.getQuantityString(R.plurals.date_minutes, min.toInt()),
                )
            }
        }
        return context.getString(
            R.string.date_format_normal,
            hour.toString(),
            context.resources.getQuantityString(R.plurals.date_hours, hour.toInt()),
        )
    }

    return if (day < 30) {
        context.getString(
            R.string.date_format_normal,
            day.toString(),
            context.resources.getQuantityString(R.plurals.date_days, day.toInt()),
        )
    } else {
        context.getString(R.string.date_format_long, DateFormat.getDateTimeInstance().format(calendarServer.time))
    }
}
