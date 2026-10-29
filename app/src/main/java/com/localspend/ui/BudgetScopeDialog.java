package com.localspend.ui;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.localspend.R;
import com.localspend.data.BudgetRepository.Scope;

/**
 * Asks how a new budget applies. Removing a budget never reaches earlier months (to remove one
 * of those, open that month's budgets), so only setting or raising one asks.
 */
final class BudgetScopeDialog {

    interface OnChoice {
        void chosen(Scope scope);
    }

    private BudgetScopeDialog() {
    }

    /**
     * @param newCents the budget being saved; 0 means it is being removed
     * @param monthLabel the month being edited, for example "October 2026"
     */
    static void choose(Context context, long newCents, String monthLabel, OnChoice onChoice) {
        if (newCents <= 0) {
            onChoice.chosen(Scope.FROM_THIS_MONTH);   // removal starts this month; earlier months stay
            return;
        }
        String[] options = {
                context.getString(R.string.budget_scope_from_this, monthLabel),
                context.getString(R.string.budget_scope_all)};
        new AlertDialog.Builder(context)
                .setTitle(R.string.budget_scope_title)
                .setItems(options, (d, which) ->
                        onChoice.chosen(which == 0 ? Scope.FROM_THIS_MONTH : Scope.ALL_MONTHS))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
