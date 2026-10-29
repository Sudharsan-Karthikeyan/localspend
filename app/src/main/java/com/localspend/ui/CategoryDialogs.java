package com.localspend.ui;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;

/** Small dialogs shared by the screens that let the user create categories. */
final class CategoryDialogs {

    interface OnCreated {
        void onCreated(Category category);
    }

    private CategoryDialogs() {
    }

    /**
     * Asks for a name and creates a new (unsaved) category with an unused colour.
     * The caller saves it. {@code onCancel} runs if the user backs out.
     */
    static void askNewCategory(Activity activity, CategoryCatalog catalog, OnCreated onCreated,
                               Runnable onCancel) {
        TextInputLayout til = new TextInputLayout(activity);
        til.setHint(activity.getString(R.string.category_name));
        TextInputEditText input = new TextInputEditText(til.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        til.addView(input);
        FrameLayout container = new FrameLayout(activity);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.new_category)
                .setView(container)
                .setPositiveButton(R.string.create, null)
                .setNegativeButton(R.string.cancel, (d, w) -> onCancel.run())
                .setOnCancelListener(d -> onCancel.run())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = String.valueOf(input.getText()).trim();
                    String error = validateName(activity, catalog, name, null);
                    if (error != null) {
                        til.setError(error);
                        return;
                    }
                    dialog.dismiss();
                    onCreated.onCreated(Category.createCustom(name, catalog.suggestColor()));
                }));
        dialog.show();
        input.requestFocus();
        input.post(() -> showKeyboard(activity, input));
    }

    /** Null if the name is fine, otherwise the message to show. */
    static String validateName(Activity activity, CategoryCatalog catalog, String name,
                               Category editing) {
        if (name.isEmpty()) {
            return activity.getString(R.string.error_category_name);
        }
        Category clash = catalog.findByLabel(name);
        if (clash != null && !clash.equals(editing)) {
            return activity.getString(R.string.error_category_exists);
        }
        return null;
    }

    private static void showKeyboard(Activity activity, View view) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
