package com.localspend.logic;

import com.localspend.model.Currency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Finds the exchange rate to use for a transaction, entirely on the device (the app has no
 * internet permission, so nothing is ever looked up online).
 *
 * No rate is built in. Saved rates are the ones fetched for a day (see RateFetcher) or typed by
 * the user. For a payment made on a given day:
 * 1. a saved rate for that exact day is used (exact, not an estimate);
 * 2. otherwise the saved rate nearest in time, an earlier day winning a tie (an estimate);
 * 3. otherwise there is no rate and {@link #find} returns null.
 *
 * A saved rate works in both directions, so a rate saved for A$ to ₹ also converts ₹ to A$.
 */
public final class ExchangeRates {

    /** A rate the user saved: 1 {@code from} = {@code rate} {@code to}, on one day. */
    public static final class Saved {
        private final Currency from;
        private final Currency to;
        private final long dayStart;
        private final BigDecimal rate;

        public Saved(Currency from, Currency to, long dayStart, BigDecimal rate) {
            this.from = from;
            this.to = to;
            this.dayStart = dayStart(dayStart);
            this.rate = rate;
        }

        public Currency getFrom() {
            return from;
        }

        public Currency getTo() {
            return to;
        }

        public long getDayStart() {
            return dayStart;
        }

        public BigDecimal getRate() {
            return rate;
        }
    }

    /** The rate chosen for one transaction, and where it came from. */
    public static final class Rate {
        private final BigDecimal value;
        private final long dayStart;
        private final boolean estimated;
        private final boolean fromSaved;

        Rate(BigDecimal value, long dayStart, boolean estimated, boolean fromSaved) {
            this.value = value;
            this.dayStart = dayStart;
            this.estimated = estimated;
            this.fromSaved = fromSaved;
        }

        /** True when the rate comes from a saved rate (maybe of a nearby day). */
        public boolean isFromSaved() {
            return fromSaved;
        }

        /** How many units of the target currency one unit of the source is worth. */
        public BigDecimal getValue() {
            return value;
        }

        /** The day this rate is for (the transaction's day when it is exact). */
        public long getDayStart() {
            return dayStart;
        }

        /** False only for a rate the user saved for exactly the transaction's day. */
        public boolean isEstimated() {
            return estimated;
        }
    }

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final int RATE_SCALE = 6;

    private final List<Saved> saved = new ArrayList<>();

    public ExchangeRates(List<Saved> savedRates) {
        if (savedRates != null) {
            saved.addAll(savedRates);
        }
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

    /**
     * The rate for turning {@code from} into {@code to} for a payment made at {@code dateMillis},
     * or null when no rate is known yet.
     */
    public Rate find(Currency from, Currency to, long dateMillis) {
        long day = dayStart(dateMillis);
        if (from == to) {
            return new Rate(BigDecimal.ONE, day, false, false);
        }
        Saved best = null;
        BigDecimal bestRate = null;
        long bestGap = Long.MAX_VALUE;
        for (Saved s : saved) {
            BigDecimal usable;
            if (s.from == from && s.to == to) {
                usable = s.rate;
            } else if (s.from == to && s.to == from && s.rate.signum() > 0) {
                usable = BigDecimal.ONE.divide(s.rate, MC).setScale(RATE_SCALE, RoundingMode.HALF_UP);
            } else {
                continue;
            }
            long gap = Math.abs(s.dayStart - day);
            boolean earlier = best != null && s.dayStart < best.dayStart;
            if (gap < bestGap || (gap == bestGap && earlier)) {
                best = s;
                bestRate = usable;
                bestGap = gap;
            }
        }
        if (best != null) {
            return new Rate(bestRate, best.dayStart, bestGap != 0, true);
        }
        return null;   // nothing known for this pair: the caller fetches a rate or asks the user
    }

    /** Converts an amount in cents at the given rate, rounded to the nearest cent (never zero). */
    public static long convert(long cents, BigDecimal rate) {
        long converted = BigDecimal.valueOf(cents).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Math.max(1, converted);
    }
}
