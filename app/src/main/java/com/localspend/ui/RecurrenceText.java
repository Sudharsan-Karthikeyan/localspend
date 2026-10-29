package com.localspend.ui;

import android.content.Context;

import com.localspend.R;
import com.localspend.model.Frequency;

/** Short descriptions of repeating payments, shared by the add screen and the list. */
final class RecurrenceText {

    private RecurrenceText() {
    }

    /** "Repeats · Monthly", "Instalments · Monthly · 12 payments". */
    static String describe(Context context, Frequency frequency, boolean installment, int total) {
        if (installment) {
            return context.getString(R.string.recurrence_summary_installment,
                    frequency.getLabel(), total);
        }
        return total > 0
                ? context.getString(R.string.recurrence_summary_repeat_limited,
                frequency.getLabel(), total)
                : context.getString(R.string.recurrence_summary_repeat, frequency.getLabel());
    }
}
