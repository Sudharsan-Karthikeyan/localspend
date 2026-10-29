package com.localspend.logic;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a month of transactions into plain-language spending insights: pace and projection,
 * the biggest expense, the most-visited merchant, categories that rose or fell, weekend spending,
 * savings rate, budgets that were exceeded, and expenses still waiting for a category.
 *
 * Everything is calculated on the device from the local database; nothing is used to suggest
 * purchases or adverts.
 */
public class InsightGenerator {

    private static final int MIN_FOR_WEEKEND_INSIGHT = 5;

    private Currency currency = Currency.defaultCurrency();

    private String money(long cents) {
        return MoneyFormatter.format(cents, currency);
    }

    private String moneyWhole(long cents) {
        return MoneyFormatter.formatWhole(cents, currency);
    }

    /**
     * @param inMonth            the month's transactions (expenses and income)
     * @param current            summary of the month
     * @param previous           summary of the month before
     * @param budgets            budget statuses for the month (may be empty)
     * @param daysElapsed        days of the month so far including today, or 0 for a past month
     * @param daysInMonth        length of the month
     * @param overallBudgetCents the overall monthly budget, or 0 if none
     */
    public List<Insight> generate(List<Transaction> inMonth, MonthSummary current,
                                  MonthSummary previous, List<BudgetStatus> budgets,
                                  int daysElapsed, int daysInMonth, long overallBudgetCents) {
        return generate(inMonth, current, previous, budgets, daysElapsed, daysInMonth,
                overallBudgetCents, Currency.defaultCurrency());
    }

    /** As above, for spending in one currency; amounts are written in that currency. */
    public List<Insight> generate(List<Transaction> inMonth, MonthSummary current,
                                  MonthSummary previous, List<BudgetStatus> budgets,
                                  int daysElapsed, int daysInMonth, long overallBudgetCents,
                                  Currency currency) {
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
        List<Insight> out = new ArrayList<>();
        long spent = current.getTotalSpentCents();
        boolean ongoing = daysElapsed > 0 && daysElapsed < daysInMonth;

        if (spent == 0) {
            out.add(new Insight(Insight.Kind.INFO, ongoing
                    ? "No spending recorded yet this month."
                    : "No spending recorded in this month."));
            return out;
        }

        addPace(out, spent, ongoing, daysElapsed, daysInMonth, overallBudgetCents);
        addBudgetWarnings(out, budgets);
        addCategoryChanges(out, current, previous);
        addBiggestExpense(out, inMonth);
        addTopMerchant(out, inMonth);
        addSavings(out, current);
        addWeekendShare(out, inMonth);
        addNeedsCategory(out, inMonth);
        return out;
    }

    private void addPace(List<Insight> out, long spent, boolean ongoing, int daysElapsed,
                         int daysInMonth, long overallBudgetCents) {
        int days = ongoing ? daysElapsed : daysInMonth;
        long perDay = Math.round(spent / (double) Math.max(1, days));
        if (!ongoing) {
            out.add(new Insight(Insight.Kind.INFO,
                    "You spent an average of " + money(perDay) + " a day."));
            return;
        }
        long projected = BudgetTracker.projectMonthTotal(spent, daysElapsed, daysInMonth);
        String pace = "Averaging " + money(perDay) + " a day. At this pace you'll spend about "
                + moneyWhole(projected) + " this month";
        if (overallBudgetCents <= 0) {
            out.add(new Insight(Insight.Kind.INFO, pace + "."));
        } else if (projected > overallBudgetCents) {
            out.add(new Insight(Insight.Kind.WARNING, pace + ", "
                    + moneyWhole(projected - overallBudgetCents)
                    + " over your " + moneyWhole(overallBudgetCents) + " budget."));
        } else {
            out.add(new Insight(Insight.Kind.GOOD, pace + ", within your "
                    + moneyWhole(overallBudgetCents) + " budget."));
        }
    }

    private void addBudgetWarnings(List<Insight> out, List<BudgetStatus> budgets) {
        if (budgets == null) {
            return;
        }
        for (BudgetStatus b : budgets) {
            if (b.isExceeded()) {
                String name = b.isOverall() ? "your monthly budget" : b.getCategory().getLabel();
                out.add(new Insight(Insight.Kind.WARNING, "Over budget: " + name + " ("
                        + money(b.getSpentCents()) + " of "
                        + money(b.getBudgetCents()) + ")."));
            }
        }
    }

