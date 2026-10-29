package com.localspend.logic;

import com.localspend.util.Dates;

/**
 * Which months the dashboard chart shows: the last 3, 6 or 12 months up to now, or the current
 * tax year (12 months from the month the tax year starts, so later months are still empty).
 */
public final class TrendWindow {

    /** The choices offered above the chart. */
    public enum Range {
        THREE_MONTHS, SIX_MONTHS, TWELVE_MONTHS, TAX_YEAR;

        /** Safe lookup for a stored value; unknown values give six months. */
        public static Range fromName(String name) {
            for (Range r : values()) {
                if (r.name().equals(name)) {
                    return r;
                }
            }
            return SIX_MONTHS;
        }
    }

    private final int startYear;
    private final int startMonth;   // 0-based
    private final int count;
    private final String label;

    private TrendWindow(int startYear, int startMonth, int count, String label) {
        this.startYear = startYear;
        this.startMonth = startMonth;
        this.count = count;
        this.label = label;
    }

    /**
     * The month the tax year starts for the currency on the dashboard: April for rupees (India's
     * financial year), July for Australian dollars, January for US dollars.
     */
    public static int taxYearStartMonth(com.localspend.model.Currency currency) {
        if (currency == com.localspend.model.Currency.INR) {
            return 3;
        }
        if (currency == com.localspend.model.Currency.AUD) {
            return 6;
        }
        return 0;
    }

    /** The same as {@link #of(Range, int, int, int)} with the tax year chosen by currency. */
    public static TrendWindow of(Range range, int nowYear, int nowMonth,
                                 com.localspend.model.Currency currency) {
        TrendWindow w = of(range, nowYear, nowMonth, taxYearStartMonth(currency));
        if (range == Range.TAX_YEAR && currency == com.localspend.model.Currency.INR) {
            return new TrendWindow(w.startYear, w.startMonth, w.count,
                    "FY " + w.startYear + "\u2013" + String.format("%02d", (w.startYear + 1) % 100));
        }
        return w;
    }

    /**
     * @param nowMonth 0-based current month
     * @param taxStartMonth 0-based month the tax year begins (6 = July, 3 = April, 0 = January)
     */
    public static TrendWindow of(Range range, int nowYear, int nowMonth, int taxStartMonth) {
        switch (range) {
            case THREE_MONTHS:
                return rolling(3, nowYear, nowMonth, "Last 3 months");
            case TWELVE_MONTHS:
                return rolling(12, nowYear, nowMonth, "Last 12 months");
            case TAX_YEAR:
                int startYear = nowMonth >= taxStartMonth ? nowYear : nowYear - 1;
                return new TrendWindow(startYear, taxStartMonth, 12,
                        taxStartMonth == 0 ? "Year " + startYear
                                : "Tax year " + startYear + "\u2013"
                                + String.format("%02d", (startYear + 1) % 100));
            default:
                return rolling(6, nowYear, nowMonth, "Last 6 months");
        }
    }

    private static TrendWindow rolling(int months, int nowYear, int nowMonth, String label) {
        int[] start = Dates.shiftMonth(nowYear, nowMonth, -(months - 1));
        return new TrendWindow(start[0], start[1], months, label);
    }

    public int getStartYear() {
        return startYear;
    }

    public int getStartMonth() {
        return startMonth;
    }

    public int getCount() {
        return count;
    }

    /** Heading for the chart: "Last 6 months", "Tax year 2026\u201327". */
    public String getLabel() {
        return label;
    }
}
