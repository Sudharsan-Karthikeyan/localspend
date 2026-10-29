package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.logic.BudgetHistory;
import com.localspend.model.Category;

import java.util.Calendar;
import java.util.Map;

/**
 * Stores budgets as a history, so a budget can change from one month to the next and earlier
 * months keep the budget they had. The overall budget in {@link AppSettings} and each category's
 * budget always hold the budget for the current month, so the rest of the app can read them as before.
 * Call from a background thread.
 */
public class BudgetRepository {

    /** How a new budget applies. */
    public enum Scope {
        /** From this month on; earlier months keep their budget. */
        FROM_THIS_MONTH,
        /** This month and every earlier month (replaces what was set for them). */
        ALL_MONTHS,
        /** This one month only (used when editing a past month). */
        THIS_MONTH_ONLY
    }

    private static final String PREFS = "localspend_budget_history";
    private static final String KEY_SEEDED = "seeded";

    private static BudgetRepository instance;

    private final Context context;
    private final DatabaseHelper helper;
    private final CategoryRepository categories;

    public static synchronized BudgetRepository getInstance(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new BudgetRepository(app, DatabaseHelper.getInstance(app),
                    CategoryRepository.getInstance(app));
        }
        return instance;
    }

    private BudgetRepository(Context context, DatabaseHelper helper, CategoryRepository categories) {
        this.context = context;
        this.helper = helper;
        this.categories = categories;
    }

    public static int currentMonthIndex() {
        Calendar now = Calendar.getInstance();
        return BudgetHistory.monthIndex(now.get(Calendar.YEAR), now.get(Calendar.MONTH));
    }

    /** All budgets over time. */
    public synchronized BudgetHistory load() {
        seedFromExistingBudgets();
        BudgetHistory history = new BudgetHistory();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DatabaseHelper.TABLE_BUDGET_HISTORY, null, null, null, null, null, null)) {
            int keyIdx = c.getColumnIndexOrThrow("budget_key");
            int monthIdx = c.getColumnIndexOrThrow("from_month");
            int centsIdx = c.getColumnIndexOrThrow("cents");
            while (c.moveToNext()) {
                history.putEntry(c.getString(keyIdx), c.getInt(monthIdx), c.getLong(centsIdx));
            }
        }
        return history;
    }

    /**
     * Sets a budget. {@code key} is {@link BudgetHistory#OVERALL} or a category key; {@code monthIndex}
     * is the month being edited (see {@link BudgetHistory#monthIndex}).
     */
    public synchronized void apply(String key, int monthIndex, long cents, Scope scope) {
        BudgetHistory history = load();
        switch (scope) {
            case ALL_MONTHS:
                history.setForAllMonths(key, cents);
                break;
            case THIS_MONTH_ONLY:
                history.setOnlyMonth(key, monthIndex, cents);
                break;
            default:
                history.setFrom(key, monthIndex, cents);
        }
        save(key, history);
        syncCurrent(key, history);
    }

    private void save(String key, BudgetHistory history) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_BUDGET_HISTORY, "budget_key = ?", new String[]{key});
            for (Map.Entry<Integer, Long> e : history.entriesFor(key).entrySet()) {
                ContentValues v = new ContentValues();
                v.put("budget_key", key);
                v.put("from_month", e.getKey());
                v.put("cents", e.getValue());
                db.insert(DatabaseHelper.TABLE_BUDGET_HISTORY, null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Keeps the "current budget" copies (settings and category rows) in step with the history. */
    private void syncCurrent(String key, BudgetHistory history) {
        long current = history.centsFor(key, currentMonthIndex());
        if (BudgetHistory.OVERALL.equals(key)) {
            new AppSettings(context).setOverallBudgetCents(current);
            return;
        }
        Category category = categories.getCatalog().find(key);
        if (category != null && category.getBudgetCents() != current) {
            categories.saveCategory(category.withBudgetCents(current));
        }
    }

    /** First run after the upgrade: budgets already set count for every month, as they always did. */
    private void seedFromExistingBudgets() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        long overall = new AppSettings(context).getOverallBudgetCents();
        if (overall > 0) {
            insertSeed(db, BudgetHistory.OVERALL, overall);
        }
        for (Category c : categories.getCatalog().all()) {
            if (c.hasBudget()) {
                insertSeed(db, c.getKey(), c.getBudgetCents());
            }
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply();
    }

    private static void insertSeed(SQLiteDatabase db, String key, long cents) {
        ContentValues v = new ContentValues();
        v.put("budget_key", key);
        v.put("from_month", BudgetHistory.BEGINNING);
        v.put("cents", cents);
        db.insertWithOnConflict(DatabaseHelper.TABLE_BUDGET_HISTORY, null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
    }
}
