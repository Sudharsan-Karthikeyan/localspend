package com.localspend.logic;

import com.localspend.model.Category;

/** One category's spending in two months, for the month-versus-month view. */
public final class CategoryComparison {

    private final Category category;
    private final long currentCents;
    private final long previousCents;

    CategoryComparison(Category category, long currentCents, long previousCents) {
        this.category = category;
        this.currentCents = currentCents;
        this.previousCents = previousCents;
    }

    public Category getCategory() {
        return category;
    }

    public long getCurrentCents() {
        return currentCents;
    }

    public long getPreviousCents() {
        return previousCents;
    }

    /** Current minus previous; positive means spending went up. */
    public long getDeltaCents() {
        return currentCents - previousCents;
    }

    /** Percentage change, or null when there was no spending in the previous month. */
    public Integer getPercentChange() {
        if (previousCents == 0) {
            return null;
        }
        return (int) Math.round(getDeltaCents() * 100.0 / previousCents);
    }
}
