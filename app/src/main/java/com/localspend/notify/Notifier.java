package com.localspend.notify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.logic.BudgetStatus;
import com.localspend.model.PendingSms;
import com.localspend.model.Transaction;
import com.localspend.ui.AddTransactionActivity;
import com.localspend.ui.MainActivity;
import com.localspend.ui.ReviewActivity;
import com.localspend.util.MoneyFormatter;

/**
 * Local notifications. They are created on the device; nothing is sent to a server.
 * Two channels let the user silence one kind in Android settings without losing the other.
 */
public final class Notifier {

    public static final String CHANNEL_TRANSACTIONS = "transactions";
    public static final String CHANNEL_BUDGETS = "budgets";

    private static final int BUDGET_ID_BASE = 1_000_000;
    private static final int REVIEW_ID = 2_000_000;

    private Notifier() {
    }

    /** Registers the notification channels (safe to call repeatedly). */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel tx = new NotificationChannel(CHANNEL_TRANSACTIONS,
                context.getString(R.string.channel_transactions), NotificationManager.IMPORTANCE_DEFAULT);
        tx.setDescription(context.getString(R.string.channel_transactions_desc));
        NotificationChannel budget = new NotificationChannel(CHANNEL_BUDGETS,
                context.getString(R.string.channel_budgets), NotificationManager.IMPORTANCE_HIGH);
        budget.setDescription(context.getString(R.string.channel_budgets_desc));
        nm.createNotificationChannel(tx);
        nm.createNotificationChannel(budget);
    }

    /** "Spent $25.50 at Woolworths · Groceries". Tapping opens the transaction to correct it. */
    public static void notifyTransaction(Context context, Transaction t) {
        if (!new AppSettings(context).isTransactionNotificationsOn()) {
            return;
        }
        String amount = MoneyFormatter.format(t.getAmountCents(), t.getCurrency());
        String title = t.isExpense()
                ? context.getString(R.string.notify_spent, amount, t.getMerchant())
                : context.getString(R.string.notify_received, amount, t.getMerchant());
        String text = t.isExpense()
                ? context.getString(R.string.notify_category, t.getCategory().getLabel())
                : context.getString(R.string.notify_income_text);

        Intent open = new Intent(context, AddTransactionActivity.class)
                .putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
        PendingIntent pending = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent((int) t.getId(),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        post(context, (int) t.getId(), new NotificationCompat.Builder(context, CHANNEL_TRANSACTIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS));
    }

    /**
     * A transaction-looking message arrived from a sender that is not a recognised bank.
     * Tapping opens the review list, where the user can add or ignore it.
     */
    public static void notifyReview(Context context, PendingSms p) {
        if (!new AppSettings(context).isTransactionNotificationsOn()) {
            return;
        }
        String sender = p.getSender() == null || p.getSender().isEmpty()
                ? context.getString(R.string.unknown_sender) : p.getSender();
        String amount = MoneyFormatter.format(p.getAmountCents(), p.getCurrency());
        String text = context.getString(p.getType() == com.localspend.model.TransactionType.EXPENSE
                ? R.string.notify_review_text_expense : R.string.notify_review_text_income,
                amount, p.getMerchant(), sender);

        Intent open = new Intent(context, ReviewActivity.class);
        PendingIntent pending = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent(REVIEW_ID,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        post(context, REVIEW_ID, new NotificationCompat.Builder(context, CHANNEL_TRANSACTIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notify_review_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true));
    }

    /** Budget warning ("80% of your Food budget used") or "over budget" alert. */
    public static void notifyBudget(Context context, BudgetStatus status, BudgetStatus.Level level) {
        String name = status.isOverall()
                ? context.getString(R.string.overall_budget)
                : status.getCategory().getLabel();
        String title = level == BudgetStatus.Level.EXCEEDED
                ? context.getString(R.string.notify_budget_over_title, name)
                : context.getString(R.string.notify_budget_warn_title, status.getPercentUsed(), name);
        String text = context.getString(R.string.notify_budget_text,
                MoneyFormatter.format(status.getSpentCents(), status.getCurrency()),
                MoneyFormatter.format(status.getBudgetCents(), status.getCurrency()));

        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String key = status.isOverall() ? "overall" : status.getCategory().getKey();
        int id = BUDGET_ID_BASE + (key.hashCode() & 0xFFFF);
        PendingIntent pending = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        post(context, id, new NotificationCompat.Builder(context, CHANNEL_BUDGETS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH));
    }

    @SuppressLint("MissingPermission")   // checked just below
    private static void post(Context context, int id, NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;   // Android 13+: the user has not allowed notifications
        }
        createChannels(context);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (!nm.areNotificationsEnabled()) {
            return;   // switched off in Android settings
        }
        try {
            nm.notify(id, builder.build());
        } catch (SecurityException e) {
            // Permission was revoked between the check and the call; nothing to show.
        }
    }
}
