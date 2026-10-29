package com.localspend.logic;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns a list of transactions into the numbers shown on the dashboard and history screens. */
public class SpendingAnalyzer {

    /**
     * Everything that can be counted in one currency: transactions in it, plus those converted
     * into it (shown at their converted amount). Anything not yet converted is left out, so
     * currencies are never added together unconverted.
     */
    public static List<Transaction> inCurrency(List<Transaction> all, Currency currency) {
        List<Transaction> out = new ArrayList<>();
        // Converted amounts are rounded as a running total (separately for money out and in), so
        // that the sum of the rounded amounts equals the rounded sum of the exact ones. Rounding
        // each payment alone would let tiny errors pile up: two payments of 1.47 cents would each
        // become 1 cent and total 2, when together they are 2.94, which is 3.
        java.math.BigDecimal[] exact = {java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO};
        long[] rounded = {0, 0};
        for (Transaction t : all) {
            if (!t.canBeShownIn(currency)) {
                continue;
            }
            if (t.getCurrency() == currency) {
                out.add(t);
                continue;
            }
            int k = t.isExpense() ? 0 : 1;
            exact[k] = exact[k].add(java.math.BigDecimal.valueOf(t.getAmountCents())
                    .multiply(t.getConversionRate()));
            long target = exact[k].setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            out.add(t.asIn(currency, target - rounded[k]));
            rounded[k] = target;
        }
        return out;
    }

    /** Transactions that fall in the given calendar month (month is 0-based). */
    public List<Transaction> transactionsInMonth(List<Transaction> all, int year, int month) {
        List<Transaction> out = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        for (Transaction t : all) {
            c.setTimeInMillis(t.getDateMillis());
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                out.add(t);
            }
        }
        return out;
    }

    public MonthSummary summarise(List<Transaction> all, int year, int month) {
        Map<Category, Long> byCategory = new LinkedHashMap<>();
        long spent = 0;
        long income = 0;
        List<Transaction> inMonth = transactionsInMonth(all, year, month);
        for (Transaction t : inMonth) {
            if (t.isExpense()) {
                spent += t.getAmountCents();
                Long previous = byCategory.get(t.getCategory());
                byCategory.put(t.getCategory(),
                        (previous == null ? 0 : previous) + t.getAmountCents());
            } else {
                income += t.getAmountCents();
            }
        }
        return new MonthSummary(year, month, spent, income, inMonth.size(), byCategory);
    }

    /** Categories that had spending in either month, biggest current spend first. */
    public List<CategoryComparison> compare(MonthSummary current, MonthSummary previous) {
        List<CategoryComparison> out = new ArrayList<>();
        Set<Category> categories = new LinkedHashSet<>(current.getCategories());
        categories.addAll(previous.getCategories());
        for (Category category : categories) {
            long now = current.getSpentCents(category);
            long before = previous.getSpentCents(category);
            if (now > 0 || before > 0) {
                out.add(new CategoryComparison(category, now, before));
            }
        }
        Collections.sort(out, (a, b) -> {
            int byCurrent = Long.compare(b.getCurrentCents(), a.getCurrentCents());
            return byCurrent != 0 ? byCurrent : Long.compare(b.getPreviousCents(), a.getPreviousCents());
        });
        return out;
    }

    /** Spending for {@code count} months starting at the given month, oldest first. */
    public List<MonthTotal> monthsFrom(List<Transaction> all, int startYear, int startMonth, int count) {
        List<MonthTotal> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int[] ym = Dates.shiftMonth(startYear, startMonth, i);
            out.add(new MonthTotal(ym[0], ym[1],
                    summarise(all, ym[0], ym[1]).getTotalSpentCents()));
        }
        return out;
    }

    /** Spending for the {@code count} months ending at the given month, oldest first. */
    public List<MonthTotal> lastMonths(List<Transaction> all, int endYear, int endMonth, int count) {
        List<MonthTotal> out = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            int[] ym = Dates.shiftMonth(endYear, endMonth, -i);
            out.add(new MonthTotal(ym[0], ym[1],
                    summarise(all, ym[0], ym[1]).getTotalSpentCents()));
        }
        return out;
    }

    /**
     * Plain-language comparison such as "12% more than last month (+$150.00)".
     *
     * @param reference how to name the other period, for example "last month"
     */
    public String describeChange(long currentCents, long previousCents, String reference) {
        return describeChange(currentCents, previousCents, reference, Currency.defaultCurrency());
    }

    public String describeChange(long currentCents, long previousCents, String reference,
                                 Currency currency) {
        if (previousCents == 0) {
            return "No spending recorded in " + reference;
        }
        long delta = currentCents - previousCents;
        if (delta == 0) {
            return "Same as " + reference;
        }
        long percent = Math.round(Math.abs(delta) * 100.0 / previousCents);
        String direction = delta > 0 ? "more" : "less";
        String sign = delta > 0 ? "+" : "-";
        return percent + "% " + direction + " than " + reference
                + " (" + sign + MoneyFormatter.format(Math.abs(delta), currency) + ")";
    }
}
