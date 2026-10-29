package com.localspend.logic;

import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares a month's spending with the user's budgets: one optional overall monthly limit and an
 * optional limit per category.
 */
public class BudgetTracker {

    /** Default point at which a budget warning is raised. */
    public static final int DEFAULT_WARN_PERCENT = 80;

    /**
     * Budget progress for the month: the overall budget first (if set), then each category that
     * has a budget, the most used first.
     */
    public List<BudgetStatus> statuses(MonthSummary summary, CategoryCatalog catalog,
                                       long overallBudgetCents) {
        Currency aud = Currency.defaultCurrency();
        return statuses(summary, catalog, overallBudgetCents, aud, aud);
    }

    /**
     * Budget progress for a month of spending in one currency. Budgets follow the currency the
     * dashboard shows: a budget of 5,000 is ₹5,000 when the dashboard is in rupees and A$5,000
     * when it is in Australian dollars, so no currency has to be chosen for a budget.
     *
     * @param summary          spending in {@code currency} only
     * @param overallCurrency  not used (kept so existing callers still compile)
     * @param currency         currency of {@code summary}, which every budget is measured in
     */
    public List<BudgetStatus> statuses(MonthSummary summary, CategoryCatalog catalog,
                                       long overallBudgetCents, Currency overallCurrency,
                                       Currency currency) {
        List<BudgetStatus> categories = new ArrayList<>();
        for (Category c : catalog.all()) {
            if (c.hasBudget()) {
                categories.add(new BudgetStatus(c, summary.getSpentCents(c), c.getBudgetCents(),
                        currency));
            }
        }
        Collections.sort(categories, (a, b) -> {
            int byPercent = Integer.compare(b.getPercentUsed(), a.getPercentUsed());
            return byPercent != 0 ? byPercent
                    : a.getCategory().getLabel().compareTo(b.getCategory().getLabel());
        });

        List<BudgetStatus> out = new ArrayList<>();
        if (overallBudgetCents > 0) {
            out.add(new BudgetStatus(null, summary.getTotalSpentCents(), overallBudgetCents,
                    currency));
        }
        out.addAll(categories);
        return out;
    }

    /**
     * Budget progress for a particular month, using the budget that applied in that month.
     *
     * @param summary spending in {@code currency} for {@code year}/{@code month} (0-based month)
     */
    public List<BudgetStatus> statuses(MonthSummary summary, CategoryCatalog catalog,
                                       BudgetHistory history, int year, int month, Currency currency) {
        int index = BudgetHistory.monthIndex(year, month);
        List<BudgetStatus> categories = new ArrayList<>();
        for (Category c : catalog.all()) {
            long budget = history.centsFor(c.getKey(), index);
            if (budget > 0) {
                categories.add(new BudgetStatus(c, summary.getSpentCents(c), budget, currency));
            }
        }
        Collections.sort(categories, (a, b) -> {
            int byPercent = Integer.compare(b.getPercentUsed(), a.getPercentUsed());
            return byPercent != 0 ? byPercent
                    : a.getCategory().getLabel().compareTo(b.getCategory().getLabel());
        });
        List<BudgetStatus> out = new ArrayList<>();
        long overall = history.centsFor(BudgetHistory.OVERALL, index);
        if (overall > 0) {
            out.add(new BudgetStatus(null, summary.getTotalSpentCents(), overall, currency));
        }
        out.addAll(categories);
        return out;
    }

    /** OK below the warning point, WARNING from it up to the budget, EXCEEDED above the budget. */
    public static BudgetStatus.Level levelFor(long spentCents, long budgetCents, int warnPercent) {
        if (budgetCents <= 0) {
            return BudgetStatus.Level.OK;
        }
        if (spentCents > budgetCents) {
            return BudgetStatus.Level.EXCEEDED;
        }
        // Integer maths: spent/budget >= warn/100.
        if (spentCents * 100 >= budgetCents * (long) warnPercent) {
            return BudgetStatus.Level.WARNING;
        }
        return BudgetStatus.Level.OK;
    }

    /**
     * Linear projection of the month's total from spending so far.
     *
     * @param daysElapsed days of the month that have started, including today (at least 1)
     * @param daysInMonth length of the month
     */
    public static long projectMonthTotal(long spentSoFarCents, int daysElapsed, int daysInMonth) {
        if (daysElapsed <= 0 || daysElapsed >= daysInMonth) {
            return spentSoFarCents;
        }
        return Math.round(spentSoFarCents * (double) daysInMonth / daysElapsed);
    }
}
