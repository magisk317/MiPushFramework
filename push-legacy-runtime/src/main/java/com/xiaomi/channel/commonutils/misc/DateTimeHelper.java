package com.xiaomi.channel.commonutils.misc;

import android.text.TextUtils;
import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.xml.sax.SAXException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/DateTimeHelper.class */
public class DateTimeHelper {
    public static final int DAY_IN_HOUR = 24;
    public static final int DAY_IN_MINUTE = 1440;
    public static final int DAY_IN_MS = 86400000;
    public static final int DAY_IN_SECOND = 86400;
    public static final int HOUR_IN_MINUTE = 60;
    public static final int HOUR_IN_MS = 3600000;
    public static final int HOUR_IN_SECOND = 3600;
    private static final String LOG_TAG = "common/DateTimeHelper";
    public static final int MINUTE_IN_MS = 60000;
    public static final int MINUTE_IN_SECOND = 60;
    public static final int SECOND_IN_MS = 1000;
    public static final int WEEK_IN_DAY = 7;
    public static final int WEEK_IN_HOUR = 168;
    public static final int WEEK_IN_MINUTE = 10080;
    public static final int WEEK_IN_MS = 604800000;
    public static final int WEEK_IN_SECOND = 604800;
    public static final TimeZone sBeijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai");
    public static final long sHourInMinutes = 60;

    public static String getCurrentString(String str) {
        return new SimpleDateFormat(str).format(new Date(System.currentTimeMillis()));
    }

    public static final long getCurrentTiemstamp() {
        return Calendar.getInstance(sBeijingTimeZone).getTimeInMillis();
    }

    public static final long getElapsedMinutesFromHour() {
        return getElapsedMinutesFromHour(getCurrentTiemstamp());
    }

    public static final long getElapsedMinutesFromHour(long j) {
        return getElapsedMinutesFromToday(j) % 60;
    }

    public static final long getElapsedMinutesFromToday() {
        return getElapsedMinutesFromToday(getCurrentTiemstamp());
    }

    public static final long getElapsedMinutesFromToday(long j) {
        return (j - getTodayStartTimestamp(j)) / 60000;
    }

    public static final long getTodayStartTimestamp() {
        return getTodayStartTimestamp(getCurrentTiemstamp());
    }

    public static final long getTodayStartTimestamp(long j) {
        return j - (j % 86400000);
    }

    public static final long getTomorrowStartTimestamp(long j) {
        return (j - (j % 86400000)) + 86400000;
    }

    public static String getWeekday(Date date) {
        Calendar calendar = Calendar.getInstance(sBeijingTimeZone, Locale.CHINA);
        calendar.setTime(date);
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.SUNDAY:
                return "周日";
            case Calendar.MONDAY:
                return "周一";
            case Calendar.TUESDAY:
                return "周二";
            case Calendar.WEDNESDAY:
                return "周三";
            case Calendar.THURSDAY:
                return "周四";
            case Calendar.FRIDAY:
                return "周五";
            default:
                return "周六";
        }
    }

    public static long parseDate(String str) throws SAXException {
        if (TextUtils.isEmpty(str)) {
            return -1L;
        }
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        try {
            gregorianCalendar.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(str));
            gregorianCalendar.setTimeZone(sBeijingTimeZone);
            return gregorianCalendar.getTimeInMillis();
        } catch (ParseException e) {
            Log.e(LOG_TAG, "Failed to parse date", e);
            return -1L;
        }
    }
}
