package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class BudgetAndInsightsTest {

    private final SpendingAnalyzer analyzer = new SpendingAnalyzer();
    private final BudgetTracker tracker = new BudgetTracker();

    private static long date(int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, month, day, 12, 0, 0);
        return c.getTimeInMillis();
    }

    private static Transaction tx(long cents, String merchant, Category cat, int month, int day) {
        return new Transaction(cents, merchant, date(month, day), TransactionType.EXPENSE, cat, Source.MANUAL);
    }

    private static CategoryCatalog budgets() {
        return new CategoryCatalog(Arrays.asList(
                Category.FOOD_AND_DINING.withBudgetCents(40000),
                Category.GROCERIES.withBudgetCents(50000),
                Category.TRANSPORT,
                Category.OTHER));
    }

    private List<Transaction> august() {
        List<Transaction> all = new ArrayList<>();
        all.add(tx(25000, "Pizza Place", Category.FOOD_AND_DINING, Calendar.AUGUST, 3));
        all.add(tx(20000, "Starbucks", Category.FOOD_AND_DINING, Calendar.AUGUST, 9));
        all.add(tx(30000, "Woolworths 1234", Category.GROCERIES, Calendar.AUGUST, 4));
        all.add(tx(5000, "Woolworths 5678", Category.GROCERIES, Calendar.AUGUST, 11));
        all.add(tx(4000, "Opal", Category.TRANSPORT, Calendar.AUGUST, 10));
        all.add(tx(1000, "Mystery", Category.OTHER, Calendar.AUGUST, 12));
        all.add(new Transaction(200000, "Salary", date(Calendar.AUGUST, 1), TransactionType.INCOME,
                Category.OTHER, Source.MANUAL));
        // July
        all.add(tx(10000, "Pizza Place", Category.FOOD_AND_DINING, Calendar.JULY, 3));
        all.add(tx(20000, "Opal", Category.TRANSPORT, Calendar.JULY, 3));
        return all;
    }

    @Test
    public void statusesListOverallFirstThenMostUsed() {
        MonthSummary aug = analyzer.summarise(august(), 2026, Calendar.AUGUST);
        List<BudgetStatus> list = tracker.statuses(aug, budgets(), 100000);
        assertEquals(3, list.size());
        assertTrue(list.get(0).isOverall());
        assertEquals(85000, list.get(0).getSpentCents());
        assertEquals(Category.FOOD_AND_DINING, list.get(1).getCategory());   // 45000 of 40000
        assertTrue(list.get(1).isExceeded());
        assertEquals(113, list.get(1).getPercentUsed());
        assertEquals(-5000, list.get(1).getRemainingCents());
        assertEquals(Category.GROCERIES, list.get(2).getCategory());         // 35000 of 50000
        assertEquals(70, list.get(2).getPercentUsed());
    }

    @Test
    public void budgetsFollowTheDashboardCurrencyWhateverCurrencyTheyWereSavedIn() {
        MonthSummary aug = analyzer.summarise(august(), 2026, Calendar.AUGUST);
        // An overall budget saved as dollars still measures a rupee dashboard.
        List<BudgetStatus> list = tracker.statuses(aug, budgets(), 100000,
                com.localspend.model.Currency.AUD, com.localspend.model.Currency.INR);
        assertEquals(3, list.size());
        assertEquals(com.localspend.model.Currency.INR, list.get(0).getCurrency());
        assertEquals(com.localspend.model.Currency.INR, list.get(1).getCurrency());
    }

    @Test
    public void noBudgetsMeansNoStatuses() {
        MonthSummary aug = analyzer.summarise(august(), 2026, Calendar.AUGUST);
        assertTrue(tracker.statuses(aug, CategoryCatalog.defaults(), 0).isEmpty());
    }

    @Test
    public void levelsFollowTheWarningPoint() {
        assertEquals(BudgetStatus.Level.OK, BudgetTracker.levelFor(7999, 10000, 80));
        assertEquals(BudgetStatus.Level.WARNING, BudgetTracker.levelFor(8000, 10000, 80));
        assertEquals(BudgetStatus.Level.WARNING, BudgetTracker.levelFor(10000, 10000, 80));
        assertEquals(BudgetStatus.Level.EXCEEDED, BudgetTracker.levelFor(10001, 10000, 80));
        assertEquals(BudgetStatus.Level.OK, BudgetTracker.levelFor(99999, 0, 80));
    }

    @Test
    public void projectionScalesSpendingToTheWholeMonth() {
        assertEquals(31000, BudgetTracker.projectMonthTotal(10000, 10, 31));
        assertEquals(10000, BudgetTracker.projectMonthTotal(10000, 31, 31));
        assertEquals(10000, BudgetTracker.projectMonthTotal(10000, 0, 31));
    }

    private List<Insight> insights(int daysElapsed, long overallBudget) {
        List<Transaction> all = august();
        MonthSummary aug = analyzer.summarise(all, 2026, Calendar.AUGUST);
        MonthSummary jul = analyzer.summarise(all, 2026, Calendar.JULY);
        List<BudgetStatus> statuses = tracker.statuses(aug, budgets(), overallBudget);
        return new InsightGenerator().generate(analyzer.transactionsInMonth(all, 2026, Calendar.AUGUST),
                aug, jul, statuses, daysElapsed, 31, overallBudget);
    }

    private static Insight find(List<Insight> list, String fragment) {
        for (Insight i : list) {
            if (i.getText().contains(fragment)) {
                return i;
            }
        }
        return null;
    }

    @Test
    public void insightsForAnOngoingMonthWarnWhenPaceBeatsBudget() {
        List<Insight> list = insights(12, 150000);   // $850 in 12 days -> about $2,196
        Insight pace = find(list, "At this pace");
        assertEquals(Insight.Kind.WARNING, pace.getKind());
        assertTrue(pace.getText(), pace.getText().contains("$2,196"));
        assertEquals(Insight.Kind.WARNING, find(list, "Over budget: Food and Dining").getKind());
    }

    @Test
    public void insightsCoverChangesBiggestTopMerchantSavingsAndOther() {
        List<Insight> list = insights(0, 0);   // a finished month
        assertTrue(find(list, "average of").getText().contains("$27.42"));   // 85000 / 31 days
        assertEquals(Insight.Kind.WARNING, find(list, "Food and Dining is up $350.00 (+350%)").getKind());
        assertEquals(Insight.Kind.GOOD, find(list, "Transport is down $160.00").getKind());
        assertTrue(find(list, "Biggest expense: $300.00 at Woolworths 1234") != null);
        // Woolworths 1234 and 5678 are the same merchant: $350 over 2 payments.
        assertTrue(find(list, "the most at Woolworths 1234: $350.00 over 2 payments") != null);
        assertEquals(Insight.Kind.GOOD, find(list, "You kept $1,150.00 (58%)").getKind());
        assertTrue(find(list, "1 expense is still in Other") != null);
        assertNull(find(list, "At this pace"));
    }

    @Test
    public void emptyMonthHasOneFriendlyInsight() {
        MonthSummary empty = analyzer.summarise(new ArrayList<>(), 2026, Calendar.MAY);
        List<Insight> list = new InsightGenerator().generate(new ArrayList<>(), empty, empty,
                new ArrayList<>(), 5, 31, 0);
        assertEquals(1, list.size());
        assertFalse(list.get(0).getText().isEmpty());
    }
}
