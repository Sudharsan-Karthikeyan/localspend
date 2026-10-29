package com.localspend.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class MoneyAndDatesTest {

    @Test
    public void formatsCents() {
        assertEquals("$2,550.00", MoneyFormatter.format(255000));
        assertEquals("$0.05", MoneyFormatter.format(5));
        assertEquals("$1,250.00", MoneyFormatter.format(125000));
        assertEquals("$1,234,567.89", MoneyFormatter.format(123456789));
        assertEquals("-$3.00", MoneyFormatter.format(-300));
    }

    @Test
    public void formatsWholeDollars() {
        assertEquals("$1,250", MoneyFormatter.formatWhole(125049));
        assertEquals("$1,251", MoneyFormatter.formatWhole(125050));
        assertEquals("$0", MoneyFormatter.formatWhole(0));
    }

    @Test
    public void parsesUserInput() {
        assertEquals(2550, MoneyFormatter.parseToCents("25.5"));
        assertEquals(125000, MoneyFormatter.parseToCents("1,250.00"));
        assertEquals(1200, MoneyFormatter.parseToCents("$12"));
        assertEquals(1, MoneyFormatter.parseToCents("0.005"));
        assertEquals(0, MoneyFormatter.parseToCents("0"));
    }

    @Test(expected = NumberFormatException.class)
    public void rejectsText() {
        MoneyFormatter.parseToCents("abc");
    }

    @Test(expected = NumberFormatException.class)
    public void rejectsNegative() {
        MoneyFormatter.parseToCents("-5");
    }

    @Test(expected = NumberFormatException.class)
    public void rejectsEmpty() {
        MoneyFormatter.parseToCents("  ");
    }

    @Test
    public void shiftMonthWrapsYears() {
        int[] back = Dates.shiftMonth(2026, Calendar.JANUARY, -1);
        assertEquals(2025, back[0]);
        assertEquals(Calendar.DECEMBER, back[1]);
        int[] fwd = Dates.shiftMonth(2026, Calendar.DECEMBER, 2);
        assertEquals(2027, fwd[0]);
        assertEquals(Calendar.FEBRUARY, fwd[1]);
    }

    @Test
    public void startOfMonthAndLookups() {
        long start = Dates.startOfMonth(2026, Calendar.OCTOBER);
        assertEquals(2026, Dates.yearOf(start));
        assertEquals(Calendar.OCTOBER, Dates.monthOf(start));
        assertEquals(Dates.startOfMonth(2025, Calendar.DECEMBER), Dates.startOfMonth(2026, -1));
    }
}
