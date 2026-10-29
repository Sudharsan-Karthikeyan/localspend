package com.localspend.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class DayHeaderTest {

    private static long at(int y, int m, int d, int h) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d, h, 15, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void todayAndYesterdayAreNamed() {
        long now = at(2026, Calendar.OCTOBER, 27, 22);
        assertEquals("Today", Dates.dayHeader(Dates.dayStart(at(2026, Calendar.OCTOBER, 27, 3)), now));
        assertEquals("Yesterday", Dates.dayHeader(Dates.dayStart(at(2026, Calendar.OCTOBER, 26, 23)), now));
    }

    @Test
    public void olderDaysUseTheDateAndOnlyShowTheYearWhenDifferent() {
        long now = at(2026, Calendar.OCTOBER, 27, 22);
        String thisYear = Dates.dayHeader(Dates.dayStart(at(2026, Calendar.MAY, 21, 9)), now);
        String lastYear = Dates.dayHeader(Dates.dayStart(at(2025, Calendar.MAY, 21, 9)), now);
        assertEquals(false, thisYear.contains("2026"));
        assertEquals(true, lastYear.contains("2025"));
    }

    @Test
    public void dayStartIsMidnight() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Dates.dayStart(at(2026, Calendar.MAY, 21, 22)));
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(21, c.get(Calendar.DAY_OF_MONTH));
    }
}
