package com.localspend.notify;

import android.content.Context;

import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.BudgetStatus;
import com.localspend.logic.BudgetTracker;
import com.localspend.logic.MonthSummary;
import com.localspend.logic.SpendingAnalyzer;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.util.Dates;

import java.util.Calendar;
import java.util.List;

/**
 * Checks this month's spending against the user's budgets and posts a notification the first time
 * a budget reaches the warning point and again the first time it is exceeded. Each alert is shown
 * at most once per budget per month. Call from a background thread.
 */
public final class BudgetAlerts {

    private BudgetAlerts() {
    }

    public static void check(Context context) {
        AppSettings settings = new AppSettings(context);
        if (!settings.isBudgetNotificationsOn()) {
            return;
        }
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);

        TransactionRepository transactions = TransactionRepository.getInstance(context);
        transactions.ensureConverted(transactions.resolveMainCurrency(settings));
        List<Transaction> inMonth = transactions.getBetween(
                Dates.startOfMonth(year, month), Dates.startOfMonth(year, month + 1));
        CategoryCatalog catalog = CategoryRepository.getInstance(context).getCatalog();
        String monthId = year + "-" + (month + 1);

        // Budgets follow the dashboard currency, so only spending in that currency is checked.
        for (Currency currency : new Currency[]{transactions.resolveMainCurrency(settings)}) {
            List<Transaction> spent = SpendingAnalyzer.inCurrency(inMonth, currency);
            if (spent.isEmpty()) {
                continue;
            }
            MonthSummary summary = new SpendingAnalyzer().summarise(spent, year, month);
            List<BudgetStatus> statuses = new BudgetTracker().statuses(summary, catalog,
                    settings.getOverallBudgetCents(), settings.getOverallBudgetCurrency(), currency);
            for (BudgetStatus status : statuses) {
                BudgetStatus.Level level = status.getLevel(settings.getWarnPercent());
                if (level == BudgetStatus.Level.OK) {
                    continue;
                }
                String key = monthId + "|" + currency.name() + "|"
                        + (status.isOverall() ? "overall" : status.getCategory().getKey());
                // Remember the warning too when going straight to "exceeded", so it is not shown later.
                boolean newWarning = settings.markAlertIfNew(key + "|WARNING");
                if (level == BudgetStatus.Level.EXCEEDED) {
                    if (settings.markAlertIfNew(key + "|EXCEEDED")) {
                        Notifier.notifyBudget(context, status, level);
                    }
                } else if (newWarning) {
                    Notifier.notifyBudget(context, status, level);
                }
            }
        }
    }
}
