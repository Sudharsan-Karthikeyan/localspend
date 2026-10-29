package com.localspend.sms;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the transaction date written inside an SMS ("on 28/08/2026", "28-Aug-26", "2026-08-28",
 * "28 Aug"). Dates are read day-first, as Australian and Indian banks write them.
 *
 * Banks sometimes send the message a day or two after the purchase, so the date in the text is
 * more accurate than the arrival time. If the text has no usable date, or the date found is
 * implausible (in the future, or long before the message arrived), the arrival time is used.
 */
public final class SmsDateExtractor {

    /** How far before the arrival time a written date is still believed. */
    private static final long MAX_AGE_MILLIS = 45L * 24 * 60 * 60 * 1000;

    private static final String MONTHS =
            "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec"
                    + "|january|february|march|april|june|july|august|september|october|november|december";

    // 2026-08-28
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    // 28/08/2026, 28-08-26, 28.08.2026
    private static final Pattern NUMERIC = Pattern.compile(
            "(?<![\\d/.:-])(\\d{1,2})([/.-])(\\d{1,2})\\2(\\d{4}|\\d{2})(?!\\d|[/.:-]\\d)");
    // 28-Aug-26, 28 Aug 2026, 28Aug2026, 28 Aug
    private static final Pattern NAMED = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-/]?(" + MONTHS + ")\\b\\.?(?:[\\s\\-/,]+(\\d{4}|\\d{2})(?![\\d:]))?",
            Pattern.CASE_INSENSITIVE);
    // 28/08 or 28-08 (no year)
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\bon\\s+(\\d{1,2})[/-](\\d{1,2})(?![\\d/:-])", Pattern.CASE_INSENSITIVE);

    private SmsDateExtractor() {
    }

    /**
     * Returns the transaction time: the date written in the message (keeping the arrival
     * time of day so ordering stays sensible), or the arrival time if no usable date is found.
     */
    public static long extract(String text, long receivedMillis) {
        Calendar found = find(text, receivedMillis);
        if (found == null) {
            return receivedMillis;
        }
        Calendar arrival = Calendar.getInstance();
        arrival.setTimeInMillis(receivedMillis);
        if (sameDay(found, arrival)) {
            return receivedMillis;
        }
        found.set(Calendar.HOUR_OF_DAY, arrival.get(Calendar.HOUR_OF_DAY));
        found.set(Calendar.MINUTE, arrival.get(Calendar.MINUTE));
        found.set(Calendar.SECOND, arrival.get(Calendar.SECOND));
        found.set(Calendar.MILLISECOND, 0);
        long millis = found.getTimeInMillis();
        // Never in the future, and not implausibly old.
        if (millis > receivedMillis || receivedMillis - millis > MAX_AGE_MILLIS) {
            return receivedMillis;
        }
        return millis;
    }

    private static Calendar find(String text, long receivedMillis) {
        if (text == null) {
            return null;
        }
        int arrivalYear = yearOf(receivedMillis);

        Matcher m = ISO.matcher(text);
        if (m.find()) {
            Calendar c = build(toInt(m.group(1)), toInt(m.group(2)), toInt(m.group(3)));
            if (c != null) {
                return c;
            }
        }
        m = NUMERIC.matcher(text);
        while (m.find()) {
            Calendar c = build(fullYear(m.group(4)), toInt(m.group(3)), toInt(m.group(1)));
            if (c != null) {
                return c;
            }
        }
        m = NAMED.matcher(text);
        while (m.find()) {
            int month = monthNumber(m.group(2));
            int year = m.group(3) == null ? arrivalYear : fullYear(m.group(3));
            Calendar c = build(year, month, toInt(m.group(1)));
            if (c != null) {
                return withYearRollback(c, m.group(3) == null, receivedMillis);
            }
        }
        m = DAY_MONTH.matcher(text);
        if (m.find()) {
            Calendar c = build(arrivalYear, toInt(m.group(2)), toInt(m.group(1)));
            if (c != null) {
                return withYearRollback(c, true, receivedMillis);
            }
        }
        return null;
    }

    /** "31 Dec" read on 2 January belongs to last year. */
    private static Calendar withYearRollback(Calendar c, boolean yearWasGuessed, long receivedMillis) {
        if (yearWasGuessed && c.getTimeInMillis() > receivedMillis + 24L * 60 * 60 * 1000) {
            c.add(Calendar.YEAR, -1);
        }
        return c;
    }

    /** A calendar at midnight on the date, or null if the date does not exist. */
    private static Calendar build(int year, int month1to12, int day) {
        if (year < 2000 || year > 2100 || month1to12 < 1 || month1to12 > 12 || day < 1) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month1to12 - 1, 1);
        if (day > c.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            return null;
        }
        c.set(Calendar.DAY_OF_MONTH, day);
        return c;
    }

    private static int monthNumber(String name) {
        String n = name.toLowerCase(Locale.ROOT).substring(0, 3);
        String[] all = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(n)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static int fullYear(String y) {
        int v = toInt(y);
        return y.length() == 2 ? 2000 + v : v;
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
