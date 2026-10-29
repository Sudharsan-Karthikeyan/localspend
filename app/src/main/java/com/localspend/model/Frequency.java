package com.localspend.model;

import java.util.Calendar;

/**
 * How often a repeating payment happens. Dates move on in the device time zone, keeping the
 * time of day; monthly and yearly payments keep their original day of the month where the
 * month is long enough (a payment on the 31st falls on the 28th/29th/30th in shorter months).
 */
public enum Frequency {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    FORTNIGHTLY("Fortnightly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    private final String label;

    Frequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** The occurrence after {@code millis}. {@code anchorDay} is the day of the month it started on. */
    public long next(long millis, int anchorDay) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        switch (this) {
            case DAILY:
                c.add(Calendar.DAY_OF_MONTH, 1);
                return c.getTimeInMillis();
            case WEEKLY:
                c.add(Calendar.DAY_OF_MONTH, 7);
                return c.getTimeInMillis();
            case FORTNIGHTLY:
                c.add(Calendar.DAY_OF_MONTH, 14);
                return c.getTimeInMillis();
            case YEARLY:
                return addMonths(c, 12, anchorDay);
            case MONTHLY:
            default:
                return addMonths(c, 1, anchorDay);
        }
    }

    private static long addMonths(Calendar c, int months, int anchorDay) {
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, months);
        c.set(Calendar.DAY_OF_MONTH, Math.min(Math.max(anchorDay, 1),
                c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return c.getTimeInMillis();
    }

    /** Safe lookup when reading from the database; unknown values fall back to monthly. */
    public static Frequency fromName(String name) {
        for (Frequency f : values()) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return MONTHLY;
    }
}
