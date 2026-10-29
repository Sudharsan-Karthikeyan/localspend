package com.localspend.logic;

import com.localspend.model.Category;
import com.localspend.model.Currency;

/** How much of a monthly budget has been used. The category is null for the overall budget. */
public final class BudgetStatus {

    /** Where spending stands against the budget. */
    public enum Level {
        OK, WARNING, EXCEEDED
    }

    private final Category category;
    private final long spentCents;
    private final long budgetCents;
    private final Currency currency;

    BudgetStatus(Category category, long spentCents, long budgetCents) {
        this(category, spentCents, budgetCents, Currency.defaultCurrency());
    }

    BudgetStatus(Category category, long spentCents, long budgetCents, Currency currency) {
        if (budgetCents <= 0) {
            throw new IllegalArgumentException("Budget must be greater than zero");
        }
        this.category = category;
        this.spentCents = spentCents;
        this.budgetCents = budgetCents;
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
    }

    /** Currency of both the budget and the spending it is compared with. */
    public Currency getCurrency() {
        return currency;
    }

    /** The category, or null for the overall monthly budget. */
    public Category getCategory() {
        return category;
    }

    public boolean isOverall() {
        return category == null;
    }

    public long getSpentCents() {
        return spentCents;
    }

    public long getBudgetCents() {
        return budgetCents;
    }

    /** Budget left; negative when over budget. */
    public long getRemainingCents() {
        return budgetCents - spentCents;
    }

    /** Share of the budget used, rounded; can exceed 100. */
    public int getPercentUsed() {
        return (int) Math.round(spentCents * 100.0 / budgetCents);
    }

    public boolean isExceeded() {
        return spentCents > budgetCents;
    }

    public Level getLevel(int warnPercent) {
        return BudgetTracker.levelFor(spentCents, budgetCents, warnPercent);
    }
}
