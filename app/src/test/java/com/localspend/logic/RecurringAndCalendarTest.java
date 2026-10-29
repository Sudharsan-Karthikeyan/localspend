package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Frequency;
import com.localspend.model.RecurringPayment;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class RecurringAndCalendarTest {

    private static long at(int year, int month, int day, int hour) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, hour, 0, 0);
        return c.getTimeInMillis();
    }

    private static int dayOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    private static int monthOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.MONTH);
    }

    private static Transaction expense(long cents, long date) {
        return new Transaction(cents, "Netflix", date, TransactionType.EXPENSE,
                Category.ENTERTAINMENT, Source.MANUAL);
    }

    // ---- frequencies ------------------------------------------------------------------------

    @Test
    public void monthlyKeepsTheDayAndClampsInShortMonths() {
        long jan31 = at(2027, Calendar.JANUARY, 31, 9);
        long feb = Frequency.MONTHLY.next(jan31, 31);
        assertEquals(Calendar.FEBRUARY, monthOf(feb));
        assertEquals(28, dayOf(feb));
        long mar = Frequency.MONTHLY.next(feb, 31);   // goes back to the 31st, not the 28th
        assertEquals(Calendar.MARCH, monthOf(mar));
        assertEquals(31, dayOf(mar));
    }

    @Test
    public void leapDayYearlyFallsOnTheLastDayOfFebruary() {
        long leap = at(2028, Calendar.FEBRUARY, 29, 9);
        long next = Frequency.YEARLY.next(leap, 29);
        assertEquals(Calendar.FEBRUARY, monthOf(next));
        assertEquals(28, dayOf(next));
    }

    @Test
    public void dailyWeeklyAndFortnightlyStepByDays() {
        long start = at(2026, Calendar.OCTOBER, 28, 9);
        assertEquals(29, dayOf(Frequency.DAILY.next(start, 28)));
        assertEquals(4, dayOf(Frequency.WEEKLY.next(start, 28)));
        assertEquals(11, dayOf(Frequency.FORTNIGHTLY.next(start, 28)));
        assertEquals(Calendar.NOVEMBER, monthOf(Frequency.WEEKLY.next(start, 28)));
    }

    // ---- catching up ------------------------------------------------------------------------

    @Test
    public void planAfterFirstPaymentIsDueOneStepLater() {
        Transaction first = expense(1599, at(2026, Calendar.OCTOBER, 21, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        assertEquals(Calendar.NOVEMBER, monthOf(plan.getNextDueMillis()));
        assertEquals(21, dayOf(plan.getNextDueMillis()));
        assertEquals(1, plan.getPaymentsMade());
        assertTrue(plan.isActive());
    }

    @Test
    public void nothingIsDueBeforeTheNextDate() {
        Transaction first = expense(1599, at(2026, Calendar.OCTOBER, 21, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        RecurringSchedule.Outcome o = RecurringSchedule.catchUp(plan, at(2026, Calendar.NOVEMBER, 20, 9));
        assertTrue(o.getDueDates().isEmpty());
        assertEquals(1, o.getPaymentsMade());
    }

    @Test
    public void missedPaymentsAreAllCaughtUp() {
        Transaction first = expense(1599, at(2026, Calendar.JANUARY, 15, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        // The phone was off from February until the middle of May: Feb, Mar, Apr, May are due.
        RecurringSchedule.Outcome o = RecurringSchedule.catchUp(plan, at(2026, Calendar.MAY, 20, 9));
        assertEquals(4, o.getDueDates().size());
        assertEquals(Calendar.FEBRUARY, monthOf(o.getDueDates().get(0)));
        assertEquals(Calendar.MAY, monthOf(o.getDueDates().get(3)));
        assertEquals(Calendar.JUNE, monthOf(o.getNextDueMillis()));
        assertEquals(5, o.getPaymentsMade());
        assertTrue(o.isActive());
    }

    @Test
    public void installmentPlanStopsAfterTheLastPayment() {
        Transaction first = expense(10000, at(2026, Calendar.JANUARY, 10, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, true, 3);
        // Payment 1 is the first transaction; two more are due, then it ends.
        RecurringSchedule.Outcome o = RecurringSchedule.catchUp(plan, at(2027, Calendar.DECEMBER, 1, 9));
        assertEquals(2, o.getDueDates().size());
        assertEquals(3, o.getPaymentsMade());
        assertFalse(o.isActive());
    }

    @Test
    public void pausedPlanProducesNothing() {
        Transaction first = expense(1599, at(2026, Calendar.JANUARY, 15, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        plan.setActive(false);
        assertTrue(RecurringSchedule.catchUp(plan, at(2026, Calendar.DECEMBER, 1, 9)).getDueDates().isEmpty());
    }

    @Test
    public void resumingSkipsWhatWasMissedWhilePaused() {
        Transaction first = expense(1599, at(2026, Calendar.JANUARY, 15, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        long resumed = RecurringSchedule.nextOnOrAfter(plan, at(2026, Calendar.MAY, 20, 9));
        assertEquals(Calendar.JUNE, monthOf(resumed));
        assertEquals(15, dayOf(resumed));
    }

    @Test
    public void catchUpIsLimitedSoADailyPlanCannotFloodTheDatabase() {
        Transaction first = expense(100, at(2000, Calendar.JANUARY, 1, 10));
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.DAILY, false, 0);
        RecurringSchedule.Outcome o = RecurringSchedule.catchUp(plan, at(2026, Calendar.JANUARY, 1, 10));
        assertEquals(RecurringSchedule.MAX_CATCH_UP, o.getDueDates().size());
    }

    // ---- calendar totals --------------------------------------------------------------------

    @Test
    public void dayTotalsSeparateIncomeSpendingAndNet() {
        long day21 = at(2026, Calendar.OCTOBER, 21, 12);
        Transaction income = new Transaction(20000, "Refund", day21, TransactionType.INCOME,
                Category.OTHER, Source.MANUAL);
        List<Transaction> list = Arrays.asList(expense(50000, day21), income,
                expense(1000, at(2026, Calendar.OCTOBER, 3, 8)),
                expense(999, at(2026, Calendar.NOVEMBER, 1, 8)));   // another month: ignored
        DayTotals totals = DayTotals.of(list, 2026, Calendar.OCTOBER);
        assertEquals(50000, totals.expenseOn(21));
        assertEquals(20000, totals.incomeOn(21));
        assertEquals(-30000, totals.netOn(21));
        assertEquals(1000, totals.expenseOn(3));
        assertEquals(0, totals.expenseOn(22));
        assertEquals(51000, totals.totalExpense());
        assertEquals(20000, totals.totalIncome());
        assertEquals(-31000, totals.totalNet());
    }

    @Test
    public void currencyIsKeptOnThePlan() {
        Transaction first = expense(19900, at(2026, Calendar.OCTOBER, 21, 10));
        first.setCurrency(Currency.INR);
        RecurringPayment plan = RecurringPayment.startingAfter(first, Frequency.MONTHLY, false, 0);
        assertEquals(Currency.INR, plan.getCurrency());
        assertEquals(19900, plan.getAmountCents());
    }
}
