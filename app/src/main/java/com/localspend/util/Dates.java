package com.localspend.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Calendar-month helpers. Months are 0-based (Calendar.JANUARY = 0) to match java.util.Calendar.
 * Out-of-range months roll over, so startOfMonth(2026, -1) is December 2025.
 */
public final class Dates {

    private Dates() {
    }

    public static long startOfMonth(int year, int month) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /** Returns {year, month} after moving {@code delta} months (negative = earlier). */
    public static int[] shiftMonth(int year, int month, int delta) {
        int total = year * 12 + month + delta;
        return new int[]{Math.floorDiv(total, 12), Math.floorMod(total, 12)};
    }

    public static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }

    public static int monthOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.MONTH);
    }

    /** "October 2026". */
    public static String monthLabel(int year, int month) {
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(new Date(startOfMonth(year, month)));
    }

    /** "Oct". */
    public static String shortMonth(int year, int month) {
        return new SimpleDateFormat("MMM", Locale.getDefault())
                .format(new Date(startOfMonth(year, month)));
    }

    /** "28 Aug 2026". */
    public static String formatDay(long millis) {
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    /** Local midnight at the start of the day containing {@code millis}. */
    public static long dayStart(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "Today", "Yesterday", or "Mon 12 May" (with the year when it is not this year). */
    public static String dayHeader(long dayStartMillis) {
        return dayHeader(dayStartMillis, System.currentTimeMillis());
    }

    public static String dayHeader(long dayStartMillis, long nowMillis) {
        long today = dayStart(nowMillis);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(today);
        c.add(Calendar.DAY_OF_MONTH, -1);
        if (dayStartMillis == today) {
            return "Today";
        }
        if (dayStartMillis == c.getTimeInMillis()) {
            return "Yesterday";
        }
        boolean sameYear = yearOf(dayStartMillis) == yearOf(nowMillis);
        return new SimpleDateFormat(sameYear ? "EEE d MMM" : "EEE d MMM yyyy", Locale.getDefault())
                .format(new Date(dayStartMillis));
    }

    /** "28 Aug 2026, 14:05". */
    public static String formatDateTime(long millis) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /** Number of days in the month (month is 0-based). */
    public static int daysInMonth(int year, int month) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /** "2026-08-28" in the device time zone, used for SMS de-duplication. */
    public static String isoDay(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(millis));
    }
}
