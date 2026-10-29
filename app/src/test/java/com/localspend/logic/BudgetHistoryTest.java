package com.localspend.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class BudgetHistoryTest {

    private static int m(int year, int month) {
        return BudgetHistory.monthIndex(year, month);
    }

    private final String FOOD = "FOOD_AND_DINING";

    @Test
    public void noEntriesMeansNoBudget() {
        assertEquals(0, new BudgetHistory().centsFor(FOOD, m(2026, Calendar.OCTOBER)));
    }

    @Test
    public void fromThisMonthLeavesEarlierMonthsAlone() {
        BudgetHistory h = new BudgetHistory();
        h.setForAllMonths(FOOD, 50000);                                  // old budget, every month
        h.setFrom(FOOD, m(2026, Calendar.OCTOBER), 80000);               // raised from October
        assertEquals(50000, h.centsFor(FOOD, m(2026, Calendar.SEPTEMBER)));
        assertEquals(80000, h.centsFor(FOOD, m(2026, Calendar.OCTOBER)));
        assertEquals(80000, h.centsFor(FOOD, m(2027, Calendar.JANUARY)));
    }

    @Test
    public void allMonthsReplacesWhatWasSetForEarlierMonths() {
        BudgetHistory h = new BudgetHistory();
        h.setFrom(FOOD, m(2026, Calendar.APRIL), 30000);
        h.setFrom(FOOD, m(2026, Calendar.JULY), 40000);
        h.setForAllMonths(FOOD, 90000);
        assertEquals(90000, h.centsFor(FOOD, m(2026, Calendar.APRIL)));
        assertEquals(90000, h.centsFor(FOOD, m(2026, Calendar.JULY)));
        assertEquals(90000, h.centsFor(FOOD, m(2020, Calendar.JANUARY)));
    }

    @Test
    public void removingFromThisMonthKeepsEarlierBudgets() {
        BudgetHistory h = new BudgetHistory();
        h.setForAllMonths(FOOD, 90000);
        h.setFrom(FOOD, m(2026, Calendar.OCTOBER), 0);                   // removed from October
        assertEquals(90000, h.centsFor(FOOD, m(2026, Calendar.SEPTEMBER)));
        assertEquals(0, h.centsFor(FOOD, m(2026, Calendar.OCTOBER)));
        assertEquals(0, h.centsFor(FOOD, m(2026, Calendar.DECEMBER)));
    }

    @Test
    public void editingOnePastMonthLeavesTheNextMonthsAsTheyWere() {
        BudgetHistory h = new BudgetHistory();
        h.setForAllMonths(FOOD, 90000);
        h.setOnlyMonth(FOOD, m(2026, Calendar.JUNE), 0);                 // removed for June only
        assertEquals(90000, h.centsFor(FOOD, m(2026, Calendar.MAY)));
        assertEquals(0, h.centsFor(FOOD, m(2026, Calendar.JUNE)));
        assertEquals(90000, h.centsFor(FOOD, m(2026, Calendar.JULY)));
        assertEquals(90000, h.centsFor(FOOD, m(2027, Calendar.JANUARY)));
    }

    @Test
    public void editingAMonthRightBeforeAnotherEntryKeepsThatEntry() {
        BudgetHistory h = new BudgetHistory();
        h.setForAllMonths(FOOD, 50000);
        h.setFrom(FOOD, m(2026, Calendar.JULY), 70000);
        h.setOnlyMonth(FOOD, m(2026, Calendar.JUNE), 10000);
        assertEquals(10000, h.centsFor(FOOD, m(2026, Calendar.JUNE)));
        assertEquals(70000, h.centsFor(FOOD, m(2026, Calendar.JULY)));
    }

    @Test
    public void overallAndCategoryBudgetsAreIndependent() {
        BudgetHistory h = new BudgetHistory();
        h.setForAllMonths(BudgetHistory.OVERALL, 200000);
        h.setForAllMonths(FOOD, 50000);
        h.setFrom(FOOD, m(2026, Calendar.OCTOBER), 0);
        assertEquals(200000, h.centsFor(BudgetHistory.OVERALL, m(2026, Calendar.OCTOBER)));
    }
}
