package com.localspend.sms;

import static org.junit.Assert.assertEquals;

import com.localspend.util.Dates;

import org.junit.Test;

import java.util.Calendar;

public class SmsDateExtractorTest {

    /** 29 Aug 2026, 14:30 local time. */
    private static final long ARRIVAL = at(2026, Calendar.AUGUST, 29, 14, 30);

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d, h, min, 0);
        return c.getTimeInMillis();
    }

    private static String day(String text) {
        return Dates.isoDay(SmsDateExtractor.extract(text, ARRIVAL));
    }

    @Test
    public void readsCommonFormats() {
        assertEquals("2026-08-28", day("Spent $5 at X on 28/08/2026."));
        assertEquals("2026-08-28", day("Rs 500 debited on 28-08-26 to Zomato"));
        assertEquals("2026-08-27", day("Txn on 27.08.2026 at KFC"));
        assertEquals("2026-08-28", day("Purchase 2026-08-28 at Coles"));
        assertEquals("2026-08-26", day("Spent INR 250 on 26-Aug-26 at Cafe"));
        assertEquals("2026-08-25", day("You paid $12 to Uber on 25 Aug 2026"));
        assertEquals("2026-08-24", day("Card used on 24th August at Myer"));
        assertEquals("2026-08-28", day("Spent Rs 250 at Cafe on 28-08."));
    }

    @Test
    public void keepsArrivalTimeOfDay() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(SmsDateExtractor.extract("on 28/08/2026", ARRIVAL));
        assertEquals(14, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, c.get(Calendar.MINUTE));
    }

    @Test
    public void sameDayUsesExactArrivalTime() {
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent $5 on 29/08/2026", ARRIVAL));
    }

    @Test
    public void fallsBackToArrivalWhenMissingOrImplausible() {
        assertEquals(ARRIVAL, SmsDateExtractor.extract("You spent $5 at KFC", ARRIVAL));
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent on 30/08/2026", ARRIVAL));   // future
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent on 01/01/2025", ARRIVAL));   // too old
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent on 31/02/2026", ARRIVAL));   // no such day
        assertEquals(ARRIVAL, SmsDateExtractor.extract(null, ARRIVAL));
    }

    @Test
    public void ignoresTimesAmountsAndStreetNames() {
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent $1,200.00 at 10:45", ARRIVAL));
        assertEquals(ARRIVAL, SmsDateExtractor.extract("Spent $5 at 5 Market St Sydney", ARRIVAL));
    }

    @Test
    public void yearlessDateInJanuaryForDecemberIsLastYear() {
        long jan2 = at(2027, Calendar.JANUARY, 2, 9, 0);
        assertEquals("2026-12-31", Dates.isoDay(SmsDateExtractor.extract("Spent on 31 Dec", jan2)));
    }
}
