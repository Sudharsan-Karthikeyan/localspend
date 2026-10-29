package com.localspend.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A spending category. The nine categories from the project proposal (plus Bank Transactions, for
 * messages that name no merchant) are built in, and users can
 * rename them, change their colour, give them a monthly budget, or add categories of their own.
 *
 * Categories are identified by a stable {@code key} (stored in the database), so renaming a
 * category never breaks the transactions already filed under it. Instances are immutable; use the
 * {@code with...} methods to get a changed copy.
 */
public final class Category {

    public static final String OTHER_KEY = "OTHER";
    public static final String BANK_TRANSACTIONS_KEY = "BANK_TRANSACTIONS";
    private static final String CUSTOM_PREFIX = "CUSTOM_";

    // Built-in categories. The keys match the enum names used by version 1 of the database.
    public static final Category FOOD_AND_DINING = builtIn("FOOD_AND_DINING", "Food and Dining", 0xFFE4572E);
    public static final Category GROCERIES = builtIn("GROCERIES", "Groceries", 0xFF4CAF50);
    public static final Category TRANSPORT = builtIn("TRANSPORT", "Transport", 0xFF2196F3);
    public static final Category SHOPPING = builtIn("SHOPPING", "Shopping", 0xFFAB47BC);
    public static final Category ENTERTAINMENT = builtIn("ENTERTAINMENT", "Entertainment", 0xFFFFB300);
    public static final Category BILLS_AND_UTILITIES = builtIn("BILLS_AND_UTILITIES", "Bills and Utilities", 0xFF607D8B);
    public static final Category HEALTH = builtIn("HEALTH", "Health", 0xFFE91E63);
    public static final Category EDUCATION = builtIn("EDUCATION", "Education", 0xFF009688);
    /** Bank messages that did not say who was paid, so there is nothing to categorise by. */
    public static final Category BANK_TRANSACTIONS = builtIn(BANK_TRANSACTIONS_KEY,
            "Bank Transactions", 0xFF5C6BC0);
    public static final Category OTHER = builtIn(OTHER_KEY, "Other", 0xFF9E9E9E);

    private static final List<Category> DEFAULTS = Collections.unmodifiableList(Arrays.asList(
            FOOD_AND_DINING, GROCERIES, TRANSPORT, SHOPPING, ENTERTAINMENT,
            BILLS_AND_UTILITIES, HEALTH, EDUCATION, BANK_TRANSACTIONS, OTHER));

    /** Colours offered when the user creates or recolours a category. */
    public static final int[] PALETTE = {
            0xFFE4572E, 0xFF4CAF50, 0xFF2196F3, 0xFFAB47BC, 0xFFFFB300, 0xFF607D8B,
            0xFFE91E63, 0xFF009688, 0xFF795548, 0xFF3F51B5, 0xFFFF7043, 0xFF8BC34A,
            0xFF00BCD4, 0xFF9C27B0, 0xFFCDDC39, 0xFF5C6BC0, 0xFF9E9E9E};

    private final String key;
    private final String label;
    private final int color;
    private final boolean builtIn;
    private final long budgetCents;   // 0 means no budget
    private final Currency budgetCurrency;

    public Category(String key, String label, int color, boolean builtIn, long budgetCents) {
        this(key, label, color, builtIn, budgetCents, Currency.defaultCurrency());
    }

    public Category(String key, String label, int color, boolean builtIn, long budgetCents,
                    Currency budgetCurrency) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Category key must not be blank");
        }
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (budgetCents < 0) {
            throw new IllegalArgumentException("Budget must not be negative");
        }
        this.key = key;
        this.label = label.trim();
        this.color = color;
        this.builtIn = builtIn;
        this.budgetCents = budgetCents;
        this.budgetCurrency = budgetCurrency == null ? Currency.defaultCurrency() : budgetCurrency;
    }

    private static Category builtIn(String key, String label, int color) {
        return new Category(key, label, color, true, 0);
    }

    /** A new user-defined category with a fresh unique key. */
    public static Category createCustom(String label, int color) {
        String key = CUSTOM_PREFIX + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT)
                + Integer.toString((int) (Math.random() * 1296), 36).toUpperCase(Locale.ROOT);
        return new Category(key, label, color, false, 0);
    }

    /** The built-in categories (the nine from the proposal plus Bank Transactions), Other last. */
    public static List<Category> defaults() {
        return DEFAULTS;
    }

    /** The built-in default with this key, or null if the key is not a built-in one. */
    public static Category defaultFor(String key) {
        for (Category c : DEFAULTS) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** ARGB colour used for dots and bars. */
    public int getColor() {
        return color;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    /** "Other" is the fallback for everything, so it can never be deleted. */
    public boolean isOther() {
        return OTHER_KEY.equals(key);
    }

    public long getBudgetCents() {
        return budgetCents;
    }

    public boolean hasBudget() {
        return budgetCents > 0;
    }

    /** The currency the monthly budget is set in. */
    public Currency getBudgetCurrency() {
        return budgetCurrency;
    }

    public Category withLabel(String newLabel) {
        return new Category(key, newLabel, color, builtIn, budgetCents, budgetCurrency);
    }

    public Category withColor(int newColor) {
        return new Category(key, label, newColor, builtIn, budgetCents, budgetCurrency);
    }

    public Category withBudgetCents(long newBudget) {
        return new Category(key, label, color, builtIn, newBudget, budgetCurrency);
    }

    public Category withBudget(long newBudget, Currency currency) {
        return new Category(key, label, color, builtIn, newBudget, currency);
    }

    /** Two categories are the same category if they have the same key, whatever their name. */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Category && key.equals(((Category) o).key));
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return label;
    }
}
