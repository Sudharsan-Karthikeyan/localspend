package com.localspend.logic;

import com.localspend.model.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Totals for one calendar month. Only expenses count towards category spending. */
public final class MonthSummary {

    /** One row of the category breakdown. */
    public static final class CategoryTotal {
        private final Category category;
        private final long cents;
        private final int percent;

        CategoryTotal(Category category, long cents, int percent) {
            this.category = category;
            this.cents = cents;
            this.percent = percent;
        }

        public Category getCategory() {
            return category;
        }

        public long getCents() {
            return cents;
        }

        /** Share of the month's spending, 0 to 100. */
        public int getPercent() {
            return percent;
        }
    }

    private final int year;
    private final int month;
    private final long totalSpentCents;
    private final long totalIncomeCents;
    private final int transactionCount;
    private final Map<Category, Long> spentByCategory;

    MonthSummary(int year, int month, long totalSpentCents, long totalIncomeCents,
                 int transactionCount, Map<Category, Long> spentByCategory) {
        this.year = year;
        this.month = month;
        this.totalSpentCents = totalSpentCents;
        this.totalIncomeCents = totalIncomeCents;
        this.transactionCount = transactionCount;
        this.spentByCategory = Collections.unmodifiableMap(new LinkedHashMap<>(spentByCategory));
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public long getTotalSpentCents() {
        return totalSpentCents;
    }

    public long getTotalIncomeCents() {
        return totalIncomeCents;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public long getSpentCents(Category category) {
        Long value = spentByCategory.get(category);
        return value == null ? 0 : value;
    }

    /** Categories with spending, biggest first. */
    public List<CategoryTotal> getCategoryTotals() {
        List<CategoryTotal> out = new ArrayList<>();
        for (Map.Entry<Category, Long> e : spentByCategory.entrySet()) {
            long cents = e.getValue();
            if (cents > 0) {
                int percent = totalSpentCents == 0 ? 0
                        : (int) Math.round(cents * 100.0 / totalSpentCents);
                out.add(new CategoryTotal(e.getKey(), cents, percent));
            }
        }
        Collections.sort(out, (a, b) -> {
            int byAmount = Long.compare(b.cents, a.cents);
            return byAmount != 0 ? byAmount : a.category.getLabel().compareTo(b.category.getLabel());
        });
        return out;
    }

    /** Every category that had spending this month. */
    public List<Category> getCategories() {
        return new ArrayList<>(spentByCategory.keySet());
    }
}
