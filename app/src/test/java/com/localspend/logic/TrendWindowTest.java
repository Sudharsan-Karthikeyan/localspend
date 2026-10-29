package com.localspend.logic;

import static org.junit.Assert.assertEquals;

import com.localspend.logic.TrendWindow.Range;

import org.junit.Test;

import java.util.Calendar;

public class TrendWindowTest {

    @Test
    public void rollingRangesEndAtTheCurrentMonth() {
        TrendWindow three = TrendWindow.of(Range.THREE_MONTHS, 2026, Calendar.OCTOBER, 6);
        assertEquals(2026, three.getStartYear());
        assertEquals(Calendar.AUGUST, three.getStartMonth());
        assertEquals(3, three.getCount());

        TrendWindow twelve = TrendWindow.of(Range.TWELVE_MONTHS, 2026, Calendar.OCTOBER, 6);
        assertEquals(2025, twelve.getStartYear());
        assertEquals(Calendar.NOVEMBER, twelve.getStartMonth());
        assertEquals(12, twelve.getCount());
        assertEquals("Last 12 months", twelve.getLabel());
    }

    @Test
    public void australianTaxYearStartsInJuly() {
        TrendWindow w = TrendWindow.of(Range.TAX_YEAR, 2026, Calendar.OCTOBER, 6);
        assertEquals(2026, w.getStartYear());
        assertEquals(Calendar.JULY, w.getStartMonth());
        assertEquals(12, w.getCount());
        assertEquals("Tax year 2026–27", w.getLabel());
    }

    @Test
    public void beforeTheStartMonthTheTaxYearBeganLastYear() {
        TrendWindow w = TrendWindow.of(Range.TAX_YEAR, 2027, Calendar.FEBRUARY, 6);
        assertEquals(2026, w.getStartYear());
        assertEquals(Calendar.JULY, w.getStartMonth());
        TrendWindow india = TrendWindow.of(Range.TAX_YEAR, 2027, Calendar.FEBRUARY, 3);
        assertEquals(2026, india.getStartYear());
        assertEquals(Calendar.APRIL, india.getStartMonth());
        assertEquals("Tax year 2026–27", india.getLabel());
    }

    @Test
    public void januaryTaxYearIsTheCalendarYear() {
        TrendWindow w = TrendWindow.of(Range.TAX_YEAR, 2026, Calendar.OCTOBER, 0);
        assertEquals(2026, w.getStartYear());
        assertEquals(0, w.getStartMonth());
        assertEquals("Year 2026", w.getLabel());
    }

    @Test
    public void unknownStoredRangeFallsBackToSixMonths() {
        assertEquals(Range.SIX_MONTHS, Range.fromName("nonsense"));
        assertEquals(Range.TAX_YEAR, Range.fromName("TAX_YEAR"));
    }

    @Test
    public void taxYearFollowsTheDashboardCurrency() {
        assertEquals(Calendar.APRIL, TrendWindow.taxYearStartMonth(com.localspend.model.Currency.INR));
        assertEquals(Calendar.JULY, TrendWindow.taxYearStartMonth(com.localspend.model.Currency.AUD));
        assertEquals(Calendar.JANUARY, TrendWindow.taxYearStartMonth(com.localspend.model.Currency.USD));

        TrendWindow inr = TrendWindow.of(Range.TAX_YEAR, 2026, Calendar.OCTOBER, com.localspend.model.Currency.INR);
        assertEquals(2026, inr.getStartYear());
        assertEquals(Calendar.APRIL, inr.getStartMonth());
        assertEquals("FY 2026\u201327", inr.getLabel());

        // February 2027 is still in the 2026-27 financial year in India
        TrendWindow feb = TrendWindow.of(Range.TAX_YEAR, 2027, Calendar.FEBRUARY, com.localspend.model.Currency.INR);
        assertEquals(2026, feb.getStartYear());

        TrendWindow aud = TrendWindow.of(Range.TAX_YEAR, 2026, Calendar.OCTOBER, com.localspend.model.Currency.AUD);
        assertEquals(Calendar.JULY, aud.getStartMonth());
        assertEquals("Tax year 2026\u201327", aud.getLabel());
    }
}
