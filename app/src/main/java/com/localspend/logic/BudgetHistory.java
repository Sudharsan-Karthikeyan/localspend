package com.localspend.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Budgets over time. Each budget (the overall one, or one per category) is a list of "from this
 * month on, the budget is X" entries, so a month's budget is the latest entry at or before it.
 * Changing a budget from this month leaves earlier months as they were; applying it to all months
 * replaces whatever was set for earlier months.
 */
public final class BudgetHistory {

    /** Key of the overall monthly budget; categories use their own key. */
    public static final String OVERALL = "overall";

    /** An entry at this index covers every month before any other entry. */
    public static final int BEGINNING = 0;

    private final Map<String, TreeMap<Integer, Long>> entries = new HashMap<>();

    /** Months counted from year 0, so entries sort in time order. {@code month} is 0-based. */
    public static int monthIndex(int year, int month) {
        return year * 12 + month;
    }

    /** The budget for the month, in cents; 0 means none. */
    public long centsFor(String key, int monthIndex) {
        TreeMap<Integer, Long> list = entries.get(key);
        if (list == null) {
            return 0;
        }
        Map.Entry<Integer, Long> e = list.floorEntry(monthIndex);
        return e == null ? 0 : e.getValue();
    }

    /** From this month onwards the budget is {@code cents} (0 removes it); earlier months keep theirs. */
    public void setFrom(String key, int monthIndex, long cents) {
        list(key).put(monthIndex, Math.max(0, cents));
    }

    /** Sets the budget for every month so far, replacing anything set for earlier months. */
    public void setForAllMonths(String key, long cents) {
        TreeMap<Integer, Long> list = list(key);
        list.clear();
        list.put(BEGINNING, Math.max(0, cents));
    }

    /** Changes one month only; the months after it keep the budget they had. */
    public void setOnlyMonth(String key, int monthIndex, long cents) {
        TreeMap<Integer, Long> list = list(key);
        long after = centsFor(key, monthIndex + 1);
        boolean nextHasOwnEntry = list.containsKey(monthIndex + 1);
        list.put(monthIndex, Math.max(0, cents));
        if (!nextHasOwnEntry) {
            list.put(monthIndex + 1, after);
        }
    }

    /** The entries of one budget, earliest first (for saving). */
    public Map<Integer, Long> entriesFor(String key) {
        TreeMap<Integer, Long> list = entries.get(key);
        return list == null ? new TreeMap<Integer, Long>() : new TreeMap<>(list);
    }

    public void putEntry(String key, int monthIndex, long cents) {
        list(key).put(monthIndex, Math.max(0, cents));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private TreeMap<Integer, Long> list(String key) {
        TreeMap<Integer, Long> list = entries.get(key);
        if (list == null) {
            list = new TreeMap<>();
            entries.put(key, list);
        }
        return list;
    }
}
