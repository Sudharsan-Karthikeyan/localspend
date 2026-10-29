package com.localspend.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.logic.RecurringSchedule;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.RecurringPayment;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.notify.BudgetAlerts;
import com.localspend.notify.Notifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns repeating payments that have fallen due into transactions. Runs when the app opens, once
 * a day in the background and after the phone restarts; missed payments are caught up, so nothing
 * is lost if the phone was off. Call from a background thread.
 */
public final class RecurringProcessor {

    /** At most this many notifications per run, so a long catch-up does not flood the shade. */
    private static final int MAX_NOTIFICATIONS = 3;

    private RecurringProcessor() {
    }

    /**
     * Creates every transaction that is due now.
     *
     * @param notify post a notification for each (when the app is not on screen)
     * @return how many transactions were created
     */
    public static int processDue(Context context, boolean notify) {
        Context app = context.getApplicationContext();
        RecurringRepository plans = RecurringRepository.getInstance(app);
        TransactionRepository transactions = TransactionRepository.getInstance(app);
        CategoryCatalog catalog = CategoryRepository.getInstance(app).getCatalog();
        long now = System.currentTimeMillis();

        List<Transaction> created = new ArrayList<>();
        SQLiteDatabase db = DatabaseHelper.getInstance(app).getWritableDatabase();
        db.beginTransaction();
        try {
            for (RecurringPayment plan : plans.getActive()) {
                RecurringSchedule.Outcome outcome = RecurringSchedule.catchUp(plan, now);
                if (outcome.getDueDates().isEmpty()) {
                    continue;
                }
                for (long due : outcome.getDueDates()) {
                    Transaction t = new Transaction(plan.getAmountCents(), plan.getMerchant(), due,
                            plan.getType(), catalog.resolve(plan.getCategory()), Source.RECURRING);
                    t.setCurrency(plan.getCurrency());
                    t.setCategoryChosenByUser(true);   // the user picked it when setting up the plan
                    transactions.insert(t);
                    created.add(t);
                }
                plan.setNextDueMillis(outcome.getNextDueMillis());
                plan.setPaymentsMade(outcome.getPaymentsMade());
                plan.setActive(outcome.isActive());
                plans.saveProgress(plan);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (!created.isEmpty()) {
            BudgetAlerts.check(app);
            if (notify && created.size() <= MAX_NOTIFICATIONS) {
                for (Transaction t : created) {
                    Notifier.notifyTransaction(app, t);
                }
            }
        }
        return created.size();
    }
}
