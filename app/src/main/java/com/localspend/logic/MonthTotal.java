package com.localspend.logic;

/** Total spending for one month, a data point for the trend chart. */
public final class MonthTotal {

    private final int year;
    private final int month;
    private final long spentCents;

    MonthTotal(int year, int month, long spentCents) {
        this.year = year;
        this.month = month;
        this.spentCents = spentCents;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public long getSpentCents() {
        return spentCents;
    }
}
