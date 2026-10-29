package com.localspend.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.TransactionRepository;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.ui.MainActivity;
import com.localspend.ui.QuickAddActivity;
import com.localspend.util.Background;
import com.localspend.util.MoneyFormatter;

import java.util.Calendar;
import java.util.List;

/**
 * Home-screen widget: shows what has been spent today and has an "Add expense" button that opens a
 * small pop-up to record a purchase without opening the app. It refreshes whenever a transaction
 * is added, changed or deleted, and every half hour.
 */
public class QuickAddWidget extends AppWidgetProvider {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DEBOUNCE_MILLIS = 400;
    private static Runnable pendingRefresh;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        Background.run(() -> {
            try {
                update(app, manager, ids);
            } finally {
                result.finish();
            }
        });
    }

    /**
     * Asks for the widget to be redrawn soon. Several changes in a row (importing many messages)
     * are combined into one redraw. Safe to call from any thread.
     */
    public static void requestRefresh(Context context) {
        final Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (pendingRefresh != null) {
                MAIN.removeCallbacks(pendingRefresh);
            }
            pendingRefresh = () -> Background.run(() -> refreshAll(app));
            MAIN.postDelayed(pendingRefresh, DEBOUNCE_MILLIS);
        });
    }

    private static void refreshAll(Context app) {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, QuickAddWidget.class));
        if (ids != null && ids.length > 0) {
            update(app, manager, ids);
        }
    }

    /** Reads today's spending and redraws the widgets. Call from a background thread. */
    private static void update(Context app, AppWidgetManager manager, int[] ids) {
        TransactionRepository repository = TransactionRepository.getInstance(app);
        AppSettings settings = new AppSettings(app);
        Currency currency = repository.resolveMainCurrency(settings);
        repository.ensureConverted(currency);

        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long start = day.getTimeInMillis();
        day.add(Calendar.DAY_OF_MONTH, 1);
        List<Transaction> today = repository.getBetween(start, day.getTimeInMillis());
        long spent = 0;
        int count = 0;
        for (Transaction t : today) {
            if (t.isExpense() && t.canBeShownIn(currency)) {
                spent += t.getAmountIn(currency);
                count++;
            }
        }

        RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_quick_add);
        views.setTextViewText(R.id.widgetAmount, MoneyFormatter.format(spent, currency));
        views.setTextViewText(R.id.widgetCount, count == 0
                ? app.getString(R.string.widget_none_today)
                : app.getResources().getQuantityString(R.plurals.widget_expenses_today, count, count));

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent add = new Intent(app, QuickAddActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widgetAdd,
                PendingIntent.getActivity(app, 1, add, flags));
        Intent open = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widgetBody,
                PendingIntent.getActivity(app, 2, open, flags));

        manager.updateAppWidget(ids, views);
    }
}
