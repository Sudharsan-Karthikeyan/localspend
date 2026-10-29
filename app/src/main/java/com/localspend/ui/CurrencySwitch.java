package com.localspend.ui;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.TransactionRepository;
import com.localspend.model.Currency;

import java.util.List;

/**
 * Chooses which currency the dashboard and history show. Totals are never mixed: rupee and dollar
 * spending are summed separately, and the screen shows one currency at a time.
 *
 * In automatic mode (the default) the screens follow the currency of the most recent transaction,
 * so the app switches between dollars and rupees by itself as the user's spending moves. The user
 * can also pin one currency.
 */
final class CurrencySwitch {

    private CurrencySwitch() {
    }

    /** The currency to show now. Call from a background thread. */
    static Currency resolve(AppSettings settings, TransactionRepository repository) {
        Currency main = repository.resolveMainCurrency(settings);
        repository.ensureConverted(main);   // everything else is converted into it
        // Budgets set in another currency are converted, not reused as the same number.
        repository.rebaseBudgets(main);
        return main;
    }

    /** Lets the user pick automatic or a fixed currency, then runs {@code onChanged}. */
    static void show(Activity activity, AppSettings settings, List<Currency> inUse,
                     Currency showing, Runnable onChanged) {
        Currency[] all = Currency.values();
        String[] labels = new String[all.length + 1];
        labels[0] = activity.getString(R.string.currency_auto);
        int checked = settings.getDisplayCurrency() == null ? 0 : -1;
        for (int i = 0; i < all.length; i++) {
            String label = all[i].getPickerLabel() + " · " + all[i].getDisplayName();
            labels[i + 1] = inUse.contains(all[i]) ? label
                    : activity.getString(R.string.currency_no_data, label);
            if (all[i] == settings.getDisplayCurrency()) {
                checked = i + 1;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.currency_title, showing.getPickerLabel()))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    settings.setDisplayCurrency(which == 0 ? null : all[which - 1]);
                    d.dismiss();
                    onChanged.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
