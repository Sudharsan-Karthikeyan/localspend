package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.localspend.model.Category;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SpendingAnalyzerTest {

    private final SpendingAnalyzer analyzer = new SpendingAnalyzer();

    private static long date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, 12, 0, 0);
        return c.getTimeInMillis();
    }

    private static Transaction tx(long cents, Category cat, TransactionType type, long date) {
        return new Transaction(cents, "Test", date, type, cat, Source.MANUAL);
    }

    private List<Transaction> sample() {
        List<Transaction> all = new ArrayList<>();
        // August 2026
        all.add(tx(32000, Category.FOOD_AND_DINING, TransactionType.EXPENSE, date(2026, Calendar.AUGUST, 3)));
        all.add(tx(18000, Category.TRANSPORT, TransactionType.EXPENSE, date(2026, Calendar.AUGUST, 10)));
        all.add(tx(25000, Category.SHOPPING, TransactionType.EXPENSE, date(2026, Calendar.AUGUST, 15)));
        all.add(tx(30000, Category.BILLS_AND_UTILITIES, TransactionType.EXPENSE, date(2026, Calendar.AUGUST, 20)));
        all.add(tx(20000, Category.OTHER, TransactionType.EXPENSE, date(2026, Calendar.AUGUST, 31)));
        all.add(tx(500000, Category.OTHER, TransactionType.INCOME, date(2026, Calendar.AUGUST, 25)));
        // July 2026
        all.add(tx(28000, Category.FOOD_AND_DINING, TransactionType.EXPENSE, date(2026, Calendar.JULY, 5)));
        all.add(tx(9000, Category.TRANSPORT, TransactionType.EXPENSE, date(2026, Calendar.JULY, 30)));
        // September 2026
        all.add(tx(1000, Category.GROCERIES, TransactionType.EXPENSE, date(2026, Calendar.SEPTEMBER, 1)));
        return all;
    }

    @Test
    public void monthTotalMatchesTheReportExample() {
        MonthSummary aug = analyzer.summarise(sample(), 2026, Calendar.AUGUST);
        assertEquals(125000, aug.getTotalSpentCents());   // $1,250 as in the proposal
        assertEquals(500000, aug.getTotalIncomeCents());
        assertEquals(6, aug.getTransactionCount());
        assertEquals(32000, aug.getSpentCents(Category.FOOD_AND_DINING));
        assertEquals(0, aug.getSpentCents(Category.GROCERIES));
    }

    @Test
    public void categoryTotalsAreSortedAndHavePercentages() {
        List<MonthSummary.CategoryTotal> totals =
                analyzer.summarise(sample(), 2026, Calendar.AUGUST).getCategoryTotals();
        assertEquals(5, totals.size());
        assertEquals(Category.FOOD_AND_DINING, totals.get(0).getCategory());
        assertEquals(26, totals.get(0).getPercent());      // 320 / 1250 = 25.6%
        assertEquals(Category.TRANSPORT, totals.get(4).getCategory());
    }

    @Test
    public void incomeIsNotCountedAsSpending() {
        MonthSummary aug = analyzer.summarise(sample(), 2026, Calendar.AUGUST);
        assertEquals(20000, aug.getSpentCents(Category.OTHER));
    }

    @Test
    public void emptyMonthIsAllZero() {
        MonthSummary may = analyzer.summarise(sample(), 2026, Calendar.MAY);
        assertEquals(0, may.getTotalSpentCents());
        assertEquals(0, may.getCategoryTotals().size());
    }

    @Test
    public void comparisonShowsBothMonths() {
        List<Transaction> all = sample();
        List<CategoryComparison> cmp = analyzer.compare(
                analyzer.summarise(all, 2026, Calendar.AUGUST),
                analyzer.summarise(all, 2026, Calendar.JULY));
        CategoryComparison food = cmp.get(0);
        assertEquals(Category.FOOD_AND_DINING, food.getCategory());
        assertEquals(4000, food.getDeltaCents());
        assertEquals(Integer.valueOf(14), food.getPercentChange());

        CategoryComparison shopping = null;
        for (CategoryComparison c : cmp) {
            if (c.getCategory() == Category.SHOPPING) {
                shopping = c;
            }
        }
        assertEquals(0, shopping.getPreviousCents());
        assertNull(shopping.getPercentChange());
    }

    @Test
    public void lastMonthsAreOldestFirstAndCrossYears() {
        List<MonthTotal> months = analyzer.lastMonths(sample(), 2026, Calendar.SEPTEMBER, 3);
        assertEquals(3, months.size());
        assertEquals(Calendar.JULY, months.get(0).getMonth());
        assertEquals(37000, months.get(0).getSpentCents());
        assertEquals(125000, months.get(1).getSpentCents());
        assertEquals(1000, months.get(2).getSpentCents());

        List<MonthTotal> wrap = analyzer.lastMonths(new ArrayList<Transaction>(), 2026, Calendar.JANUARY, 3);
        assertEquals(2025, wrap.get(0).getYear());
        assertEquals(Calendar.NOVEMBER, wrap.get(0).getMonth());
        assertEquals(2026, wrap.get(2).getYear());
    }

    @Test
    public void describeChangeReadsNaturally() {
        assertEquals("12% more than last month (+$150.00)",
                analyzer.describeChange(140000, 125000, "last month"));
        assertEquals("20% less than last month (-$250.00)",
                analyzer.describeChange(100000, 125000, "last month"));
        assertEquals("Same as last month", analyzer.describeChange(500, 500, "last month"));
        assertEquals("No spending recorded in last month", analyzer.describeChange(500, 0, "last month"));
    }
}