    private void addCategoryChanges(List<Insight> out, MonthSummary current, MonthSummary previous) {
        if (previous.getTotalSpentCents() == 0) {
            return;
        }
        CategoryComparison rise = null;
        CategoryComparison drop = null;
        for (CategoryComparison c : new SpendingAnalyzer().compare(current, previous)) {
            if (c.getDeltaCents() > 0 && (rise == null || c.getDeltaCents() > rise.getDeltaCents())) {
                rise = c;
            }
            if (c.getDeltaCents() < 0 && (drop == null || c.getDeltaCents() < drop.getDeltaCents())) {
                drop = c;
            }
        }
        if (rise != null) {
            Integer pct = rise.getPercentChange();
            out.add(new Insight(Insight.Kind.WARNING, rise.getCategory().getLabel() + " is up "
                    + money(rise.getDeltaCents())
                    + (pct == null ? " (nothing last month)" : " (+" + pct + "%)")
                    + " on the month before."));
        }
        if (drop != null) {
            out.add(new Insight(Insight.Kind.GOOD, drop.getCategory().getLabel() + " is down "
                    + money(-drop.getDeltaCents()) + " ("
                    + drop.getPercentChange() + "%) on the month before."));
        }
    }

    private void addBiggestExpense(List<Insight> out, List<Transaction> inMonth) {
        Transaction biggest = null;
        for (Transaction t : inMonth) {
            if (t.isExpense() && (biggest == null || t.getAmountCents() > biggest.getAmountCents())) {
                biggest = t;
            }
        }
        if (biggest != null) {
            out.add(new Insight(Insight.Kind.INFO, "Biggest expense: "
                    + money(biggest.getAmountCents()) + " at " + biggest.getMerchant()
                    + " on " + Dates.formatDay(biggest.getDateMillis()) + "."));
        }
    }

    private void addTopMerchant(List<Insight> out, List<Transaction> inMonth) {
        Map<String, long[]> byMerchant = new LinkedHashMap<>();   // key -> {cents, count}
        Map<String, String> names = new LinkedHashMap<>();
        for (Transaction t : inMonth) {
            if (!t.isExpense()) {
                continue;
            }
            String key = MerchantNormaliser.merchantKey(t.getMerchant());
            long[] totals = byMerchant.get(key);
            if (totals == null) {
                totals = new long[2];
                byMerchant.put(key, totals);
                names.put(key, t.getMerchant());
            }
            totals[0] += t.getAmountCents();
            totals[1]++;
        }
        String topKey = null;
        for (Map.Entry<String, long[]> e : byMerchant.entrySet()) {
            if (e.getValue()[1] >= 2
                    && (topKey == null || e.getValue()[0] > byMerchant.get(topKey)[0])) {
                topKey = e.getKey();
            }
        }
        if (topKey != null) {
            long[] totals = byMerchant.get(topKey);
            out.add(new Insight(Insight.Kind.INFO, "You spent the most at " + names.get(topKey) + ": "
                    + money(totals[0]) + " over " + totals[1] + " payments."));
        }
    }

    private void addSavings(List<Insight> out, MonthSummary current) {
        long income = current.getTotalIncomeCents();
        if (income <= 0) {
            return;
        }
        long saved = income - current.getTotalSpentCents();
        if (saved >= 0) {
            int pct = (int) Math.round(saved * 100.0 / income);
            out.add(new Insight(Insight.Kind.GOOD, "You kept " + money(saved)
                    + " (" + pct + "%) of your income."));
        } else {
            out.add(new Insight(Insight.Kind.WARNING, "You spent " + money(-saved)
                    + " more than you earned."));
        }
    }

    private void addWeekendShare(List<Insight> out, List<Transaction> inMonth) {
        long total = 0;
        long weekend = 0;
        int count = 0;
        Calendar c = Calendar.getInstance();
        for (Transaction t : inMonth) {
            if (!t.isExpense()) {
                continue;
            }
            count++;
            total += t.getAmountCents();
            c.setTimeInMillis(t.getDateMillis());
            int day = c.get(Calendar.DAY_OF_WEEK);
            if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
                weekend += t.getAmountCents();
            }
        }
        if (count >= MIN_FOR_WEEKEND_INSIGHT && total > 0) {
            int pct = (int) Math.round(weekend * 100.0 / total);
            out.add(new Insight(Insight.Kind.INFO, pct + "% of your spending was on weekends."));
        }
    }

    private void addNeedsCategory(List<Insight> out, List<Transaction> inMonth) {
        int uncategorised = 0;
        for (Transaction t : inMonth) {
            if (t.isExpense() && Category.OTHER_KEY.equals(t.getCategory().getKey())
                    && !t.isCategoryChosenByUser()) {
                uncategorised++;
            }
        }
        if (uncategorised > 0) {
            out.add(new Insight(Insight.Kind.INFO, uncategorised
                    + (uncategorised == 1 ? " expense is" : " expenses are")
                    + " still in Other. Tap one to choose a category; LocalSpend will remember it."));
        }
    }
}
