package com.localspend.model;

/** Whether money left (expense) or arrived (income). */
public enum TransactionType {
    EXPENSE("Expense"),
    INCOME("Income");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Safe lookup used when reading from the database; unknown values fall back to EXPENSE. */
    public static TransactionType fromName(String name) {
        for (TransactionType t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return EXPENSE;
    }
}
