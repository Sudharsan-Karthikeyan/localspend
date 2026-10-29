package com.localspend.logic;

import com.localspend.model.Transaction;

import java.util.Calendar;
import java.util.List;

/** Income and spending for each day of one month, for the calendar. Days are numbered from 1. */
public final class DayTotals {

    private final long[] income = new long[32];
    private final long[] expense = new long[32];
    private final int year;
    private final int month;

    private DayTotals(int year, int month) {
        this.year = year;
        this.month = month;
    }

    /** Adds up the transactions that fall in the given month (month is 0-based). */
    public static DayTotals of(List<Transaction> transactions, int year, int month) {
        DayTotals totals = new DayTotals(year, month);
        Calendar c = Calendar.getInstance();
        for (Transaction t : transactions) {
            c.setTimeInMillis(t.getDateMillis());
            if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) != month) {
                continue;
            }
            int day = c.get(Calendar.DAY_OF_MONTH);
            if (t.isExpense()) {
                totals.expense[day] += t.getAmountCents();
            } else {
                totals.income[day] += t.getAmountCents();
            }
        }
        return totals;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public long incomeOn(int day) {
        return day >= 1 && day <= 31 ? income[day] : 0;
    }

    public long expenseOn(int day) {
        return day >= 1 && day <= 31 ? expense[day] : 0;
    }

    /** Income minus spending for the day (negative when more was spent than received). */
    public long netOn(int day) {
        return incomeOn(day) - expenseOn(day);
    }

    public long totalIncome() {
        long sum = 0;
        for (int d = 1; d <= 31; d++) {
            sum += income[d];
        }
        return sum;
    }

    public long totalExpense() {
        long sum = 0;
        for (int d = 1; d <= 31; d++) {
            sum += expense[d];
        }
        return sum;
    }

    public long totalNet() {
        return totalIncome() - totalExpense();
    }
}
