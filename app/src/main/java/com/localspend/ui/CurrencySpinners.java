package com.localspend.ui;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;
import com.localspend.model.Currency;

/** Currency pickers for amount fields: the field's prefix follows the picked currency. */
final class CurrencySpinners {

    private CurrencySpinners() {
    }

    /** Fills the spinner with every currency and keeps {@code field}'s prefix in step. */
    static void setUp(Spinner spinner, TextInputLayout field, Currency initial) {
        Currency[] all = Currency.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = all[i].getPickerLabel();
        }
        spinner.setAdapter(new ArrayAdapter<>(spinner.getContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(initial.ordinal());
        if (field != null) {
            field.setPrefixText(initial.getSymbol());
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (field != null) {
                    field.setPrefixText(all[position].getSymbol());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    static Currency selected(Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        Currency[] all = Currency.values();
        return position >= 0 && position < all.length ? all[position] : Currency.defaultCurrency();
    }
}
